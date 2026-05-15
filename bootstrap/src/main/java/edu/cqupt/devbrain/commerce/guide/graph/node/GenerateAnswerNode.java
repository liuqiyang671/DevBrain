package edu.cqupt.devbrain.commerce.guide.graph.node;

import edu.cqupt.devbrain.commerce.guide.domain.GuideEvidence;
import edu.cqupt.devbrain.commerce.guide.domain.GuideRecommendation;
import edu.cqupt.devbrain.commerce.guide.domain.GuideState;
import edu.cqupt.devbrain.commerce.guide.service.GuideAnswerGenerator;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Optional;

/**
 * 回答生成节点。
 * <p>
 * 根据推荐结果生成自然语言回答草稿，是工作流的最后一个节点。
 * 回答生成策略：
 * <ul>
 *   <li><b>有追问</b>：直接使用追问问题作为回答（阻断式追问时跳过推荐）</li>
 *   <li><b>有推荐</b>：生成包含推荐理由、价格、库存、优惠、证据的回答</li>
 *   <li><b>无推荐</b>：生成引导用户补充信息的回答</li>
 * </ul>
 * <p>
 * 回答格式：
 * <ul>
 *   <li>单商品推荐：结论 + 关键信息 + 评分依据 + 推荐理由 + 可追溯证据 + 风险提示</li>
 *   <li>多商品推荐：编号列表 + 每个商品的关键信息 + 一句话推荐理由</li>
 * </ul>
 * <p>
 * 非阻塞追问：推荐完成后追加追问问题（如"你更看重拍照还是游戏？"）。
 *
 * @author liuqiyang
 * @since 2026-05-15
 */
@Component
public class GenerateAnswerNode implements GuideWorkflowNode {

    private final GuideAnswerGenerator answerGenerator;

    public GenerateAnswerNode() {
        this(null);
    }

    @Autowired
    public GenerateAnswerNode(GuideAnswerGenerator answerGenerator) {
        this.answerGenerator = answerGenerator;
    }

    @Override
    public String name() {
        return "generate_answer";
    }

    @Override
    public GuideState execute(GuideState state) {
        if (StringUtils.hasText(state.getClarificationQuestion()) && blocksAnswer(state)) {
            state.setAnswerDraft(state.getClarificationQuestion());
            return state;
        }
        if (state.getRecommendations() == null || state.getRecommendations().isEmpty()) {
            if (state.getImageRefs() != null && !state.getImageRefs().isEmpty()) {
                state.setAnswerDraft("根据你上传的图片，我已把图片摘要作为补充信息，但暂时没有找到完全匹配的商品。请补充预算、品类或核心使用场景，我会继续帮你筛选。");
            } else {
                state.setAnswerDraft(nonRecommendationFallback(state));
            }
            return state;
        }
        if (answerGenerator != null) {
            Optional<String> generated = answerGenerator.generate(state);
            if (generated.isPresent()) {
                state.setAnswerDraft(withNonBlockingClarification(generated.get(), state));
                return state;
            }
        }
        state.setAnswerDraft(withNonBlockingClarification(buildAnswer(state.getRecommendations(), state.getImageRefs()), state));
        return state;
    }

    private boolean blocksAnswer(GuideState state) {
        return state.getClarificationPlan() == null || state.getClarificationPlan().blocksRetrieval();
    }

    private String withNonBlockingClarification(String answer, GuideState state) {
        if (state == null || state.getClarificationPlan() == null || !state.getClarificationPlan().asksNonBlockingQuestion()
                || !StringUtils.hasText(state.getClarificationQuestion())) {
            return answer;
        }
        return answer + "\n\n" + state.getClarificationQuestion();
    }

    private String buildAnswer(List<GuideRecommendation> recommendations, List<String> imageRefs) {
        if (recommendations.size() > 1) {
            return buildChoiceListAnswer(recommendations, imageRefs);
        }
        GuideRecommendation best = recommendations.get(0);
        StringBuilder builder = new StringBuilder();
        if (imageRefs != null && !imageRefs.isEmpty()) {
            builder.append("根据你上传的图片和当前商品库信息，");
        }
        builder.append("结论：优先推荐 ").append(best.getName()).append(roleText(best.getRecommendationRole())).append("。");
        if (best.getScore() != null) {
            builder.append("综合匹配分约 ").append(Math.round(best.getScore())).append(" 分。");
        }
        builder.append("\n\n关键购买信息：");
        builder.append("\n- 价格：").append(priceRange(best));
        builder.append("\n- 库存：").append(stockText(best.getStockStatus()));
        builder.append("\n- 优惠：").append(promotionText(best));
        if (best.getScoreBreakdown() != null && !best.getScoreBreakdown().isEmpty()) {
            builder.append("\n\n评分依据：");
            best.getScoreBreakdown().forEach((key, value) ->
                    builder.append("\n- ").append(key).append("：").append(Math.round(value * 100D)).append("%"));
        }
        builder.append("\n\n推荐理由：");
        for (String reason : best.getReasons()) {
            builder.append("\n- ").append(reason).append(reasonEvidenceSuffix(best));
        }
        if (!best.getEvidences().isEmpty()) {
            builder.append("\n\n可追溯证据：");
            for (GuideEvidence evidence : best.getEvidences()) {
                builder.append("\n- ").append(evidenceId(evidence))
                        .append(" [").append(evidence.getEvidenceType() == null ? "support" : evidence.getEvidenceType()).append("]：")
                        .append(StringUtils.hasText(evidence.getHighlight()) ? evidence.getHighlight() : evidence.getText());
            }
        } else {
            builder.append("\n\n当前商品文档证据不足，以上建议主要基于结构化商品属性和筛选条件。");
        }
        if (best.getRiskFlags() != null && !best.getRiskFlags().isEmpty()) {
            builder.append("\n\n风险提示：");
            best.getRiskFlags().forEach(flag -> builder.append("\n- ").append(flag));
        }
        builder.append("\n\n需要确认：");
        builder.append("\n- 价格、库存和优惠以当前商品库记录为依据，最终请以实时结算页为准。");
        if (best.getEvidences().stream().anyMatch(evidence -> "missing".equals(evidence.getEvidenceType()))) {
            builder.append("\n- 该商品存在文档证据缺口，关键参数建议二次确认。");
        }
        return builder.toString();
    }

    private String buildChoiceListAnswer(List<GuideRecommendation> recommendations, List<String> imageRefs) {
        StringBuilder builder = new StringBuilder();
        if (imageRefs != null && !imageRefs.isEmpty()) {
            builder.append("结合你上传的图片和当前商品库，");
        }
        builder.append("我先给你推荐几个可选项：");
        int limit = Math.min(3, recommendations.size());
        for (int index = 0; index < limit; index++) {
            GuideRecommendation item = recommendations.get(index);
            builder.append("\n").append(index + 1).append(". ")
                    .append(item.getName())
                    .append(roleText(item.getRecommendationRole()))
                    .append("：")
                    .append(priceRange(item))
                    .append("，库存").append(stockText(item.getStockStatus()));
            if (item.getScore() != null) {
                builder.append("，匹配分约 ").append(Math.round(item.getScore()));
            }
            String reason = firstReason(item);
            if (StringUtils.hasText(reason)) {
                builder.append("。").append(reason).append(reasonEvidenceSuffix(item));
            } else {
                builder.append("。");
            }
        }
        if (recommendations.size() > limit) {
            builder.append("\n另外还有 ").append(recommendations.size() - limit).append(" 个备选，我可以继续帮你展开对比。");
        }
        builder.append("\n\n如果你告诉我你的预算、偏好品牌、主要用途（比如拍照、游戏、续航、长辈使用）或是否看重优惠，我可以按这些需求重新给你推荐。");
        builder.append("\n\n需要确认：价格、库存和优惠以当前商品库记录为依据，最终请以实时结算页为准。");
        return builder.toString();
    }

    private String firstReason(GuideRecommendation recommendation) {
        if (recommendation.getReasons() == null || recommendation.getReasons().isEmpty()) {
            return "";
        }
        return recommendation.getReasons().get(0);
    }

    private String roleText(String role) {
        return switch (String.valueOf(role)) {
            case "value_pick" -> "（性价比备选）";
            case "premium_option" -> "（加预算选项）";
            case "safe_choice" -> "（证据更充分）";
            case "alternative" -> "（备选）";
            default -> "";
        };
    }

    private String reasonEvidenceSuffix(GuideRecommendation recommendation) {
        if (recommendation.getEvidences() == null || recommendation.getEvidences().isEmpty()) {
            return "";
        }
        return "（证据 " + evidenceId(recommendation.getEvidences().get(0)) + "）";
    }

    private String evidenceId(GuideEvidence evidence) {
        String docId = StringUtils.hasText(evidence.getDocumentId()) ? evidence.getDocumentId() : "missing";
        String chunkId = StringUtils.hasText(evidence.getChunkId()) ? evidence.getChunkId() : "no-chunk";
        return docId + "#" + chunkId;
    }

    private String nonRecommendationFallback(GuideState state) {
        String userText = state == null ? "" : state.getUserText();
        if (!StringUtils.hasText(userText)) {
            return "可以，我在。你可以直接告诉我想买的品类、预算和使用场景，我会结合当前商品库的价格、库存和优惠帮你筛选。";
        }
        if (!looksLikePurchaseMessage(userText)) {
            return "可以，我先接住你的消息。等你想继续购物时，告诉我购物需求、预算多少、主要使用场景和偏好品牌，我会结合真实商品库的价格、库存和优惠给出推荐。";
        }
        return "我暂时没有找到完全匹配的商品。可以补充或放宽预算、品牌、品类或使用场景，我会继续结合当前商品库的价格、库存和优惠筛选。";
    }

    private boolean looksLikePurchaseMessage(String text) {
        return text.contains("买")
                || text.contains("推荐")
                || text.contains("选")
                || text.contains("预算")
                || text.contains("优惠")
                || text.contains("活动")
                || text.contains("库存")
                || text.contains("价格")
                || text.contains("对比")
                || text.contains("哪个好");
    }

    private String priceRange(GuideRecommendation recommendation) {
        if (recommendation.getPriceMin() == null && recommendation.getPriceMax() == null) {
            return "待确认";
        }
        if (recommendation.getPriceMin() != null && recommendation.getPriceMax() != null
                && recommendation.getPriceMin().compareTo(recommendation.getPriceMax()) != 0) {
            return recommendation.getPriceMin().stripTrailingZeros().toPlainString()
                    + "-"
                    + recommendation.getPriceMax().stripTrailingZeros().toPlainString()
                    + " 元";
        }
        return (recommendation.getPriceMin() == null ? recommendation.getPriceMax() : recommendation.getPriceMin())
                .stripTrailingZeros()
                .toPlainString() + " 元";
    }

    private String stockText(String stockStatus) {
        return switch (String.valueOf(stockStatus)) {
            case "in_stock" -> "有货";
            case "out_of_stock" -> "缺货";
            default -> "待确认";
        };
    }

    private String promotionText(GuideRecommendation recommendation) {
        if (recommendation.getPromotions() == null || recommendation.getPromotions().isEmpty()) {
            return "当前商品库暂无明确优惠/优惠券信息";
        }
        return String.join("；", recommendation.getPromotions());
    }
}
