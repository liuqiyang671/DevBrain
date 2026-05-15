package edu.cqupt.devbrain.commerce.evaluation.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import edu.cqupt.devbrain.commerce.evaluation.dto.req.GuideFeedbackCreateReq;
import edu.cqupt.devbrain.commerce.evaluation.dto.req.GuideFeedbackReviewReq;
import edu.cqupt.devbrain.commerce.evaluation.dto.resp.GuideFeedbackResp;

/**
 * 导购反馈服务接口。
 * 提供用户反馈的提交、查询和审核处理能力。
 */
public interface GuideFeedbackService {

    /**
     * 创建导购反馈。
     *
     * @param request 反馈创建请求
     * @return 创建后的反馈信息
     */
    GuideFeedbackResp create(GuideFeedbackCreateReq request);

    /**
     * 分页查询导购反馈列表。
     *
     * @param pageNo       页码
     * @param pageSize     每页条数
     * @param reviewStatus 按审核状态筛选（可选）
     * @return 分页结果
     */
    IPage<GuideFeedbackResp> page(long pageNo, long pageSize, String reviewStatus);

    /**
     * 审核处理导购反馈。
     *
     * @param feedbackId 反馈ID
     * @param request    审核请求
     * @return 更新后的反馈信息
     */
    GuideFeedbackResp review(String feedbackId, GuideFeedbackReviewReq request);
}
