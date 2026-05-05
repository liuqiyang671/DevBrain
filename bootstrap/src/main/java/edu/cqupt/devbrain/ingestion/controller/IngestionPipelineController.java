package edu.cqupt.devbrain.ingestion.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import edu.cqupt.devbrain.framework.convention.Result;
import edu.cqupt.devbrain.framework.web.Results;
import edu.cqupt.devbrain.ingestion.controller.request.CreatePipelineRequest;
import edu.cqupt.devbrain.ingestion.controller.request.IngestionPipelinePageRequest;
import edu.cqupt.devbrain.ingestion.controller.request.UpdatePipelineRequest;
import edu.cqupt.devbrain.ingestion.controller.vo.IngestionPipelineVO;
import edu.cqupt.devbrain.ingestion.domain.pipeline.PipelineDefinition;
import edu.cqupt.devbrain.ingestion.service.IngestionPipelineService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * 摄入流水线定义控制器，提供前端配置节点链所需的 CRUD 接口。
 */
@RequiredArgsConstructor
@RestController
public class IngestionPipelineController {

    private final IngestionPipelineService ingestionPipelineService;

    /**
     * 创建流水线定义。
     */
    @PostMapping("/ingestion/pipelines")
    public Result<IngestionPipelineVO> create(@RequestBody @Valid CreatePipelineRequest request) {
        return Results.success(ingestionPipelineService.create(request));
    }

    /**
     * 更新流水线定义。
     */
    @PutMapping("/ingestion/pipelines/{id}")
    public Result<IngestionPipelineVO> update(@PathVariable String id,
                                              @RequestBody @Valid UpdatePipelineRequest request) {
        return Results.success(ingestionPipelineService.update(id, request));
    }

    /**
     * 获取可执行流水线定义。
     */
    @GetMapping("/ingestion/pipelines/{id}")
    public Result<PipelineDefinition> detail(@PathVariable String id) {
        return Results.success(ingestionPipelineService.getDefinition(id));
    }

    /**
     * 分页查询流水线定义。
     */
    @GetMapping("/ingestion/pipelines")
    public Result<IPage<IngestionPipelineVO>> page(@Valid IngestionPipelinePageRequest request) {
        return Results.success(ingestionPipelineService.page(request));
    }

    /**
     * 删除流水线定义。
     */
    @DeleteMapping("/ingestion/pipelines/{id}")
    public Result<Void> delete(@PathVariable String id) {
        ingestionPipelineService.delete(id);
        return Results.success();
    }
}
