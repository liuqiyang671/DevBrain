package edu.cqupt.devbrain.commerce.evaluation.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import edu.cqupt.devbrain.commerce.evaluation.dto.req.EvaluationCaseReq;
import edu.cqupt.devbrain.commerce.evaluation.dto.req.EvaluationDatasetReq;
import edu.cqupt.devbrain.commerce.evaluation.dto.resp.EvaluationCaseResp;
import edu.cqupt.devbrain.commerce.evaluation.dto.resp.EvaluationDatasetResp;
import edu.cqupt.devbrain.commerce.evaluation.service.EvaluationCaseService;
import edu.cqupt.devbrain.commerce.evaluation.service.EvaluationDatasetService;
import edu.cqupt.devbrain.framework.convention.Result;
import edu.cqupt.devbrain.framework.web.Results;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 评测数据集与评测用例控制器。
 * 提供评测数据集和评测用例的REST API接口。
 */
@RestController
@RequiredArgsConstructor
public class EvaluationDatasetController {

    private final EvaluationDatasetService datasetService;
    private final EvaluationCaseService caseService;

    /**
     * 创建评测数据集。
     */
    @PostMapping("/commerce/evaluations/datasets")
    public Result<EvaluationDatasetResp> create(@RequestBody @Valid EvaluationDatasetReq request) {
        return Results.success(datasetService.create(request));
    }

    /**
     * 分页查询评测数据集列表。
     */
    @GetMapping("/commerce/evaluations/datasets")
    public Result<IPage<EvaluationDatasetResp>> page(@RequestParam(defaultValue = "1") long pageNo,
                                                     @RequestParam(defaultValue = "10") long pageSize,
                                                     @RequestParam(required = false) String keyword) {
        return Results.success(datasetService.page(pageNo, pageSize, keyword));
    }

    /**
     * 查询单个评测数据集详情。
     */
    @GetMapping("/commerce/evaluations/datasets/{datasetId}")
    public Result<EvaluationDatasetResp> get(@PathVariable String datasetId) {
        return Results.success(datasetService.get(datasetId));
    }

    /**
     * 更新评测数据集。
     */
    @PutMapping("/commerce/evaluations/datasets/{datasetId}")
    public Result<EvaluationDatasetResp> update(@PathVariable String datasetId,
                                                @RequestBody @Valid EvaluationDatasetReq request) {
        return Results.success(datasetService.update(datasetId, request));
    }

    /**
     * 删除评测数据集。
     */
    @DeleteMapping("/commerce/evaluations/datasets/{datasetId}")
    public Result<Void> delete(@PathVariable String datasetId) {
        datasetService.delete(datasetId);
        return Results.success();
    }

    /**
     * 在指定数据集下创建评测用例。
     */
    @PostMapping("/commerce/evaluations/datasets/{datasetId}/cases")
    public Result<EvaluationCaseResp> createCase(@PathVariable String datasetId,
                                                 @RequestBody @Valid EvaluationCaseReq request) {
        return Results.success(caseService.create(datasetId, request));
    }

    /**
     * 分页查询指定数据集下的评测用例列表。
     */
    @GetMapping("/commerce/evaluations/datasets/{datasetId}/cases")
    public Result<IPage<EvaluationCaseResp>> cases(@PathVariable String datasetId,
                                                   @RequestParam(defaultValue = "1") long pageNo,
                                                   @RequestParam(defaultValue = "10") long pageSize) {
        return Results.success(caseService.page(datasetId, pageNo, pageSize));
    }

    /**
     * 更新评测用例。
     */
    @PutMapping("/commerce/evaluations/cases/{caseId}")
    public Result<EvaluationCaseResp> updateCase(@PathVariable String caseId,
                                                 @RequestBody @Valid EvaluationCaseReq request) {
        return Results.success(caseService.update(caseId, request));
    }

    /**
     * 删除评测用例。
     */
    @DeleteMapping("/commerce/evaluations/cases/{caseId}")
    public Result<Void> deleteCase(@PathVariable String caseId) {
        caseService.delete(caseId);
        return Results.success();
    }
}
