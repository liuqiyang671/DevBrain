package edu.cqupt.devbrain.knowledge.service.impl;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import edu.cqupt.devbrain.framework.context.UserContext;
import edu.cqupt.devbrain.framework.errorcode.BaseErrorCode;
import edu.cqupt.devbrain.framework.exception.ClientException;
import edu.cqupt.devbrain.framework.exception.ServiceException;
import edu.cqupt.devbrain.knowledge.controller.request.OnlineDocumentImportRequest;
import edu.cqupt.devbrain.knowledge.controller.vo.DocumentVO;
import edu.cqupt.devbrain.knowledge.dao.entity.KnowledgeBaseDO;
import edu.cqupt.devbrain.knowledge.dao.entity.KnowledgeDocumentDO;
import edu.cqupt.devbrain.knowledge.dao.mapper.KnowledgeBaseMapper;
import edu.cqupt.devbrain.knowledge.dao.mapper.KnowledgeDocumentMapper;
import edu.cqupt.devbrain.knowledge.service.KnowledgeDocumentService;
import edu.cqupt.devbrain.knowledge.service.validator.FileUploadValidator;
import edu.cqupt.devbrain.knowledge.storage.FileStorageService;
import edu.cqupt.devbrain.sync.adapter.DocumentSourceAdapter;
import edu.cqupt.devbrain.sync.adapter.DocumentSourceAdapterRegistry;
import edu.cqupt.devbrain.sync.adapter.FetchedContent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.Date;
import java.util.HexFormat;

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
    private static final String ONLINE_FILE_TYPE = "txt";
    private static final Set<String> ONLINE_SOURCE_TYPES = Set.of("feishu", "url");
    private static final Set<String> DOCUMENT_STATUSES = Set.of("pending", "processing", "completed", "failed");

    private final KnowledgeBaseMapper knowledgeBaseMapper;
    private final KnowledgeDocumentMapper knowledgeDocumentMapper;
    private final FileStorageService fileStorageService;
    private final FileUploadValidator fileUploadValidator;
    private final TransactionTemplate transactionTemplate;
    private final DocumentSourceAdapterRegistry adapterRegistry;

    @Override
    public List<DocumentVO> listByKnowledgeBase(String kbId) {
        KnowledgeBaseDO kb = requireKnowledgeBase(kbId);
        return knowledgeDocumentMapper.selectList(Wrappers.lambdaQuery(KnowledgeDocumentDO.class)
                        .eq(KnowledgeDocumentDO::getKbId, kb.getId())
                        .eq(KnowledgeDocumentDO::getDeleted, 0)
                        .orderByDesc(KnowledgeDocumentDO::getUpdateTime))
                .stream()
                .map(this::toVO)
                .toList();
    }

    @Override
    public IPage<DocumentVO> page(long pageNo, long pageSize, String kbId, String keyword, String status, Integer enabled) {
        long current = Math.max(1, pageNo);
        long size = Math.min(Math.max(1, pageSize), 100);
        String cleanedKbId = clean(kbId);
        String cleanedKeyword = clean(keyword);
        String cleanedStatus = normalizeStatus(clean(status));
        if (StringUtils.hasText(cleanedKbId)) {
            requireKnowledgeBase(cleanedKbId);
        }
        if (StringUtils.hasText(cleanedStatus) && !DOCUMENT_STATUSES.contains(cleanedStatus)) {
            throw new ClientException("文档状态只能为 pending、processing、completed 或 failed");
        }
        if (enabled != null) {
            ensureEnabledValid(enabled);
        }

        IPage<KnowledgeDocumentDO> result = knowledgeDocumentMapper.selectPage(new Page<>(current, size),
                Wrappers.lambdaQuery(KnowledgeDocumentDO.class)
                        .eq(KnowledgeDocumentDO::getDeleted, 0)
                        .eq(StringUtils.hasText(cleanedKbId), KnowledgeDocumentDO::getKbId, cleanedKbId)
                        .eq(StringUtils.hasText(cleanedStatus), KnowledgeDocumentDO::getStatus, cleanedStatus)
                        .eq(enabled != null, KnowledgeDocumentDO::getEnabled, enabled)
                        .like(StringUtils.hasText(cleanedKeyword), KnowledgeDocumentDO::getDocName, cleanedKeyword)
                        .orderByDesc(KnowledgeDocumentDO::getUpdateTime));
        return result.convert(this::toVO);
    }

    @Override
    public DocumentVO upload(String kbId, MultipartFile file, String processMode,
                             String chunkStrategy, String chunkConfig, String pipelineId) {
        // 1. 校验知识库存在且未删除
        requireKnowledgeBase(kbId);

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

    @Override
    public DocumentVO importOnline(String kbId, OnlineDocumentImportRequest request) {
        requireKnowledgeBase(kbId);
        String sourceType = clean(request.sourceType());
        String sourceLocation = clean(request.sourceLocation());
        if (!ONLINE_SOURCE_TYPES.contains(sourceType)) {
            throw new ClientException("在线文档来源类型只能为 feishu 或 url");
        }
        if (!StringUtils.hasText(sourceLocation)) {
            throw new ClientException("在线文档来源地址不能为空");
        }
        ensureEnabledValid(request.scheduleEnabled() == null ? 0 : request.scheduleEnabled());
        validateSchedule(request.scheduleEnabled(), request.scheduleCron());

        FetchedContent fetched = fetchOnlineContent(sourceType, sourceLocation);
        String text = fetched.text();
        if (!StringUtils.hasText(text)) {
            throw new ClientException("在线文档内容为空，无法导入");
        }

        byte[] bytes = text.getBytes(StandardCharsets.UTF_8);
        String docName = resolveOnlineDocName(request.docName(), fetched.title(), sourceType);
        String objectKey = "online/" + UUID.randomUUID().toString().replace("-", "") + "." + ONLINE_FILE_TYPE;
        String fileUrl = fileStorageService.upload(objectKey,
                new ByteArrayInputStream(bytes), "text/plain; charset=utf-8", bytes.length);
        String contentHash = sha256(text);

        try {
            return transactionTemplate.execute(status -> {
                String userId = UserContext.requireUser().userId();
                KnowledgeDocumentDO document = new KnowledgeDocumentDO();
                document.setKbId(kbId);
                document.setDocName(docName);
                document.setEnabled(1);
                document.setChunkCount(0L);
                document.setFileUrl(fileUrl);
                document.setFileType(ONLINE_FILE_TYPE);
                document.setFileSize((long) bytes.length);
                document.setProcessMode(StringUtils.hasText(request.processMode()) ? request.processMode().trim() : DEFAULT_PROCESS_MODE);
                document.setStatus(DEFAULT_STATUS);
                document.setSourceType(sourceType);
                document.setSourceLocation(sourceLocation);
                document.setScheduleEnabled(request.scheduleEnabled() == null ? 0 : request.scheduleEnabled());
                document.setScheduleCron(clean(request.scheduleCron()));
                document.setChunkStrategy(clean(request.chunkStrategy()));
                document.setChunkConfig(clean(request.chunkConfig()));
                document.setPipelineId(clean(request.pipelineId()));
                document.setLastContentHash(contentHash);
                document.setLastSyncTime(new Date());
                document.setCreatedBy(userId);
                document.setUpdatedBy(userId);

                knowledgeDocumentMapper.insert(document);

                log.info("在线文档导入完成，kbId={}, docId={}, sourceType={}, docName={}, fileSize={}",
                        kbId, document.getId(), sourceType, docName, bytes.length);

                return toVO(document);
            });
        } catch (Exception e) {
            log.error("在线文档入库失败，需回滚已上传文件，kbId={}, docName={}, fileUrl={}, objectKey={}",
                    kbId, docName, fileUrl, objectKey, e);
            compensateUploadedFile(objectKey, kbId, docName, fileUrl);
            throw e;
        }
    }

    @Override
    public DocumentVO updateEnabled(String kbId, String docId, Integer enabled) {
        ensureEnabledValid(enabled);
        KnowledgeDocumentDO document = requireDocument(kbId, docId);
        document.setEnabled(enabled);
        document.setUpdatedBy(UserContext.requireUser().userId());
        knowledgeDocumentMapper.updateById(document);
        log.info("文档启停状态已更新，kbId={}, docId={}, enabled={}", kbId, docId, enabled);
        return toVO(document);
    }

    @Override
    public void delete(String kbId, String docId) {
        KnowledgeDocumentDO document = requireDocument(kbId, docId);
        document.setUpdatedBy(UserContext.requireUser().userId());
        cleanupStoredFile(document);
        knowledgeDocumentMapper.deleteById(document);
        log.info("文档已逻辑删除，kbId={}, docId={}", kbId, docId);
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

    private KnowledgeBaseDO requireKnowledgeBase(String kbId) {
        if (!StringUtils.hasText(kbId)) {
            throw new ClientException("知识库 ID 不能为空");
        }
        KnowledgeBaseDO kb = knowledgeBaseMapper.selectById(kbId);
        if (kb == null || Integer.valueOf(1).equals(kb.getDeleted())) {
            throw new ClientException("知识库不存在或已删除：" + kbId);
        }
        return kb;
    }

    private KnowledgeDocumentDO requireDocument(String kbId, String docId) {
        requireKnowledgeBase(kbId);
        if (!StringUtils.hasText(docId)) {
            throw new ClientException("文档 ID 不能为空");
        }
        KnowledgeDocumentDO document = knowledgeDocumentMapper.selectById(docId);
        if (document == null || Integer.valueOf(1).equals(document.getDeleted()) || !kbId.equals(document.getKbId())) {
            throw new ClientException("文档不存在或已删除");
        }
        return document;
    }

    private void ensureEnabledValid(Integer enabled) {
        if (enabled == null || (enabled != 0 && enabled != 1)) {
            throw new ClientException("enabled 只能为 0 或 1");
        }
    }

    private FetchedContent fetchOnlineContent(String sourceType, String sourceLocation) {
        try {
            DocumentSourceAdapter adapter = adapterRegistry.requireAdapter(sourceType);
            return adapter.fetchContent(sourceLocation);
        } catch (ClientException | ServiceException e) {
            throw e;
        } catch (Exception e) {
            log.error("在线文档抓取失败，sourceType={}, sourceLocation={}", sourceType, sourceLocation, e);
            throw new ClientException("在线文档抓取失败: " + e.getMessage());
        }
    }

    private void validateSchedule(Integer scheduleEnabled, String scheduleCron) {
        if (scheduleEnabled != null && scheduleEnabled == 1 && StringUtils.hasText(scheduleCron)) {
            try {
                org.springframework.scheduling.support.CronExpression.parse(scheduleCron);
            } catch (IllegalArgumentException e) {
                throw new ClientException("Cron 表达式格式错误: " + e.getMessage());
            }
        }
    }

    private String resolveOnlineDocName(String requestedName, String fetchedTitle, String sourceType) {
        String name = clean(requestedName);
        if (!StringUtils.hasText(name)) {
            name = clean(fetchedTitle);
        }
        if (!StringUtils.hasText(name)) {
            name = "feishu".equals(sourceType) ? "飞书文档" : "网页文档";
        }
        name = fileUploadValidator.sanitizeFilename(name);
        return fileUploadValidator.extractExtension(name).isEmpty() ? name + "." + ONLINE_FILE_TYPE : name;
    }

    private String normalizeStatus(String status) {
        if ("running".equals(status)) {
            return "processing";
        }
        if ("success".equals(status)) {
            return "completed";
        }
        return status;
    }

    private String clean(String value) {
        return value == null ? null : value.trim();
    }

    private String sha256(String text) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(text.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new ServiceException("SHA-256 算法不可用", e, BaseErrorCode.SERVICE_ERROR);
        }
    }

    private void cleanupStoredFile(KnowledgeDocumentDO document) {
        String objectKey = extractObjectKey(document.getFileUrl());
        if (!StringUtils.hasText(objectKey)) {
            return;
        }
        try {
            fileStorageService.delete(objectKey);
        } catch (Exception ex) {
            log.error("删除文档对象存储文件失败，将继续逻辑删除文档，docId={}, fileUrl={}, objectKey={}",
                    document.getId(), document.getFileUrl(), objectKey, ex);
        }
    }

    private String extractObjectKey(String fileUrl) {
        if (!StringUtils.hasText(fileUrl)) {
            return null;
        }
        try {
            String path = URI.create(fileUrl).getPath();
            if (!StringUtils.hasText(path)) {
                return null;
            }
            int lastSlash = path.lastIndexOf('/');
            return lastSlash >= 0 ? path.substring(lastSlash + 1) : path;
        } catch (Exception ex) {
            int lastSlash = fileUrl.lastIndexOf('/');
            return lastSlash >= 0 ? fileUrl.substring(lastSlash + 1) : fileUrl;
        }
    }

    private DocumentVO toVO(KnowledgeDocumentDO doc) {
        return new DocumentVO(
                doc.getId(), doc.getKbId(), doc.getDocName(),
                doc.getEnabled(), doc.getChunkCount(), doc.getFileUrl(),
                doc.getFileType(), doc.getFileSize(), doc.getProcessMode(),
                doc.getStatus(), doc.getSourceType(), doc.getSourceLocation(),
                doc.getChunkStrategy(), doc.getChunkConfig(), doc.getPipelineId(),
                doc.getCreateTime(), doc.getUpdateTime(),
                doc.getScheduleEnabled(), doc.getScheduleCron(),
                doc.getLastSyncTime(), doc.getLastContentHash()
        );
    }
}
