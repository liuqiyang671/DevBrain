package edu.cqupt.devbrain.ingestion.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import edu.cqupt.devbrain.framework.convention.Result;
import edu.cqupt.devbrain.framework.web.Results;
import edu.cqupt.devbrain.ingestion.controller.request.ExecuteTaskRequest;
import edu.cqupt.devbrain.ingestion.controller.request.IngestionTaskPageRequest;
import edu.cqupt.devbrain.ingestion.controller.vo.IngestionTaskNodeVO;
import edu.cqupt.devbrain.ingestion.controller.vo.IngestionTaskVO;
import edu.cqupt.devbrain.ingestion.domain.result.IngestionResult;
import edu.cqupt.devbrain.ingestion.service.IngestionTaskService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

/**
 * 摄入任务控制器，提供 Pipeline 任务执行、上传执行和日志查询接口。
 */
@RestController
@RequiredArgsConstructor
public class IngestionTaskController {

    private final IngestionTaskService ingestionTaskService;

    /**
     * 创建并执行摄入任务。
     */
    @PostMapping("/ingestion/tasks")
    public Result<IngestionResult> execute(@RequestBody @Valid ExecuteTaskRequest request) {
        return Results.success(ingestionTaskService.execute(request));
    }

    /**
     * 上传文件并执行摄入任务。
     */
    @PostMapping(value = "/ingestion/tasks/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Result<IngestionResult> upload(@RequestParam String pipelineId,
                                          @RequestPart("file") MultipartFile file) {
        return Results.success(ingestionTaskService.upload(pipelineId, file));
    }

    /**
     * 查询任务详情。
     */
    @GetMapping("/ingestion/tasks/{id}")
    public Result<IngestionTaskVO> getTask(@PathVariable String id) {
        return Results.success(ingestionTaskService.getTask(id));
    }

    /**
     * 查询任务节点日志。
     */
    @GetMapping("/ingestion/tasks/{id}/nodes")
    public Result<List<IngestionTaskNodeVO>> getTaskNodes(@PathVariable String id) {
        return Results.success(ingestionTaskService.getTaskNodes(id));
    }

    /**
     * 分页查询任务列表。
     */
    @GetMapping("/ingestion/tasks")
    public Result<IPage<IngestionTaskVO>> page(@Valid IngestionTaskPageRequest request) {
        return Results.success(ingestionTaskService.page(request));
    }
}
