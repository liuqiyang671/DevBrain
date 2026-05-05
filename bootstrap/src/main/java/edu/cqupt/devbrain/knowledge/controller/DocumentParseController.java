package edu.cqupt.devbrain.knowledge.controller;

import edu.cqupt.devbrain.core.chunk.VectorChunk;
import edu.cqupt.devbrain.framework.convention.Result;
import edu.cqupt.devbrain.framework.context.UserContext;
import edu.cqupt.devbrain.framework.exception.ServiceException;
import edu.cqupt.devbrain.framework.web.Results;
import edu.cqupt.devbrain.knowledge.controller.request.DocumentParseRequest;
import edu.cqupt.devbrain.knowledge.controller.vo.ChunkVO;
import edu.cqupt.devbrain.knowledge.controller.vo.PageResult;
import edu.cqupt.devbrain.knowledge.controller.vo.ParseStatusVO;
import edu.cqupt.devbrain.knowledge.dao.entity.KnowledgeDocumentChunkLogDO;
import edu.cqupt.devbrain.knowledge.service.DocumentParseService;
import edu.cqupt.devbrain.knowledge.service.KnowledgeDocumentService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 文档解析控制器，提供触发解析、查询状态、查询分块和重试解析接口。
 */
@RestController
@RequestMapping("/documents")
@RequiredArgsConstructor
@Slf4j
public class DocumentParseController {

    /**
     * 分块列表展示内容最大字符数。
     */
    private static final int CHUNK_PREVIEW_LENGTH = 200;

    /**
     * 单页最大记录数，避免一次返回过多 chunk 内容。
     */
    private static final int MAX_PAGE_SIZE = 100;

    private final DocumentParseService documentParseService;
    private final KnowledgeDocumentService knowledgeDocumentService;

    /**
     * 触发指定文档解析。
     *
     * @param docId 文档 ID
     * @return 空响应
     */
    @PostMapping("/parse/{docId}")
    public Result<Void> parse(@PathVariable String docId,
                              @RequestBody(required = false) @Valid DocumentParseRequest request) {
        try {
            if (request != null) {
                knowledgeDocumentService.updateChunkConfig(docId, request.chunkStrategy(), request.chunkConfig());
            }
            UserContext.requireUser();
            return triggerChunk(docId);
        } catch (ServiceException e) {
            return Results.failure(e.errorCode, e.getMessage());
        }
    }

    /**
     * 查询指定文档最近一次解析状态。
     *
     * @param docId 文档 ID
     * @return 解析状态视图
     */
    @GetMapping("/{docId}/parse-status")
    public Result<ParseStatusVO> parseStatus(@PathVariable String docId) {
        KnowledgeDocumentChunkLogDO latestLog = documentParseService.getLatestLog(docId);
        return Results.success(toParseStatusVO(latestLog));
    }

    /**
     * 分页查询指定文档的分块列表。
     *
     * @param docId 文档 ID
     * @param page 当前页码，从 1 开始
     * @param size 每页记录数
     * @return 分块分页结果
     */
    @GetMapping("/{docId}/chunks")
    public Result<PageResult<ChunkVO>> chunks(@PathVariable String docId,
                                              @RequestParam(defaultValue = "1") int page,
                                              @RequestParam(defaultValue = "20") int size) {
        int current = Math.max(1, page);
        int pageSize = Math.min(Math.max(1, size), MAX_PAGE_SIZE);
        List<VectorChunk> chunks = documentParseService.getChunks(docId);
        int fromIndex = Math.min((current - 1) * pageSize, chunks.size());
        int toIndex = Math.min(fromIndex + pageSize, chunks.size());
        List<ChunkVO> records = chunks.subList(fromIndex, toIndex).stream()
                .map(this::toChunkVO)
                .toList();
        return Results.success(PageResult.of(records, chunks.size(), current, pageSize));
    }

    /**
     * 重试指定文档的失败解析任务。
     *
     * @param docId 文档 ID
     * @return 空响应
     */
    @PostMapping("/parse/{docId}/retry")
    public Result<Void> retry(@PathVariable String docId) {
        try {
            UserContext.requireUser();
            return triggerChunk(docId);
        } catch (ServiceException e) {
            return Results.failure(e.errorCode, e.getMessage());
        }
    }

    private Result<Void> triggerChunk(String docId) {
        boolean triggered = knowledgeDocumentService.startChunk(docId);
        if (triggered) {
            return Results.success();
        }
        return Results.failure("A000409", "文档正在解析中或当前状态不允许解析");
    }

    /**
     * 将解析日志实体转换为状态视图对象。
     *
     * @param logRecord 解析日志实体
     * @return 状态视图对象
     */
    private ParseStatusVO toParseStatusVO(KnowledgeDocumentChunkLogDO logRecord) {
        if (logRecord == null) {
            return new ParseStatusVO(null, 0, null, null, null, null, null, null);
        }
        return new ParseStatusVO(
                logRecord.getStatus(),
                logRecord.getChunkCount(),
                logRecord.getExtractDuration(),
                logRecord.getChunkDuration(),
                logRecord.getTotalDuration(),
                logRecord.getErrorMessage(),
                logRecord.getStartTime() != null ? java.util.Date.from(logRecord.getStartTime().atZone(java.time.ZoneId.systemDefault()).toInstant()) : null,
                logRecord.getEndTime() != null ? java.util.Date.from(logRecord.getEndTime().atZone(java.time.ZoneId.systemDefault()).toInstant()) : null
        );
    }

    /**
     * 将核心 VectorChunk 转换为列表展示 VO，并截断内容预览。
     *
     * @param chunk 文档分块
     * @return 分块视图对象
     */
    private ChunkVO toChunkVO(VectorChunk chunk) {
        String content = chunk.getContent() == null ? "" : chunk.getContent();
        String preview = content.length() > CHUNK_PREVIEW_LENGTH
                ? content.substring(0, CHUNK_PREVIEW_LENGTH)
                : content;
        return new ChunkVO(chunk.getChunkId(), chunk.getIndex(), preview, content.length());
    }
}
