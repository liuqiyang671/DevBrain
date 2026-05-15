package edu.cqupt.devbrain.commerce.evaluation.service.impl;

import cn.hutool.core.util.IdUtil;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import edu.cqupt.devbrain.commerce.evaluation.dao.entity.GuideFeedbackDO;
import edu.cqupt.devbrain.commerce.evaluation.dao.mapper.GuideFeedbackMapper;
import edu.cqupt.devbrain.commerce.evaluation.dto.req.GuideFeedbackCreateReq;
import edu.cqupt.devbrain.commerce.evaluation.dto.req.GuideFeedbackReviewReq;
import edu.cqupt.devbrain.commerce.evaluation.dto.resp.GuideFeedbackResp;
import edu.cqupt.devbrain.commerce.evaluation.service.GuideFeedbackService;
import edu.cqupt.devbrain.framework.context.UserContext;
import edu.cqupt.devbrain.framework.exception.ClientException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.Set;

/**
 * 导购反馈服务实现类。
 * 提供反馈的创建、分页查询和审核处理功能。
 */
@Service
@RequiredArgsConstructor
public class GuideFeedbackServiceImpl implements GuideFeedbackService {

    private static final Set<String> REVIEW_STATUS = Set.of("pending", "reviewing", "resolved", "ignored");
    private static final Set<String> TARGET_TYPES = Set.of("answer", "product", "reason", "evidence", "tool_step", "session");
    private static final Set<String> FEEDBACK_TYPES = Set.of(
            "like", "dislike", "wrong", "purchased", "not_interested", "helpful", "not_helpful",
            "wrong_product", "wrong_fact", "missing_context", "bad_citation", "unsafe_or_inappropriate",
            "irrelevant_reason", "weak_evidence", "missing_product", "bad_ranking", "unhelpful_clarification"
    );

    private final GuideFeedbackMapper feedbackMapper;

    @Override
    @Transactional
    public GuideFeedbackResp create(GuideFeedbackCreateReq request) {
        String userId = UserContext.requireUser().userId();
        GuideFeedbackDO feedback = new GuideFeedbackDO();
        feedback.setId(IdUtil.getSnowflakeNextIdStr());
        feedback.setConversationId(required(request.conversationId(), "会话 ID 不能为空"));
        feedback.setMessageId(clean(request.messageId()));
        feedback.setProductId(clean(request.productId()));
        feedback.setFeedbackType(validFeedbackType(request.feedbackType()));
        feedback.setComment(clean(request.comment()));
        feedback.setTargetType(validTargetType(request.targetType(), request.productId()));
        feedback.setTargetId(clean(request.targetId()));
        feedback.setAgentRunId(clean(request.agentRunId()));
        feedback.setStepId(clean(request.stepId()));
        feedback.setEvidenceId(clean(request.evidenceId()));
        feedback.setReasonIndex(request.reasonIndex());
        feedback.setReviewStatus("pending");
        feedback.setCreatedBy(userId);
        feedbackMapper.insert(feedback);
        return toResp(feedback);
    }

    @Override
    public IPage<GuideFeedbackResp> page(long pageNo, long pageSize, String reviewStatus) {
        IPage<GuideFeedbackDO> page = feedbackMapper.selectPage(new Page<>(Math.max(1, pageNo), Math.min(Math.max(1, pageSize), 100)),
                Wrappers.lambdaQuery(GuideFeedbackDO.class)
                        .eq(StringUtils.hasText(reviewStatus), GuideFeedbackDO::getReviewStatus, reviewStatus)
                        .eq(GuideFeedbackDO::getDeleted, 0)
                        .orderByDesc(GuideFeedbackDO::getCreateTime));
        return page.convert(this::toResp);
    }

    @Override
    @Transactional
    public GuideFeedbackResp review(String feedbackId, GuideFeedbackReviewReq request) {
        GuideFeedbackDO feedback = feedbackMapper.selectById(feedbackId);
        if (feedback == null || Integer.valueOf(1).equals(feedback.getDeleted())) {
            throw new ClientException("反馈不存在或已删除");
        }
        if (!REVIEW_STATUS.contains(request.reviewStatus())) {
            throw new ClientException("处理状态不合法");
        }
        feedback.setReviewStatus(request.reviewStatus());
        feedback.setReviewResult(clean(request.reviewResult()));
        if ("resolved".equals(request.reviewStatus())) {
            feedback.setImprovementSuggestion(improvementSuggestion(feedback));
        }
        feedback.setReviewedBy(UserContext.requireUser().userId());
        feedbackMapper.updateById(feedback);
        return toResp(feedback);
    }

    private GuideFeedbackResp toResp(GuideFeedbackDO feedback) {
        return new GuideFeedbackResp(feedback.getId(), feedback.getConversationId(), feedback.getMessageId(),
                feedback.getProductId(), feedback.getFeedbackType(), feedback.getComment(), feedback.getTargetType(),
                feedback.getTargetId(), feedback.getAgentRunId(), feedback.getStepId(), feedback.getEvidenceId(),
                feedback.getReasonIndex(), feedback.getReviewStatus(), feedback.getReviewResult(),
                feedback.getImprovementSuggestion(), feedback.getCreateTime());
    }

    private String required(String value, String message) {
        String cleaned = clean(value);
        if (!StringUtils.hasText(cleaned)) {
            throw new ClientException(message);
        }
        return cleaned;
    }

    private String clean(String value) {
        return value == null ? null : value.trim();
    }

    private String validFeedbackType(String value) {
        String cleaned = required(value, "反馈类型不能为空");
        if (!FEEDBACK_TYPES.contains(cleaned)) {
            throw new ClientException("反馈类型不合法");
        }
        return cleaned;
    }

    private String validTargetType(String targetType, String productId) {
        String cleaned = clean(targetType);
        if (!StringUtils.hasText(cleaned)) {
            return StringUtils.hasText(productId) ? "product" : "answer";
        }
        if (!TARGET_TYPES.contains(cleaned)) {
            throw new ClientException("反馈目标类型不合法");
        }
        return cleaned;
    }

    private String improvementSuggestion(GuideFeedbackDO feedback) {
        return switch (String.valueOf(feedback.getFeedbackType())) {
            case "wrong_product" -> "新增相似购买意图评测用例，并复核候选召回和商品排序权重。";
            case "bad_citation", "weak_evidence" -> "修正文档绑定、证据检索 query 或补充商品证据分块。";
            case "missing_context", "missing_product" -> "补充商品属性、库存/价格/优惠数据或新增商品文档。";
            case "unsafe_or_inappropriate" -> "更新回答安全 Prompt，禁止无证据承诺和不合适表达。";
            case "unhelpful_clarification" -> "更新澄清策略，优先追问品类、预算、使用场景和品牌偏好。";
            case "bad_ranking", "irrelevant_reason" -> "调整排序权重与推荐理由模板，并补充对应失败样本到评测集。";
            default -> "把该反馈转为评测样本，复核 Agent 回放、证据和最终回答。";
        };
    }
}
