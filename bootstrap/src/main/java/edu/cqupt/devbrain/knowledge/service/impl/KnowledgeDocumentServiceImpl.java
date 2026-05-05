package edu.cqupt.devbrain.knowledge.service.impl;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import cn.hutool.core.util.IdUtil;
import edu.cqupt.devbrain.core.chunk.ChunkEmbeddingService;
import edu.cqupt.devbrain.core.chunk.ChunkingMode;
import edu.cqupt.devbrain.core.chunk.ChunkingOptions;
import edu.cqupt.devbrain.core.chunk.ChunkingStrategy;
import edu.cqupt.devbrain.core.chunk.ChunkingStrategyFactory;
import edu.cqupt.devbrain.core.chunk.VectorChunk;
import edu.cqupt.devbrain.core.parser.DocumentParser;
import edu.cqupt.devbrain.core.parser.DocumentParserSelector;
import edu.cqupt.devbrain.framework.context.UserContext;
import edu.cqupt.devbrain.framework.errorcode.BaseErrorCode;
import edu.cqupt.devbrain.framework.exception.ClientException;
import edu.cqupt.devbrain.framework.exception.ServiceException;
import edu.cqupt.devbrain.ingestion.domain.IngestionStatus;
import edu.cqupt.devbrain.ingestion.domain.SourceType;
import edu.cqupt.devbrain.ingestion.domain.context.DocumentSource;
import edu.cqupt.devbrain.ingestion.domain.context.IngestionContext;
import edu.cqupt.devbrain.ingestion.domain.context.NodeLog;
import edu.cqupt.devbrain.ingestion.domain.pipeline.PipelineDefinition;
import edu.cqupt.devbrain.ingestion.domain.result.IngestionResult;
import edu.cqupt.devbrain.ingestion.engine.IngestionEngine;
import edu.cqupt.devbrain.ingestion.service.IngestionPipelineService;
import edu.cqupt.devbrain.knowledge.controller.request.OnlineDocumentImportRequest;
import edu.cqupt.devbrain.knowledge.controller.vo.DocumentVO;
import edu.cqupt.devbrain.knowledge.dao.entity.KnowledgeBaseDO;
import edu.cqupt.devbrain.knowledge.dao.entity.KnowledgeChunkDO;
import edu.cqupt.devbrain.knowledge.dao.entity.KnowledgeDocumentChunkLogDO;
import edu.cqupt.devbrain.knowledge.dao.entity.KnowledgeDocumentDO;
import edu.cqupt.devbrain.knowledge.dao.mapper.KnowledgeBaseMapper;
import edu.cqupt.devbrain.knowledge.dao.mapper.KnowledgeDocumentChunkLogMapper;
import edu.cqupt.devbrain.knowledge.dao.mapper.KnowledgeDocumentMapper;
import edu.cqupt.devbrain.knowledge.service.KnowledgeChunkService;
import edu.cqupt.devbrain.knowledge.mq.KnowledgeDocumentChunkProducer;
import edu.cqupt.devbrain.knowledge.service.KnowledgeDocumentService;
import edu.cqupt.devbrain.knowledge.service.validator.FileUploadValidator;
import edu.cqupt.devbrain.knowledge.storage.FileStorageService;
import edu.cqupt.devbrain.rag.core.vector.VectorStoreService;
import edu.cqupt.devbrain.sync.adapter.DocumentSourceAdapter;
import edu.cqupt.devbrain.sync.adapter.DocumentSourceAdapterRegistry;
import edu.cqupt.devbrain.sync.adapter.FetchedContent;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
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
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.time.LocalDateTime;
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
    private static final String PIPELINE_PROCESS_MODE = "pipeline";
    private static final String SOURCE_TYPE = "file";
    private static final String ONLINE_FILE_TYPE = "txt";
    private static final String STATUS_PENDING = "pending";
    private static final String STATUS_PROCESSING = "processing";
    private static final String STATUS_RUNNING = "RUNNING";
    private static final String STATUS_COMPLETED = "completed";
    private static final String STATUS_FAILED = "failed";
    private static final String LOG_STATUS_SUCCESS = "SUCCESS";
    private static final String LOG_STATUS_FAILED = "FAILED";
    private static final String LOG_STATUS_PROCESSING = "processing";
    private static final Set<String> ONLINE_SOURCE_TYPES = Set.of("feishu", "url");
    private static final Set<String> DOCUMENT_STATUSES = Set.of("pending", "processing", "completed", "failed");

    private final KnowledgeBaseMapper knowledgeBaseMapper;
    private final KnowledgeDocumentMapper knowledgeDocumentMapper;
    private final KnowledgeDocumentChunkLogMapper chunkLogMapper;
    private final KnowledgeChunkService chunkService;
    private final FileStorageService fileStorageService;
    private final FileUploadValidator fileUploadValidator;
    private final TransactionTemplate transactionTemplate;
    private final DocumentSourceAdapterRegistry adapterRegistry;
    private final DocumentParserSelector parserSelector;
    private final ChunkingStrategyFactory strategyFactory;
    private final ChunkEmbeddingService chunkEmbeddingService;
    private final VectorStoreService vectorStoreService;
    private final ObjectMapper objectMapper;
    private final KnowledgeDocumentChunkProducer chunkProducer;
    private final IngestionEngine ingestionEngine;
    private final IngestionPipelineService ingestionPipelineService;

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
        String cleanedChunkConfig = normalizeChunkConfig(chunkConfig);

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
                document.setChunkConfig(cleanedChunkConfig);
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

        log.info("文档上传已入库，等待用户手动确认分块，kbId={}, docId={}", kbId, result.id());
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
        String cleanedChunkConfig = normalizeChunkConfig(request.chunkConfig());

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

        DocumentVO result;
        try {
            result = transactionTemplate.execute(status -> {
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
                document.setChunkConfig(cleanedChunkConfig);
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

        log.info("在线文档导入已入库，等待用户手动确认分块，kbId={}, docId={}", kbId, result.id());
        return result;
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
    public void updateChunkConfig(String docId, String chunkStrategy, String chunkConfig) {
        KnowledgeDocumentDO document = knowledgeDocumentMapper.selectById(docId);
        if (document == null || Integer.valueOf(1).equals(document.getDeleted())) {
            throw new ClientException("文档不存在或已删除：" + docId);
        }
        document.setChunkStrategy(clean(chunkStrategy));
        document.setChunkConfig(normalizeChunkConfig(chunkConfig));
        document.setProcessMode(DEFAULT_PROCESS_MODE);
        document.setUpdatedBy(UserContext.requireUser().userId());
        knowledgeDocumentMapper.updateById(document);
        log.info("文档分块配置已更新，docId={}, chunkStrategy={}", docId, document.getChunkStrategy());
    }

    @Override
    public boolean startChunk(String docId) {
        if (!StringUtils.hasText(docId)) {
            throw new ClientException("文档 ID 不能为空");
        }
        KnowledgeDocumentDO document = knowledgeDocumentMapper.selectById(docId);
        if (document == null || Integer.valueOf(1).equals(document.getDeleted())) {
            throw new ClientException("文档不存在或已删除：" + docId);
        }

        // 本项目数据库状态仍使用 processing 表示 RUNNING，避免引入新状态破坏既有查询和前端枚举。
        String status = clean(document.getStatus());
        if (STATUS_COMPLETED.equalsIgnoreCase(status)) {
            log.info("文档已分块成功，不再触发异步分块，docId={}", docId);
            return false;
        }
        if (!STATUS_PENDING.equalsIgnoreCase(status) && !STATUS_FAILED.equalsIgnoreCase(status)) {
            log.info("文档当前状态不允许触发异步分块，docId={}, status={}", docId, status);
            return false;
        }

        String operator = resolveOperator(document);
        boolean triggered = chunkProducer.startChunk(document.getId(), document.getKbId(), operator);
        if (!triggered) {
            log.warn("文档异步分块事务消息未提交，docId={}, kbId={}, operator={}",
                    document.getId(), document.getKbId(), operator);
        }
        return triggered;
    }

    @Override
    public void delete(String kbId, String docId) {
        KnowledgeDocumentDO document = requireDocument(kbId, docId);
        document.setUpdatedBy(UserContext.requireUser().userId());
        cleanupStoredFile(document);
        knowledgeDocumentMapper.deleteById(document);
        log.info("文档已逻辑删除，kbId={}, docId={}", kbId, docId);
    }

    @Override
    public void executeChunk(String docId) {
        KnowledgeDocumentDO doc = knowledgeDocumentMapper.selectById(docId);
        if (doc == null || Integer.valueOf(1).equals(doc.getDeleted())) {
            return;
        }
        if (!isExecutableStatus(doc.getStatus())) {
            log.info("文档状态不需要分块，跳过执行，docId={}, status={}", docId, doc.getStatus());
            return;
        }

        long totalStart = System.currentTimeMillis();
        KnowledgeDocumentChunkLogDO logEntry = null;
        boolean logInserted = false;
        try {
            doc.setStatus(STATUS_PROCESSING);
            knowledgeDocumentMapper.updateById(doc);

            logEntry = new KnowledgeDocumentChunkLogDO();
            logEntry.setDocId(docId);
            logEntry.setKbId(doc.getKbId());
            logEntry.setProcessMode(doc.getProcessMode());
            logEntry.setChunkStrategy(doc.getChunkStrategy());
            logEntry.setPipelineId(doc.getPipelineId());
            logEntry.setStatus(LOG_STATUS_PROCESSING);
            logEntry.setCreateTime(LocalDateTime.now());
            logEntry.setStartTime(LocalDateTime.now());
            chunkLogMapper.insert(logEntry);
            logInserted = true;

            if (PIPELINE_PROCESS_MODE.equalsIgnoreCase(clean(doc.getProcessMode()))) {
                runPipelineProcess(doc, logEntry);
            } else {
                runChunkProcess(doc, logEntry);
            }
            long totalDuration = System.currentTimeMillis() - totalStart;

            logEntry.setStatus(LOG_STATUS_SUCCESS);
            logEntry.setTotalDuration(totalDuration);
            logEntry.setEndTime(LocalDateTime.now());
            chunkLogMapper.updateById(logEntry);

            doc.setStatus(STATUS_COMPLETED);
            knowledgeDocumentMapper.updateById(doc);

            log.info("文档分块处理完成，docId={}, chunkCount={}, totalDuration={}ms",
                    docId, logEntry.getChunkCount(), totalDuration);
        } catch (Exception e) {
            long totalDuration = System.currentTimeMillis() - totalStart;
            if (logInserted) {
                logEntry.setStatus(LOG_STATUS_FAILED);
                logEntry.setErrorMessage(truncate(e.getMessage(), 2000));
                logEntry.setTotalDuration(totalDuration);
                logEntry.setEndTime(LocalDateTime.now());
                chunkLogMapper.updateById(logEntry);
            }

            doc.setStatus(STATUS_FAILED);
            knowledgeDocumentMapper.updateById(doc);

            log.error("文档分块处理失败，docId={}", docId, e);
            throw new ServiceException("文档分块处理失败: " + e.getMessage(), e, BaseErrorCode.SERVICE_ERROR);
        }
    }

    /**
     * 执行完整的文档分块处理流程：解析文本 → 分块 → 嵌入向量 → 持久化。
     *
     * @param doc      文档记录
     * @param logEntry 解析日志记录，用于记录各阶段耗时
     */
    private void runChunkProcess(KnowledgeDocumentDO doc, KnowledgeDocumentChunkLogDO logEntry) {
        // 1. 解析文档
        long extractStart = System.currentTimeMillis();
        String text = extractDocumentText(doc);
        long extractDuration = System.currentTimeMillis() - extractStart;
        logEntry.setExtractDuration(extractDuration);

        if (!StringUtils.hasText(text)) {
            logEntry.setChunkCount(0);
            log.warn("文档内容为空，跳过分块，docId={}", doc.getId());
            return;
        }

        // 2. 分块
        long chunkStart = System.currentTimeMillis();
        ChunkingMode mode = StringUtils.hasText(doc.getChunkStrategy())
                ? ChunkingMode.fromValue(doc.getChunkStrategy())
                : ChunkingMode.FIXED_SIZE;
        ChunkingStrategy strategy = strategyFactory.requireStrategy(mode);
        ChunkingOptions options = buildChunkOptions(mode, doc.getChunkConfig());
        List<VectorChunk> chunks = strategy.chunk(text, options);
        long chunkDuration = System.currentTimeMillis() - chunkStart;
        logEntry.setChunkDuration(chunkDuration);

        if (chunks.isEmpty()) {
            logEntry.setChunkCount(0);
            log.warn("分块结果为空，docId={}", doc.getId());
            return;
        }

        // 3. 嵌入
        long embedStart = System.currentTimeMillis();
        KnowledgeBaseDO kb = requireKnowledgeBase(doc.getKbId());
        String embeddingModel = kb.getEmbeddingModel();
        if (StringUtils.hasText(embeddingModel)) {
            chunkEmbeddingService.embed(chunks, embeddingModel);
        }
        long embedDuration = System.currentTimeMillis() - embedStart;
        logEntry.setEmbedDuration(embedDuration);

        // 4. 持久化
        long persistStart = System.currentTimeMillis();
        persistChunksAndVectorsAtomically(doc, chunks);
        long persistDuration = System.currentTimeMillis() - persistStart;
        logEntry.setPersistDuration(persistDuration);

        logEntry.setChunkCount(chunks.size());
    }

    /**
     * 执行 Pipeline 模式处理。Pipeline 节点负责解析、增强、分块和向量生成，
     * Indexer 节点通过 skipIndexerWrite 只做校验，最终写库仍由知识库模块统一完成。
     */
    private void runPipelineProcess(KnowledgeDocumentDO doc, KnowledgeDocumentChunkLogDO logEntry) {
        long extractStart = System.currentTimeMillis();
        String objectKey = resolveObjectKey(doc);
        byte[] fileBytes = loadFileBytes(objectKey);
        long extractDuration = System.currentTimeMillis() - extractStart;
        logEntry.setExtractDuration(extractDuration);

        long chunkStart = System.currentTimeMillis();
        PipelineDefinition pipeline = ingestionPipelineService.getDefinition(
                cleanRequired(doc.getPipelineId(), "PIPELINE 模式必须配置 pipelineId"));
        IngestionContext context = IngestionContext.builder()
                .taskId(doc.getId())
                .pipelineId(pipeline.getId())
                .source(DocumentSource.builder()
                        .type(SourceType.FILE)
                        .location(objectKey)
                        .fileName(doc.getDocName())
                        .build())
                .rawBytes(fileBytes)
                .mimeType(doc.getFileType())
                .vectorSpaceId(collectionName(doc))
                .skipIndexerWrite(true)
                .metadata(new HashMap<>(Map.of(
                        "kb_id", doc.getKbId(),
                        "doc_id", doc.getId(),
                        "doc_name", doc.getDocName()
                )))
                .status(IngestionStatus.RUNNING)
                .build();
        IngestionResult result = ingestionEngine.execute(pipeline, context);
        if (result.getStatus() == IngestionStatus.FAILED) {
            throw new ServiceException("Pipeline 执行失败: " + result.getMessage(), BaseErrorCode.SERVICE_ERROR);
        }
        List<VectorChunk> chunks = context.getChunks() == null ? List.of() : context.getChunks();
        long chunkDuration = System.currentTimeMillis() - chunkStart;
        logEntry.setChunkDuration(chunkDuration);
        logEntry.setEmbedDuration(0L);

        long persistStart = System.currentTimeMillis();
        persistChunksAndVectorsAtomically(doc, chunks);
        long persistDuration = System.currentTimeMillis() - persistStart;
        logEntry.setPersistDuration(persistDuration);
        logEntry.setChunkCount(chunks.size());
        appendPipelineLogs(logEntry, context.getLogs());
    }

    /**
     * 原子化持久化分块到数据库，并同步更新文档的分块计数。
     *
     * @param doc    文档记录
     * @param chunks 向量分块列表
     */
    public void persistChunksAndVectorsAtomically(KnowledgeDocumentDO doc, List<VectorChunk> chunks) {
        List<VectorChunk> safeChunks = chunks == null ? List.of() : chunks;
        List<KnowledgeChunkDO> chunkDOs = safeChunks.stream()
                .map(vc -> toChunkDO(doc, vc))
                .toList();
        String collectionName = collectionName(doc);

        String finalCollectionName = collectionName;
        transactionTemplate.executeWithoutResult(status -> {
            // 先删除旧 chunk，再写入新 chunk；向量清理由本方法统一控制，避免旧索引残留。
            chunkService.deleteByDocId(doc.getId());
            chunkService.batchCreate(chunkDOs, false);
            vectorStoreService.deleteDocumentVectors(finalCollectionName, doc.getId());
            vectorStoreService.indexDocumentChunks(finalCollectionName, doc.getId(), safeChunks);

            doc.setChunkCount((long) safeChunks.size());
            doc.setStatus(STATUS_COMPLETED);
            knowledgeDocumentMapper.updateById(doc);
        });

        log.info("分块和向量持久化完成，docId={}, collectionName={}, count={}",
                doc.getId(), collectionName, safeChunks.size());
    }

    /**
     * 将核心 VectorChunk 模型转换为数据库分块实体。
     *
     * @param doc 文档记录
     * @param vc  向量分块
     * @return 数据库分块实体
     */
    private KnowledgeChunkDO toChunkDO(KnowledgeDocumentDO doc, VectorChunk vc) {
        String chunkId = StringUtils.hasText(vc.getChunkId()) ? vc.getChunkId() : IdUtil.fastSimpleUUID();
        vc.setChunkId(chunkId);
        Map<String, Object> metadata = vc.getMetadata() == null ? new HashMap<>() : new HashMap<>(vc.getMetadata());
        metadata.put("kb_id", doc.getKbId());
        metadata.put("doc_id", doc.getId());
        metadata.put("chunk_index", vc.getIndex());
        vc.setMetadata(metadata);

        KnowledgeChunkDO chunk = new KnowledgeChunkDO();
        chunk.setId(chunkId);
        chunk.setKbId(doc.getKbId());
        chunk.setDocId(doc.getId());
        chunk.setChunkIndex(vc.getIndex());
        chunk.setContent(vc.getContent());
        chunk.setContentHash(sha256(vc.getContent()));
        chunk.setCharCount(vc.getContent().length());
        chunk.setMetadata(writeMetadata(metadata));
        chunk.setEnabled(1);
        chunk.setCreatedBy(doc.getCreatedBy());
        chunk.setUpdatedBy(doc.getUpdatedBy());
        return chunk;
    }

    private String writeMetadata(Map<String, Object> metadata) {
        try {
            return objectMapper.writeValueAsString(metadata == null ? Map.of() : metadata);
        } catch (Exception e) {
            throw new ServiceException("分块元数据序列化失败", e, BaseErrorCode.SERVICE_ERROR);
        }
    }

    /**
     * 判断文档当前状态是否允许执行分块任务。
     * completed 直接跳过，防止分块成功后重复触发造成额外写入。
     */
    private boolean isExecutableStatus(String status) {
        if (!StringUtils.hasText(status)) {
            return true;
        }
        return STATUS_PENDING.equalsIgnoreCase(status)
                || STATUS_PROCESSING.equalsIgnoreCase(status)
                || STATUS_RUNNING.equalsIgnoreCase(status);
    }

    /**
     * 知识库模块统一使用 kb_{kbId} 作为向量集合名，与 KnowledgeChunkService 和 VectorStoreService 约定一致。
     */
    private String collectionName(KnowledgeDocumentDO doc) {
        return "kb_" + doc.getKbId();
    }

    /**
     * 解析对象存储 key。上传文件优先从 fileUrl 提取；若缺失则回退到 sourceLocation。
     */
    private String resolveObjectKey(KnowledgeDocumentDO doc) {
        String objectKey = extractObjectKey(doc.getFileUrl());
        if (!StringUtils.hasText(objectKey)) {
            objectKey = clean(doc.getSourceLocation());
        }
        if (!StringUtils.hasText(objectKey)) {
            throw new ServiceException("文档存储路径为空", BaseErrorCode.SERVICE_ERROR);
        }
        return objectKey;
    }

    /**
     * 从对象存储加载文件字节，供 Pipeline 节点复用原始内容。
     */
    private byte[] loadFileBytes(String objectKey) {
        try (var is = fileStorageService.download(objectKey)) {
            return is.readAllBytes();
        } catch (Exception e) {
            log.error("加载 Pipeline 原始文件失败，objectKey={}", objectKey, e);
            throw new ServiceException("加载 Pipeline 原始文件失败", e, BaseErrorCode.SERVICE_ERROR);
        }
    }

    /**
     * 将 Pipeline 节点日志摘要附加到分块日志 errorMessage 字段，便于在现有日志表中查看节点链路。
     */
    private void appendPipelineLogs(KnowledgeDocumentChunkLogDO logEntry, List<NodeLog> logs) {
        if (logs == null || logs.isEmpty()) {
            return;
        }
        String summary = logs.stream()
                .map(nodeLog -> nodeLog.getNodeId() + "(" + nodeLog.getNodeType() + "): "
                        + (nodeLog.isSuccess() ? "OK" : "FAILED")
                        + ", " + nodeLog.getDurationMs() + "ms"
                        + (StringUtils.hasText(nodeLog.getMessage()) ? ", " + nodeLog.getMessage() : ""))
                .reduce((left, right) -> left + "\n" + right)
                .orElse(null);
        logEntry.setErrorMessage(truncate(summary, 2000));
    }

    /**
     * 清理字符串并校验必填，避免 Pipeline 模式缺少关键配置时继续执行。
     */
    private String cleanRequired(String value, String message) {
        String cleaned = clean(value);
        if (!StringUtils.hasText(cleaned)) {
            throw new ClientException(message);
        }
        return cleaned;
    }

    /**
     * 从对象存储下载文档并提取纯文本内容。
     *
     * @param doc 文档记录
     * @return 提取出的纯文本
     */
    private String extractDocumentText(KnowledgeDocumentDO doc) {
        String objectKey = resolveObjectKey(doc);
        try (var is = fileStorageService.download(objectKey)) {
            DocumentParser parser = parserSelector.selectByMimeType(doc.getFileType());
            return parser.extractText(is, doc.getFileType());
        } catch (Exception e) {
            log.error("文档内容提取失败，docId={}, objectKey={}", doc.getId(), objectKey, e);
            throw new ServiceException("文档内容提取失败", e, BaseErrorCode.SERVICE_ERROR);
        }
    }

    /**
     * 根据分块模式和 JSON 配置构建分块选项，解析失败时使用默认配置。
     *
     * @param mode           分块模式
     * @param chunkConfigJson 分块配置 JSON 字符串
     * @return 分块选项
     */
    private ChunkingOptions buildChunkOptions(ChunkingMode mode, String chunkConfigJson) {
        if (StringUtils.hasText(chunkConfigJson)) {
            try {
                java.util.Map<String, Object> configMap = objectMapper.readValue(
                        chunkConfigJson, new TypeReference<java.util.Map<String, Object>>() {});
                return mode.createOptions(configMap);
            } catch (Exception e) {
                log.warn("解析分块配置失败，使用默认配置: {}", chunkConfigJson, e);
            }
        }
        return mode.createDefaultOptions(null, null);
    }

    /**
     * 解析异步分块操作人，优先使用当前登录用户，兜底使用文档审计字段。
     *
     * @param document 文档记录
     * @return 操作人用户 ID，可为空
     */
    private String resolveOperator(KnowledgeDocumentDO document) {
        if (UserContext.hasUser()) {
            return UserContext.getUserId();
        }
        if (StringUtils.hasText(document.getUpdatedBy())) {
            return document.getUpdatedBy();
        }
        return document.getCreatedBy();
    }

    /**
     * 截断文本到指定最大长度，超出部分以省略号代替。
     *
     * @param text   原始文本
     * @param maxLen 最大长度
     * @return 截断后的文本
     */
    private String truncate(String text, int maxLen) {
        if (text == null) {
            return null;
        }
        return text.length() <= maxLen ? text : text.substring(0, maxLen) + "...";
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

    /**
     * 查询并确认知识库存在且未被逻辑删除。
     *
     * @param kbId 知识库 ID
     * @return 知识库记录
     */
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

    /**
     * 查询并确认文档存在、未删除且归属指定知识库。
     *
     * @param kbId  知识库 ID
     * @param docId 文档 ID
     * @return 文档记录
     */
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

    /**
     * 校验启用状态参数是否合法（只能为 0 或 1）。
     *
     * @param enabled 启用状态
     */
    private void ensureEnabledValid(Integer enabled) {
        if (enabled == null || (enabled != 0 && enabled != 1)) {
            throw new ClientException("enabled 只能为 0 或 1");
        }
    }

    /**
     * 通过适配器抓取在线文档内容。
     *
     * @param sourceType     来源类型（feishu、url）
     * @param sourceLocation 来源地址
     * @return 抓取到的内容
     */
    private FetchedContent fetchOnlineContent(String sourceType, String sourceLocation) {
        try {
            DocumentSourceAdapter adapter = adapterRegistry.requireAdapter(sourceType);
            return adapter.fetchContent(sourceLocation);
        } catch (ServiceException e) {
            throw e;
        } catch (Exception e) {
            log.error("在线文档抓取失败，sourceType={}, sourceLocation={}", sourceType, sourceLocation, e);
            throw new ClientException("在线文档抓取失败: " + e.getMessage());
        }
    }

    /**
     * 校验定时同步的 Cron 表达式格式。
     *
     * @param scheduleEnabled 是否启用定时同步
     * @param scheduleCron    Cron 表达式
     */
    private void validateSchedule(Integer scheduleEnabled, String scheduleCron) {
        if (scheduleEnabled != null && scheduleEnabled == 1 && StringUtils.hasText(scheduleCron)) {
            try {
                org.springframework.scheduling.support.CronExpression.parse(scheduleCron);
            } catch (IllegalArgumentException e) {
                throw new ClientException("Cron 表达式格式错误: " + e.getMessage());
            }
        }
    }

    /**
     * 标准化分块配置 JSON 字符串，校验合法性后返回。
     *
     * @param chunkConfig 分块配置 JSON 字符串
     * @return 标准化后的 JSON 字符串，空输入返回 null
     */
    private String normalizeChunkConfig(String chunkConfig) {
        String cleaned = clean(chunkConfig);
        if (!StringUtils.hasText(cleaned)) {
            return null;
        }
        try {
            objectMapper.readTree(cleaned);
            return cleaned;
        } catch (Exception e) {
            throw new ClientException("分块配置必须是合法 JSON");
        }
    }

    /**
     * 解析在线文档名称，优先使用用户指定名称，其次使用抓取标题，兜底使用来源类型默认名。
     *
     * @param requestedName 用户指定的文档名称
     * @param fetchedTitle  抓取到的文档标题
     * @param sourceType    来源类型
     * @return 清洗后的文档名称
     */
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

    /**
     * 标准化文档状态名称，兼容旧状态值（running→processing，success→completed）。
     *
     * @param status 原始状态
     * @return 标准化后的状态
     */
    private String normalizeStatus(String status) {
        if ("running".equals(status)) {
            return "processing";
        }
        if ("success".equals(status)) {
            return "completed";
        }
        return status;
    }

    /**
     * 统一去除字符串前后空白，保留 null 语义。
     *
     * @param value 原始字符串
     * @return 清洗后的字符串
     */
    private String clean(String value) {
        return value == null ? null : value.trim();
    }

    /**
     * 计算文本的 SHA-256 哈希值，用于内容变更检测。
     *
     * @param text 原始文本
     * @return 十六进制哈希字符串
     */
    private String sha256(String text) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(text.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new ServiceException("SHA-256 算法不可用", e, BaseErrorCode.SERVICE_ERROR);
        }
    }

    /**
     * 清理文档对应的对象存储文件，删除失败仅记录日志不阻断流程。
     *
     * @param document 文档记录
     */
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

    /**
     * 从文件 URL 中提取对象存储 key（最后一段路径）。
     *
     * @param fileUrl 文件访问 URL
     * @return 对象存储 key
     */
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

    /**
     * 将文档数据库实体转换为前端视图对象。
     *
     * @param doc 文档数据库实体
     * @return 文档视图对象
     */
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
