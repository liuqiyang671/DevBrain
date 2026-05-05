package edu.cqupt.devbrain.ingestion.service.impl;

import cn.hutool.core.util.IdUtil;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import edu.cqupt.devbrain.framework.context.UserContext;
import edu.cqupt.devbrain.framework.errorcode.BaseErrorCode;
import edu.cqupt.devbrain.framework.exception.ClientException;
import edu.cqupt.devbrain.framework.exception.ServiceException;
import edu.cqupt.devbrain.ingestion.controller.request.ExecuteTaskRequest;
import edu.cqupt.devbrain.ingestion.controller.request.IngestionTaskPageRequest;
import edu.cqupt.devbrain.ingestion.controller.vo.IngestionTaskNodeVO;
import edu.cqupt.devbrain.ingestion.controller.vo.IngestionTaskVO;
import edu.cqupt.devbrain.ingestion.dao.entity.IngestionTaskDO;
import edu.cqupt.devbrain.ingestion.dao.entity.IngestionTaskNodeDO;
import edu.cqupt.devbrain.ingestion.dao.mapper.IngestionTaskMapper;
import edu.cqupt.devbrain.ingestion.dao.mapper.IngestionTaskNodeMapper;
import edu.cqupt.devbrain.ingestion.domain.IngestionStatus;
import edu.cqupt.devbrain.ingestion.domain.SourceType;
import edu.cqupt.devbrain.ingestion.domain.context.DocumentSource;
import edu.cqupt.devbrain.ingestion.domain.context.IngestionContext;
import edu.cqupt.devbrain.ingestion.domain.context.NodeLog;
import edu.cqupt.devbrain.ingestion.domain.pipeline.PipelineDefinition;
import edu.cqupt.devbrain.ingestion.domain.result.IngestionResult;
import edu.cqupt.devbrain.ingestion.engine.IngestionEngine;
import edu.cqupt.devbrain.ingestion.service.IngestionPipelineService;
import edu.cqupt.devbrain.ingestion.service.IngestionTaskService;
import edu.cqupt.devbrain.knowledge.service.validator.FileUploadValidator;
import edu.cqupt.devbrain.knowledge.storage.FileStorageService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 摄入任务执行服务实现，负责创建任务、调用 Pipeline 引擎并持久化节点日志。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class IngestionTaskServiceImpl implements IngestionTaskService {

    /**
     * JSON Map 解析类型。
     */
    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {
    };

    /**
     * JSON List<Map> 解析类型。
     */
    private static final TypeReference<List<Map<String, Object>>> LOG_LIST_TYPE = new TypeReference<>() {
    };

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final IngestionPipelineService pipelineService;
    private final IngestionEngine ingestionEngine;
    private final IngestionTaskMapper taskMapper;
    private final IngestionTaskNodeMapper taskNodeMapper;
    private final FileStorageService fileStorageService;
    private final FileUploadValidator fileUploadValidator;

    /**
     * 创建并同步执行摄入任务。
     */
    @Override
    @Transactional
    public IngestionResult execute(ExecuteTaskRequest request) {
        PipelineDefinition pipeline = pipelineService.getDefinition(request.pipelineId());
        DocumentSource source = buildDocumentSource(request);
        IngestionTaskDO task = createRunningTask(request, source);
        IngestionContext context = buildContext(task, request, source);

        IngestionResult result;
        try {
            result = ingestionEngine.execute(pipeline, context);
        } catch (Exception ex) {
            context.setStatus(IngestionStatus.FAILED);
            result = IngestionResult.builder()
                    .taskId(task.getId())
                    .pipelineId(pipeline.getId())
                    .status(IngestionStatus.FAILED)
                    .chunkCount(context.getChunks() == null ? 0 : context.getChunks().size())
                    .message(ex.getMessage() == null ? ex.getClass().getSimpleName() : ex.getMessage())
                    .build();
        }

        persistNodeLogs(task, context);
        updateTaskAfterExecution(task, context, result);
        return result;
    }

    /**
     * 保存上传文件后以 FILE 来源执行指定 Pipeline。
     */
    @Override
    public IngestionResult upload(String pipelineId, MultipartFile file) {
        fileUploadValidator.validate(file);
        String originalFilename = fileUploadValidator.sanitizeFilename(file.getOriginalFilename());
        // 单元测试中 FileUploadValidator 可能是 Mock；这里保留原始文件名兜底，避免后续上下文丢失展示名。
        if (!StringUtils.hasText(originalFilename)) {
            originalFilename = file.getOriginalFilename();
        }
        if (!StringUtils.hasText(originalFilename)) {
            throw new ClientException("文件名不能为空");
        }
        String extension = fileUploadValidator.extractExtension(originalFilename);
        if (extension == null) {
            extension = "";
        }
        fileUploadValidator.validateFileType(file, extension);

        String objectKey = buildObjectKey(originalFilename, extension);
        try {
            fileStorageService.upload(objectKey, file.getInputStream(), file.getContentType(), file.getSize());
        } catch (IOException ex) {
            throw new ClientException("文件读取失败");
        }

        return execute(new ExecuteTaskRequest(
                pipelineId,
                SourceType.FILE.name(),
                objectKey,
                originalFilename,
                Map.of("uploaded", true)
        ));
    }

    /**
     * 查询任务详情。
     */
    @Override
    public IngestionTaskVO getTask(String taskId) {
        return toTaskVO(requireTask(taskId));
    }

    /**
     * 查询任务节点日志。
     */
    @Override
    public List<IngestionTaskNodeVO> getTaskNodes(String taskId) {
        requireTask(taskId);
        return taskNodeMapper.selectList(Wrappers.lambdaQuery(IngestionTaskNodeDO.class)
                        .eq(IngestionTaskNodeDO::getTaskId, taskId)
                        .orderByAsc(IngestionTaskNodeDO::getNodeOrder))
                .stream()
                .map(this::toTaskNodeVO)
                .toList();
    }

    /**
     * 分页查询任务，支持 pipelineId 和 status 过滤。
     */
    @Override
    public IPage<IngestionTaskVO> page(IngestionTaskPageRequest request) {
        long current = Math.max(1, request.getPageNo());
        long size = Math.min(Math.max(1, request.getPageSize()), 100);
        String pipelineId = clean(request.getPipelineId());
        String status = clean(request.getStatus());
        IPage<IngestionTaskDO> result = taskMapper.selectPage(new Page<>(current, size),
                Wrappers.lambdaQuery(IngestionTaskDO.class)
                        .eq(StringUtils.hasText(pipelineId), IngestionTaskDO::getPipelineId, pipelineId)
                        .eq(StringUtils.hasText(status), IngestionTaskDO::getStatus, status)
                        .orderByDesc(IngestionTaskDO::getUpdateTime));
        return result.convert(this::toTaskVO);
    }

    /**
     * 根据请求构建文档来源。
     */
    private DocumentSource buildDocumentSource(ExecuteTaskRequest request) {
        SourceType sourceType = parseSourceType(request.sourceType());
        return DocumentSource.builder()
                .type(sourceType)
                .location(cleanRequired(request.sourceLocation(), "sourceLocation 不能为空"))
                .fileName(clean(request.fileName()))
                .build();
    }

    /**
     * 创建 RUNNING 状态任务记录。
     */
    private IngestionTaskDO createRunningTask(ExecuteTaskRequest request, DocumentSource source) {
        IngestionTaskDO task = new IngestionTaskDO();
        task.setPipelineId(cleanRequired(request.pipelineId(), "pipelineId 不能为空"));
        task.setSourceType(source.getType().name());
        task.setSourceLocation(source.getLocation());
        task.setStatus(IngestionStatus.RUNNING.name());
        task.setChunkCount(0);
        task.setMetadataJson(toJson(request.metadata() == null ? Map.of() : request.metadata()));
        task.setLogsJson("[]");
        task.setCreatedBy(UserContext.requireUser().userId());
        taskMapper.insert(task);
        return task;
    }

    /**
     * 构建引擎上下文。
     */
    private IngestionContext buildContext(IngestionTaskDO task, ExecuteTaskRequest request, DocumentSource source) {
        Map<String, Object> metadata = request.metadata() == null
                ? new LinkedHashMap<>()
                : new LinkedHashMap<>(request.metadata());
        return IngestionContext.builder()
                .taskId(task.getId())
                .pipelineId(task.getPipelineId())
                .source(source)
                .metadata(metadata)
                .status(IngestionStatus.RUNNING)
                .build();
    }

    /**
     * 持久化每个节点日志。
     */
    private void persistNodeLogs(IngestionTaskDO task, IngestionContext context) {
        List<NodeLog> logs = context.getLogs() == null ? List.of() : context.getLogs();
        for (int i = 0; i < logs.size(); i++) {
            taskNodeMapper.insert(toTaskNodeDO(task, logs.get(i), i));
        }
    }

    /**
     * 更新任务最终状态、chunk 数量、日志和元数据。
     */
    private void updateTaskAfterExecution(IngestionTaskDO task, IngestionContext context, IngestionResult result) {
        // 使用独立更新实体，避免把已插入对象上的 RUNNING 状态改写，便于审计和测试观察创建瞬间状态。
        IngestionTaskDO update = new IngestionTaskDO();
        update.setId(task.getId());
        update.setStatus(result.getStatus() == null ? IngestionStatus.FAILED.name() : result.getStatus().name());
        update.setChunkCount(result.getChunkCount());
        update.setLogsJson(toJsonList(context.getLogs()));
        update.setMetadataJson(toJson(context.getMetadata() == null ? Map.of() : context.getMetadata()));
        taskMapper.updateById(update);
    }

    /**
     * 将 NodeLog 转换为任务节点日志实体。
     */
    private IngestionTaskNodeDO toTaskNodeDO(IngestionTaskDO task, NodeLog log, int order) {
        IngestionTaskNodeDO node = new IngestionTaskNodeDO();
        node.setTaskId(task.getId());
        node.setPipelineId(task.getPipelineId());
        node.setNodeId(log.getNodeId());
        node.setNodeType(log.getNodeType());
        node.setNodeOrder(order);
        node.setStatus(log.isSuccess() ? IngestionStatus.COMPLETED.name() : IngestionStatus.FAILED.name());
        node.setDurationMs(log.getDurationMs());
        node.setOutputJson(toJson(Map.of(
                "success", log.isSuccess(),
                "message", log.getMessage() == null ? "" : log.getMessage()
        )));
        return node;
    }

    /**
     * 查询并校验任务存在。
     */
    private IngestionTaskDO requireTask(String taskId) {
        if (!StringUtils.hasText(taskId)) {
            throw new ClientException("任务 ID 不能为空");
        }
        IngestionTaskDO task = taskMapper.selectById(taskId);
        if (task == null) {
            throw new ClientException("摄入任务不存在");
        }
        return task;
    }

    /**
     * 解析来源类型。
     */
    private SourceType parseSourceType(String sourceType) {
        try {
            return SourceType.valueOf(cleanRequired(sourceType, "sourceType 不能为空").toUpperCase());
        } catch (IllegalArgumentException ex) {
            throw new ClientException("sourceType 只能为 FILE、URL、FEISHU 或 S3");
        }
    }

    /**
     * 构建对象存储 key。
     */
    private String buildObjectKey(String originalFilename, String extension) {
        String suffix = StringUtils.hasText(extension) ? "." + extension : "";
        return "ingestion/" + IdUtil.fastSimpleUUID() + suffix;
    }

    /**
     * 转换任务视图。
     */
    private IngestionTaskVO toTaskVO(IngestionTaskDO task) {
        return new IngestionTaskVO(
                task.getId(),
                task.getPipelineId(),
                task.getSourceType(),
                task.getSourceLocation(),
                task.getStatus(),
                task.getChunkCount(),
                parseLogList(task.getLogsJson()),
                parseMap(task.getMetadataJson()),
                task.getCreatedBy(),
                task.getCreateTime(),
                task.getUpdateTime()
        );
    }

    /**
     * 转换任务节点日志视图。
     */
    private IngestionTaskNodeVO toTaskNodeVO(IngestionTaskNodeDO node) {
        return new IngestionTaskNodeVO(
                node.getId(),
                node.getTaskId(),
                node.getPipelineId(),
                node.getNodeId(),
                node.getNodeType(),
                node.getNodeOrder(),
                node.getStatus(),
                node.getDurationMs(),
                parseMap(node.getOutputJson()),
                node.getCreateTime()
        );
    }

    /**
     * 将对象序列化为 JSON。
     */
    private String toJson(Object value) {
        try {
            return OBJECT_MAPPER.writeValueAsString(value);
        } catch (JsonProcessingException ex) {
            throw new ServiceException("摄入任务 JSON 序列化失败", ex, BaseErrorCode.SERVICE_ERROR);
        }
    }

    /**
     * 将节点日志列表转为 JSON。
     */
    private String toJsonList(List<NodeLog> logs) {
        List<Map<String, Object>> values = new ArrayList<>();
        if (logs != null) {
            for (NodeLog log : logs) {
                values.add(Map.of(
                        "nodeId", log.getNodeId() == null ? "" : log.getNodeId(),
                        "nodeType", log.getNodeType() == null ? "" : log.getNodeType(),
                        "success", log.isSuccess(),
                        "message", log.getMessage() == null ? "" : log.getMessage(),
                        "durationMs", log.getDurationMs()
                ));
            }
        }
        return toJson(values);
    }

    /**
     * 解析 JSON 对象。
     */
    private Map<String, Object> parseMap(String json) {
        if (!StringUtils.hasText(json)) {
            return Map.of();
        }
        try {
            return OBJECT_MAPPER.readValue(json, MAP_TYPE);
        } catch (JsonProcessingException ex) {
            return Map.of();
        }
    }

    /**
     * 解析节点日志 JSON 列表。
     */
    private List<Map<String, Object>> parseLogList(String json) {
        if (!StringUtils.hasText(json)) {
            return List.of();
        }
        try {
            return OBJECT_MAPPER.readValue(json, LOG_LIST_TYPE);
        } catch (JsonProcessingException ex) {
            return List.of();
        }
    }

    /**
     * 清理并校验必填字符串。
     */
    private String cleanRequired(String value, String message) {
        String cleaned = clean(value);
        if (!StringUtils.hasText(cleaned)) {
            throw new ClientException(message);
        }
        return cleaned;
    }

    /**
     * 去除字符串前后空白，保留 null 语义。
     */
    private String clean(String value) {
        return value == null ? null : value.trim();
    }
}
