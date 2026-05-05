package edu.cqupt.devbrain.knowledge.service.impl;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import cn.hutool.core.util.IdUtil;
import edu.cqupt.devbrain.auth.core.DigestSupport;
import edu.cqupt.devbrain.core.chunk.ChunkingMode;
import edu.cqupt.devbrain.core.chunk.ChunkingOptions;
import edu.cqupt.devbrain.core.chunk.ChunkingStrategy;
import edu.cqupt.devbrain.core.chunk.ChunkingStrategyFactory;
import edu.cqupt.devbrain.core.chunk.FixedSizeOptions;
import edu.cqupt.devbrain.core.chunk.TextBoundaryOptions;
import edu.cqupt.devbrain.core.chunk.VectorChunk;
import edu.cqupt.devbrain.core.parser.DocumentParser;
import edu.cqupt.devbrain.core.parser.DocumentParserSelector;
import edu.cqupt.devbrain.framework.errorcode.BaseErrorCode;
import edu.cqupt.devbrain.framework.exception.ClientException;
import edu.cqupt.devbrain.framework.exception.ServiceException;
import edu.cqupt.devbrain.knowledge.dao.entity.KnowledgeChunkDO;
import edu.cqupt.devbrain.knowledge.dao.entity.KnowledgeDocumentChunkLogDO;
import edu.cqupt.devbrain.knowledge.dao.entity.KnowledgeDocumentDO;
import edu.cqupt.devbrain.knowledge.dao.mapper.KnowledgeChunkMapper;
import edu.cqupt.devbrain.knowledge.dao.mapper.KnowledgeDocumentChunkLogMapper;
import edu.cqupt.devbrain.knowledge.dao.mapper.KnowledgeDocumentMapper;
import edu.cqupt.devbrain.knowledge.service.DocumentParseService;
import edu.cqupt.devbrain.knowledge.storage.FileStorageService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.io.InputStream;
import java.net.URI;
import java.util.ArrayList;
import java.time.LocalDateTime;
import java.util.Date;
import java.util.List;
import java.util.Map;

/**
 * 文档解析服务实现，串联文本提取、文本分块和分块持久化三个阶段。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DocumentParseServiceImpl implements DocumentParseService {

    /**
     * 文档解析运行中状态。
     */
    private static final String STATUS_RUNNING = "RUNNING";

    /**
     * 文档解析成功状态。
     */
    private static final String STATUS_SUCCESS = "SUCCESS";

    /**
     * 文档解析失败状态。
     */
    private static final String STATUS_FAILED = "FAILED";

    /**
     * 兼容知识库文档表当前已有的处理中状态。
     */
    private static final String DOCUMENT_STATUS_PROCESSING = "processing";

    /**
     * 兼容知识库文档表当前已有的完成状态。
     */
    private static final String DOCUMENT_STATUS_COMPLETED = "completed";

    /**
     * 兼容知识库文档表当前已有的失败状态。
     */
    private static final String DOCUMENT_STATUS_FAILED = "failed";

    /**
     * 默认 MIME 类型，文件类型无法识别时交给 Tika 兜底处理。
     */
    private static final String DEFAULT_MIME_TYPE = "application/octet-stream";

    /**
     * 默认固定长度分块配置。
     */
    private static final FixedSizeOptions DEFAULT_FIXED_SIZE_OPTIONS = new FixedSizeOptions(512, 128);

    /**
     * Jackson 类型引用，用于读取 chunk_config JSON。
     */
    private static final TypeReference<Map<String, Object>> MAP_TYPE_REFERENCE = new TypeReference<>() {
    };

    private final KnowledgeDocumentMapper knowledgeDocumentMapper;
    private final KnowledgeDocumentChunkLogMapper chunkLogMapper;
    private final KnowledgeChunkMapper knowledgeChunkMapper;
    private final FileStorageService fileStorageService;
    private final DocumentParserSelector parserSelector;
    private final ChunkingStrategyFactory chunkingStrategyFactory;
    private final ObjectMapper objectMapper;
    private final DocumentParsePersistenceService persistenceService;

    /**
     * 解析指定文档，记录各阶段耗时，并在成功后原子替换文档分块。
     *
     * @param docId 文档 ID
     */
    @Override
    public void parseAndChunk(String docId) {
        KnowledgeDocumentDO document = requireDocument(docId);
        ensureRunnable(document);

        long totalStart = System.currentTimeMillis();
        KnowledgeDocumentChunkLogDO logRecord = null;
        Long extractDuration = null;
        Long chunkDuration = null;
        Long persistDuration = null;
        List<VectorChunk> chunks = List.of();

        try {
            logRecord = createRunningLog(document);
            if (!DOCUMENT_STATUS_PROCESSING.equalsIgnoreCase(document.getStatus())) {
                updateDocumentStatus(document, DOCUMENT_STATUS_PROCESSING);
            }

            long extractStart = System.currentTimeMillis();
            String text = extractText(document);
            extractDuration = elapsedSince(extractStart);
            if (!StringUtils.hasText(text)) {
                throw new ClientException("文档内容为空");
            }

            long chunkStart = System.currentTimeMillis();
            ChunkingMode chunkingMode = resolveChunkingMode(document.getChunkStrategy());
            ChunkingOptions chunkingOptions = resolveChunkingOptions(chunkingMode, document.getChunkConfig());
            ChunkingStrategy strategy = chunkingStrategyFactory.requireStrategy(chunkingMode);
            chunks = strategy.chunk(text, chunkingOptions);
            chunkDuration = elapsedSince(chunkStart);

            long persistStart = System.currentTimeMillis();
            persistenceService.persistChunksAndSuccess(document, logRecord, chunks,
                    extractDuration, chunkDuration, persistStart, totalStart);
            persistDuration = elapsedSince(persistStart);

            log.info("文档解析完成，docId={}, chunkCount={}", docId, chunks.size());
        } catch (Exception e) {
            handleParseFailure(document, logRecord, e, extractDuration, chunkDuration, persistDuration,
                    elapsedSince(totalStart), chunks.size());
            throw e;
        }
    }

    /**
     * 查询指定文档最新解析日志。
     *
     * @param docId 文档 ID
     * @return 最新解析日志
     */
    @Override
    public KnowledgeDocumentChunkLogDO getLatestLog(String docId) {
        return chunkLogMapper.selectLatestByDocId(docId);
    }

    /**
     * 查询指定文档已经持久化的分块，并转换为核心 VectorChunk 模型。
     *
     * @param docId 文档 ID
     * @return 文档分块列表
     */
    @Override
    public List<VectorChunk> getChunks(String docId) {
        return knowledgeChunkMapper.selectByDocId(docId).stream()
                .map(this::toVectorChunk)
                .toList();
    }

    /**
     * 仅允许失败状态的文档进入重试流程。
     *
     * @param docId 文档 ID
     */
    @Override
    public void retryParse(String docId) {
        KnowledgeDocumentDO document = requireDocument(docId);
        if (!DOCUMENT_STATUS_FAILED.equalsIgnoreCase(document.getStatus())
                && !STATUS_FAILED.equalsIgnoreCase(document.getStatus())) {
            throw new ClientException("仅失败的文档允许重试解析");
        }
        parseAndChunk(docId);
    }

    /**
     * 查询文档记录，并排除不存在或已逻辑删除的情况。
     *
     * @param docId 文档 ID
     * @return 文档记录
     */
    private KnowledgeDocumentDO requireDocument(String docId) {
        if (!StringUtils.hasText(docId)) {
            throw new ClientException("文档 ID 不能为空");
        }
        KnowledgeDocumentDO document = knowledgeDocumentMapper.selectById(docId);
        if (document == null || Integer.valueOf(1).equals(document.getDeleted())) {
            throw new ClientException("文档不存在或已删除");
        }
        return document;
    }

    /**
     * 检查文档是否可执行解析。
     * <p>
     * 异步入口会先通过事务消息把文档置为 processing，此时尚未创建 RUNNING 日志，应允许消费者继续执行。
     *
     * @param document 文档记录
     */
    private void ensureRunnable(KnowledgeDocumentDO document) {
        String status = document.getStatus();
        KnowledgeDocumentChunkLogDO latestLog = chunkLogMapper.selectLatestByDocId(document.getId());
        if (latestLog != null && STATUS_RUNNING.equalsIgnoreCase(latestLog.getStatus())) {
            throw new ClientException("文档正在解析中");
        }
    }

    /**
     * 创建运行中的解析日志。
     *
     * @param document 文档记录
     * @return 已插入数据库的日志记录
     */
    private KnowledgeDocumentChunkLogDO createRunningLog(KnowledgeDocumentDO document) {
        KnowledgeDocumentChunkLogDO logRecord = new KnowledgeDocumentChunkLogDO();
        logRecord.setDocId(document.getId());
        logRecord.setKbId(document.getKbId());
        logRecord.setStatus(STATUS_RUNNING);
        logRecord.setProcessMode(document.getProcessMode());
        logRecord.setChunkStrategy(document.getChunkStrategy());
        logRecord.setPipelineId(document.getPipelineId());
        logRecord.setStartTime(LocalDateTime.now());
        chunkLogMapper.insert(logRecord);
        return logRecord;
    }

    /**
     * 从对象存储下载文档并使用匹配的解析器提取纯文本。
     *
     * @param document 文档记录
     * @return 提取出的纯文本
     */
    private String extractText(KnowledgeDocumentDO document) {
        String objectKey = extractObjectKey(document.getFileUrl());
        if (!StringUtils.hasText(objectKey)) {
            throw new ClientException("文档文件地址为空");
        }

        String mimeType = resolveMimeType(document);
        DocumentParser parser = parserSelector.selectByMimeType(mimeType);
        try (InputStream inputStream = fileStorageService.download(objectKey)) {
            return parser.extractText(inputStream, document.getDocName());
        } catch (Exception e) {
            if (e instanceof ServiceException serviceException) {
                throw serviceException;
            }
            throw new ServiceException("文档文本提取失败: " + e.getMessage(), e, BaseErrorCode.SERVICE_ERROR);
        }
    }

    /**
     * 根据文档中的 chunk_strategy 字段解析分块模式，未配置时使用固定长度分块。
     *
     * @param chunkStrategy 文档配置的分块策略
     * @return 分块模式
     */
    private ChunkingMode resolveChunkingMode(String chunkStrategy) {
        if (!StringUtils.hasText(chunkStrategy)) {
            return ChunkingMode.FIXED_SIZE;
        }
        for (ChunkingMode mode : ChunkingMode.values()) {
            if (mode.getValue().equalsIgnoreCase(chunkStrategy) || mode.name().equalsIgnoreCase(chunkStrategy)) {
                return mode;
            }
        }
        throw new ClientException("不支持的分块策略: " + chunkStrategy);
    }

    /**
     * 根据分块模式和 JSON 配置构建具体 ChunkingOptions。
     *
     * @param mode 分块模式
     * @param chunkConfig JSON 字符串配置
     * @return 分块配置模型
     */
    private ChunkingOptions resolveChunkingOptions(ChunkingMode mode, String chunkConfig) {
        Map<String, Object> configMap = parseChunkConfig(chunkConfig);
        return switch (mode) {
            case FIXED_SIZE -> new FixedSizeOptions(
                    readInt(configMap, "chunkSize", DEFAULT_FIXED_SIZE_OPTIONS.chunkSize()),
                    readInt(configMap, "overlapSize", DEFAULT_FIXED_SIZE_OPTIONS.overlapSize())
            );
            case STRUCTURE_AWARE -> new TextBoundaryOptions(
                    readInt(configMap, "targetChars", 1400),
                    readInt(configMap, "overlapChars", 0),
                    readInt(configMap, "maxChars", 1800),
                    readInt(configMap, "minChars", 600)
            );
            case RECURSIVE_CHARACTER -> mode.createOptions(configMap);
            case QA_PAIR -> mode.createOptions(configMap);
            case TABLE_AWARE -> mode.createOptions(configMap);
            case SEMANTIC_CHUNKING -> mode.createOptions(configMap);
        };
    }

    /**
     * 解析 chunk_config JSON，空配置返回空 Map。
     *
     * @param chunkConfig JSON 字符串
     * @return 配置 Map
     */
    private Map<String, Object> parseChunkConfig(String chunkConfig) {
        if (!StringUtils.hasText(chunkConfig)) {
            return Map.of();
        }
        try {
            return objectMapper.readValue(chunkConfig, MAP_TYPE_REFERENCE);
        } catch (Exception e) {
            throw new ClientException("分块配置格式错误");
        }
    }

    /**
     * 从配置 Map 中读取整型值，不存在或无法转换时返回默认值。
     *
     * @param configMap 配置 Map
     * @param key 配置项 key
     * @param defaultValue 默认值
     * @return 配置整型值
     */
    private int readInt(Map<String, Object> configMap, String key, int defaultValue) {
        Object value = configMap.get(key);
        if (value instanceof Number number) {
            return number.intValue();
        }
        if (value instanceof String stringValue && StringUtils.hasText(stringValue)) {
            try {
                return Integer.parseInt(stringValue);
            } catch (NumberFormatException ignored) {
                return defaultValue;
            }
        }
        return defaultValue;
    }

    /**
     * 将数据库实体转换为 VectorChunk 模型。
     *
     * @param entity 分块实体
     * @return VectorChunk 模型
     */
    private VectorChunk toVectorChunk(KnowledgeChunkDO entity) {
        VectorChunk chunk = new VectorChunk();
        chunk.setChunkId(entity.getId());
        chunk.setIndex(entity.getChunkIndex());
        chunk.setContent(entity.getContent());
        chunk.setMetadata(parseMetadata(entity.getMetadata()));
        return chunk;
    }

    /**
     * 将数据库中的 JSON 元数据反序列化为 Map。
     *
     * @param metadata JSON 字符串
     * @return 元数据 Map
     */
    private Map<String, Object> parseMetadata(String metadata) {
        if (!StringUtils.hasText(metadata)) {
            return Map.of();
        }
        try {
            return objectMapper.readValue(metadata, MAP_TYPE_REFERENCE);
        } catch (Exception e) {
            return Map.of();
        }
    }

    /**
     * 处理解析失败，记录错误信息并更新文档状态为失败。
     *
     * @param document 文档记录
     * @param logRecord 解析日志记录
     * @param exception 原始异常
     * @param extractDuration 文本提取耗时
     * @param chunkDuration 分块耗时
     * @param persistDuration 持久化耗时
     * @param totalDuration 总耗时
     * @param chunkCount 已生成分块数量
     */
    private void handleParseFailure(KnowledgeDocumentDO document,
                                    KnowledgeDocumentChunkLogDO logRecord,
                                    Exception exception,
                                    Long extractDuration,
                                    Long chunkDuration,
                                    Long persistDuration,
                                    Long totalDuration,
                                    int chunkCount) {
        log.error("文档解析失败，docId={}", document.getId(), exception);
        document.setStatus(DOCUMENT_STATUS_FAILED);
        knowledgeDocumentMapper.updateById(document);

        if (logRecord == null) {
            return;
        }
        logRecord.setStatus(STATUS_FAILED);
        logRecord.setExtractDuration(extractDuration);
        logRecord.setChunkDuration(chunkDuration);
        logRecord.setPersistDuration(persistDuration);
        logRecord.setTotalDuration(totalDuration);
        logRecord.setChunkCount(chunkCount);
        logRecord.setErrorMessage(buildErrorMessage(exception));
        logRecord.setEndTime(LocalDateTime.now());
        chunkLogMapper.updateById(logRecord);
    }

    /**
     * 更新文档处理状态。
     *
     * @param document 文档记录
     * @param status 新状态
     */
    private void updateDocumentStatus(KnowledgeDocumentDO document, String status) {
        document.setStatus(status);
        knowledgeDocumentMapper.updateById(document);
    }

    /**
     * 根据文件类型推断 MIME 类型。
     *
     * @param document 文档记录
     * @return MIME 类型
     */
    private String resolveMimeType(KnowledgeDocumentDO document) {
        String fileType = document.getFileType();
        if (!StringUtils.hasText(fileType)) {
            return DEFAULT_MIME_TYPE;
        }
        return switch (fileType.toLowerCase()) {
            case "md", "markdown" -> "text/markdown";
            case "txt" -> "text/plain";
            case "pdf" -> "application/pdf";
            case "doc" -> "application/msword";
            case "docx" -> "application/vnd.openxmlformats-officedocument.wordprocessingml.document";
            case "xls" -> "application/vnd.ms-excel";
            case "xlsx" -> "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";
            case "ppt" -> "application/vnd.ms-powerpoint";
            case "pptx" -> "application/vnd.openxmlformats-officedocument.presentationml.presentation";
            case "html", "htm" -> "text/html";
            default -> DEFAULT_MIME_TYPE;
        };
    }

    /**
     * 从文件 URL 中提取对象存储 key。
     *
     * @param fileUrl 文件访问 URL
     * @return 对象 key
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
     * 计算从起始时间到当前时间的耗时。
     *
     * @param startMillis 起始毫秒时间戳
     * @return 耗时毫秒
     */
    private long elapsedSince(long startMillis) {
        return System.currentTimeMillis() - startMillis;
    }

    /**
     * 构建失败日志错误信息，保留异常类型和消息便于排查。
     *
     * @param exception 原始异常
     * @return 错误信息
     */
    private String buildErrorMessage(Exception exception) {
        String message = exception.getMessage();
        return exception.getClass().getSimpleName() + ": " + (message == null ? "" : message);
    }
}

/**
 * 文档分块持久化组件，独立成 Spring Bean 以确保 @Transactional 通过代理生效。
 */
@Service
@RequiredArgsConstructor
class DocumentParsePersistenceService {

    /**
     * 文档解析成功状态。
     */
    private static final String STATUS_SUCCESS = "SUCCESS";

    /**
     * 兼容知识库文档表当前已有的完成状态。
     */
    private static final String DOCUMENT_STATUS_COMPLETED = "completed";

    private final KnowledgeDocumentMapper knowledgeDocumentMapper;
    private final KnowledgeDocumentChunkLogMapper chunkLogMapper;
    private final KnowledgeChunkMapper knowledgeChunkMapper;
    private final ObjectMapper objectMapper;

    /**
     * 持久化阶段事务，保证删除旧分块、插入新分块和更新成功状态原子提交。
     *
     * @param document 文档记录
     * @param logRecord 解析日志记录
     * @param chunks 新分块列表
     * @param extractDuration 文本提取耗时
     * @param chunkDuration 文档分块耗时
     * @param persistStart 持久化阶段开始时间
     * @param totalStart 解析主流程开始时间
     */
    @Transactional(rollbackFor = Exception.class)
    public void persistChunksAndSuccess(KnowledgeDocumentDO document,
                                        KnowledgeDocumentChunkLogDO logRecord,
                                        List<VectorChunk> chunks,
                                        Long extractDuration,
                                        Long chunkDuration,
                                        long persistStart,
                                        long totalStart) {
        knowledgeChunkMapper.deleteByDocId(document.getId());
        if (!chunks.isEmpty()) {
            knowledgeChunkMapper.insertBatch(toChunkEntities(document, chunks));
        }

        document.setStatus(DOCUMENT_STATUS_COMPLETED);
        document.setChunkCount((long) chunks.size());
        knowledgeDocumentMapper.updateById(document);

        logRecord.setStatus(STATUS_SUCCESS);
        logRecord.setExtractDuration(extractDuration);
        logRecord.setChunkDuration(chunkDuration);
        logRecord.setPersistDuration(elapsedSince(persistStart));
        logRecord.setTotalDuration(elapsedSince(totalStart));
        logRecord.setChunkCount(chunks.size());
        logRecord.setEndTime(LocalDateTime.now());
        chunkLogMapper.updateById(logRecord);
    }

    /**
     * 将核心 VectorChunk 模型转换为数据库分块实体。
     *
     * @param document 文档记录
     * @param chunks VectorChunk 列表
     * @return 数据库实体列表
     */
    private List<KnowledgeChunkDO> toChunkEntities(KnowledgeDocumentDO document, List<VectorChunk> chunks) {
        List<KnowledgeChunkDO> entities = new ArrayList<>(chunks.size());
        for (VectorChunk chunk : chunks) {
            String content = chunk.getContent() == null ? "" : chunk.getContent();
            KnowledgeChunkDO entity = new KnowledgeChunkDO();
            entity.setId(StringUtils.hasText(chunk.getChunkId()) ? chunk.getChunkId() : IdUtil.fastSimpleUUID());
            entity.setKbId(document.getKbId());
            entity.setDocId(document.getId());
            entity.setChunkIndex(chunk.getIndex());
            entity.setContent(content);
            entity.setContentHash(DigestSupport.sha256(content));
            entity.setCharCount(content.length());
            entity.setMetadata(writeMetadata(chunk.getMetadata()));
            entity.setEnabled(1);
            entity.setCreatedBy(document.getCreatedBy());
            entity.setUpdatedBy(document.getUpdatedBy());
            entities.add(entity);
        }
        return entities;
    }

    /**
     * 将元数据 Map 序列化为 JSON 字符串。
     *
     * @param metadata 元数据
     * @return JSON 字符串
     */
    private String writeMetadata(Map<String, Object> metadata) {
        try {
            return objectMapper.writeValueAsString(metadata == null ? Map.of() : metadata);
        } catch (Exception e) {
            throw new ServiceException("分块元数据序列化失败", e, BaseErrorCode.SERVICE_ERROR);
        }
    }

    /**
     * 计算从起始时间到当前时间的耗时。
     *
     * @param startMillis 起始毫秒时间戳
     * @return 耗时毫秒
     */
    private long elapsedSince(long startMillis) {
        return System.currentTimeMillis() - startMillis;
    }
}
