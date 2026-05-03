package edu.cqupt.devbrain.knowledge.service.impl;

import edu.cqupt.devbrain.framework.context.UserContext;
import edu.cqupt.devbrain.framework.exception.ClientException;
import edu.cqupt.devbrain.knowledge.controller.vo.DocumentVO;
import edu.cqupt.devbrain.knowledge.dao.entity.KnowledgeBaseDO;
import edu.cqupt.devbrain.knowledge.dao.entity.KnowledgeDocumentDO;
import edu.cqupt.devbrain.knowledge.dao.mapper.KnowledgeBaseMapper;
import edu.cqupt.devbrain.knowledge.dao.mapper.KnowledgeDocumentMapper;
import edu.cqupt.devbrain.knowledge.service.KnowledgeDocumentService;
import edu.cqupt.devbrain.knowledge.service.validator.FileUploadValidator;
import edu.cqupt.devbrain.knowledge.storage.FileStorageService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.UUID;

/**
 * 知识库文档服务实现 —— 负责文档上传的完整业务流程。
 * <p>
 * 流程：校验 → 上传对象存储（事务外） → 插入数据库（编程式事务） → 返回 VO。
 * 若数据库插入失败，回滚已上传的对象存储文件，避免孤立文件。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class KnowledgeDocumentServiceImpl implements KnowledgeDocumentService {

    private static final String DEFAULT_STATUS = "pending";
    private static final String DEFAULT_PROCESS_MODE = "chunk";
    private static final String SOURCE_TYPE = "file";

    private final KnowledgeBaseMapper knowledgeBaseMapper;
    private final KnowledgeDocumentMapper knowledgeDocumentMapper;
    private final FileStorageService fileStorageService;
    private final FileUploadValidator fileUploadValidator;
    private final TransactionTemplate transactionTemplate;

    @Override
    public DocumentVO upload(String kbId, MultipartFile file, String processMode,
                             String chunkStrategy, String chunkConfig, String pipelineId) {
        // 1. 校验知识库存在且未删除
        KnowledgeBaseDO kb = knowledgeBaseMapper.selectById(kbId);
        if (kb == null) {
            throw new ClientException("知识库不存在：" + kbId);
        }

        // 2. 文件基础校验（非空、大小）
        fileUploadValidator.validate(file);

        // 3. 文件名清洗和扩展名提取
        String originalFilename = file.getOriginalFilename();
        String sanitizedName = fileUploadValidator.sanitizeFilename(originalFilename);
        String extension = fileUploadValidator.extractExtension(sanitizedName);

        // 4. 文件类型白名单校验
        fileUploadValidator.validateFileType(file, extension);

        // 5. 上传到对象存储（事务外，避免长时间占用数据库连接）
        String objectKey = UUID.randomUUID().toString().replace("-", "") + "." + extension;
        String fileUrl;
        try {
            fileUrl = fileStorageService.upload(objectKey, file.getInputStream(), file.getContentType(), file.getSize());
        } catch (IOException e) {
            log.error("读取上传文件流失败，kbId={}, fileName={}", kbId, sanitizedName, e);
            throw new ClientException("文件读取失败");
        }

        // 6. 插入数据库（编程式事务），失败时清理已上传文件
        DocumentVO result;
        try {
            result = transactionTemplate.execute(status -> {
                String userId = UserContext.requireUser().userId();

                KnowledgeDocumentDO document = new KnowledgeDocumentDO();
                document.setKbId(kbId);
                document.setDocName(sanitizedName);
                document.setEnabled(1);
                document.setChunkCount(0L);
                document.setFileUrl(fileUrl);
                document.setFileType(extension);
                document.setFileSize(file.getSize());
                document.setProcessMode(processMode != null ? processMode : DEFAULT_PROCESS_MODE);
                document.setStatus(DEFAULT_STATUS);
                document.setSourceType(SOURCE_TYPE);
                document.setSourceLocation(sanitizedName);
                document.setScheduleEnabled(0);
                document.setChunkStrategy(chunkStrategy);
                document.setChunkConfig(chunkConfig);
                document.setPipelineId(pipelineId);
                document.setCreatedBy(userId);
                document.setUpdatedBy(userId);

                knowledgeDocumentMapper.insert(document);

                log.info("文档上传完成，kbId={}, docId={}, fileName={}, fileSize={}, fileType={}",
                        kbId, document.getId(), sanitizedName, file.getSize(), extension);

                return toVO(document);
            });
        } catch (Exception e) {
            log.error("数据库插入失败，需回滚已上传文件，kbId={}, docName={}, fileUrl={}, objectKey={}",
                    kbId, sanitizedName, fileUrl, objectKey, e);
            compensateUploadedFile(objectKey, kbId, sanitizedName, fileUrl);
            throw e;
        }

        return result;
    }

    /**
     * 补偿删除已上传的对象存储文件。
     * 删除失败只记录日志，不覆盖原始异常。
     */
    private void compensateUploadedFile(String objectKey, String kbId, String docName, String fileUrl) {
        try {
            fileStorageService.delete(objectKey);
            log.info("补偿删除文件成功，objectKey={}", objectKey);
        } catch (Exception ex) {
            log.error("补偿删除文件失败，存在孤立文件，kbId={}, docName={}, fileUrl={}, objectKey={}",
                    kbId, docName, fileUrl, objectKey, ex);
        }
    }

    private DocumentVO toVO(KnowledgeDocumentDO doc) {
        return new DocumentVO(
                doc.getId(), doc.getKbId(), doc.getDocName(),
                doc.getEnabled(), doc.getChunkCount(), doc.getFileUrl(),
                doc.getFileType(), doc.getFileSize(), doc.getProcessMode(),
                doc.getStatus(), doc.getSourceType(), doc.getSourceLocation(),
                doc.getChunkStrategy(), doc.getChunkConfig(), doc.getPipelineId(),
                doc.getCreateTime(), doc.getUpdateTime()
        );
    }
}
