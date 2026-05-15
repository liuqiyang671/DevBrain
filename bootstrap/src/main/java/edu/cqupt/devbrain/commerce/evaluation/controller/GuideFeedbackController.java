package edu.cqupt.devbrain.commerce.evaluation.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import edu.cqupt.devbrain.commerce.evaluation.dto.req.GuideFeedbackCreateReq;
import edu.cqupt.devbrain.commerce.evaluation.dto.req.GuideFeedbackReviewReq;
import edu.cqupt.devbrain.commerce.evaluation.dto.resp.GuideFeedbackResp;
import edu.cqupt.devbrain.commerce.evaluation.service.GuideFeedbackService;
import edu.cqupt.devbrain.framework.convention.Result;
import edu.cqupt.devbrain.framework.web.Results;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 导购反馈控制器。
 * 提供用户反馈的提交、查询和审核处理的REST API接口。
 */
@RestController
@RequiredArgsConstructor
public class GuideFeedbackController {

    private final GuideFeedbackService feedbackService;

    /**
     * 创建导购反馈。
     */
    @PostMapping("/commerce/guide/feedback")
    public Result<GuideFeedbackResp> create(@RequestBody @Valid GuideFeedbackCreateReq request) {
        return Results.success(feedbackService.create(request));
    }

    /**
     * 分页查询导购反馈列表。
     */
    @GetMapping("/commerce/guide/feedback")
    public Result<IPage<GuideFeedbackResp>> page(@RequestParam(defaultValue = "1") long pageNo,
                                                 @RequestParam(defaultValue = "10") long pageSize,
                                                 @RequestParam(required = false) String reviewStatus) {
        return Results.success(feedbackService.page(pageNo, pageSize, reviewStatus));
    }

    /**
     * 审核处理导购反馈。
     */
    @PutMapping("/commerce/guide/feedback/{feedbackId}/review")
    public Result<GuideFeedbackResp> review(@PathVariable String feedbackId,
                                            @RequestBody @Valid GuideFeedbackReviewReq request) {
        return Results.success(feedbackService.review(feedbackId, request));
    }
}
