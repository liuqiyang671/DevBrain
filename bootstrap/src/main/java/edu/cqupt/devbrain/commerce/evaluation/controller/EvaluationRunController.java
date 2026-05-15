package edu.cqupt.devbrain.commerce.evaluation.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import edu.cqupt.devbrain.commerce.evaluation.dto.req.EvaluationRunReq;
import edu.cqupt.devbrain.commerce.evaluation.dto.resp.EvaluationReportResp;
import edu.cqupt.devbrain.commerce.evaluation.dto.resp.EvaluationRunResp;
import edu.cqupt.devbrain.commerce.evaluation.service.EvaluationRunService;
import edu.cqupt.devbrain.framework.convention.Result;
import edu.cqupt.devbrain.framework.web.Results;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 评测运行控制器。
 * 提供评测运行的发起、查询和报告查看的REST API接口。
 */
@RestController
@RequiredArgsConstructor
public class EvaluationRunController {

    private final EvaluationRunService runService;

    /**
     * 发起一次评测运行。
     */
    @PostMapping("/commerce/evaluations/runs")
    public Result<EvaluationRunResp> run(@RequestBody @Valid EvaluationRunReq request) {
        return Results.success(runService.run(request));
    }

    /**
     * 分页查询评测运行记录。
     */
    @GetMapping("/commerce/evaluations/runs")
    public Result<IPage<EvaluationRunResp>> page(@RequestParam(defaultValue = "1") long pageNo,
                                                 @RequestParam(defaultValue = "10") long pageSize,
                                                 @RequestParam(required = false) String datasetId) {
        return Results.success(runService.page(pageNo, pageSize, datasetId));
    }

    /**
     * 获取评测运行的详细报告。
     */
    @GetMapping("/commerce/evaluations/runs/{runId}/report")
    public Result<EvaluationReportResp> report(@PathVariable String runId) {
        return Results.success(runService.report(runId));
    }

    /**
     * 取消正在执行的评测运行。
     */
    @PostMapping("/commerce/evaluations/runs/{runId}/cancel")
    public Result<EvaluationRunResp> cancel(@PathVariable String runId) {
        return Results.success(runService.cancel(runId));
    }
}
