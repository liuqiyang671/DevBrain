package edu.cqupt.devbrain.knowledge.controller;

import edu.cqupt.devbrain.framework.convention.Result;
import edu.cqupt.devbrain.framework.web.Results;
import edu.cqupt.devbrain.knowledge.controller.request.KnowledgeChunkBatchRequest;
import edu.cqupt.devbrain.knowledge.controller.request.KnowledgeChunkCreateRequest;
import edu.cqupt.devbrain.knowledge.controller.request.KnowledgeChunkPageRequest;
import edu.cqupt.devbrain.knowledge.controller.request.KnowledgeChunkUpdateRequest;
import edu.cqupt.devbrain.knowledge.controller.vo.KnowledgeChunkVO;
import edu.cqupt.devbrain.knowledge.controller.vo.PageResult;
import edu.cqupt.devbrain.knowledge.service.KnowledgeChunkService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 知识库分块控制器 -- 提供分块 CRUD 和启停管理 REST 接口。
 */
@RestController
@RequiredArgsConstructor
public class KnowledgeChunkController {

    private final KnowledgeChunkService chunkService;

    /**
     * 分页查询某文档的分块列表。
     */
    @GetMapping("/knowledge-base/docs/{docId}/chunks")
    public Result<PageResult<KnowledgeChunkVO>> page(@PathVariable String docId,
                                                     @Valid KnowledgeChunkPageRequest request) {
        return Results.success(chunkService.pageQuery(docId, request));
    }

    /**
     * 手动新增单条分块。
     */
    @PostMapping("/knowledge-base/docs/{docId}/chunks")
    public Result<KnowledgeChunkVO> create(@PathVariable String docId,
                                           @RequestBody @Valid KnowledgeChunkCreateRequest request) {
        return Results.success(chunkService.create(docId, request));
    }

    /**
     * 更新分块内容。
     */
    @PutMapping("/knowledge-base/docs/{docId}/chunks/{chunkId}")
    public Result<KnowledgeChunkVO> update(@PathVariable String docId,
                                           @PathVariable String chunkId,
                                           @RequestBody @Valid KnowledgeChunkUpdateRequest request) {
        return Results.success(chunkService.update(docId, chunkId, request));
    }

    /**
     * 删除分块。
     */
    @DeleteMapping("/knowledge-base/docs/{docId}/chunks/{chunkId}")
    public Result<Void> delete(@PathVariable String docId, @PathVariable String chunkId) {
        chunkService.delete(docId, chunkId);
        return Results.success();
    }

    /**
     * 启用或禁用单条分块。
     */
    @PatchMapping("/knowledge-base/docs/{docId}/chunks/{chunkId}/enable")
    public Result<Void> enable(@PathVariable String docId,
                               @PathVariable String chunkId,
                               @RequestParam boolean enabled) {
        chunkService.enableChunk(chunkId, enabled);
        return Results.success();
    }

    /**
     * 批量启用或禁用分块。
     */
    @PatchMapping("/knowledge-base/docs/{docId}/chunks/batch-enable")
    public Result<Void> batchEnable(@PathVariable String docId,
                                    @RequestBody @Valid KnowledgeChunkBatchRequest request) {
        boolean enabled = request.enabled() == 1;
        chunkService.batchToggleEnabled(request.chunkIds(), enabled);
        return Results.success();
    }
}
