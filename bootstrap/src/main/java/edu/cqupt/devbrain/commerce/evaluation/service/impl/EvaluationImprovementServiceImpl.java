package edu.cqupt.devbrain.commerce.evaluation.service.impl;

import edu.cqupt.devbrain.commerce.evaluation.service.EvaluationImprovementService;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 评测改进建议服务实现类。
 * 根据各指标阈值自动生成针对性的优化建议。
 */
@Service
public class EvaluationImprovementServiceImpl implements EvaluationImprovementService {

    @Override
    public List<String> suggest(Map<String, Object> summaryMetrics) {
        List<String> hints = new ArrayList<>();
        if (score(summaryMetrics, "intentAccuracy") < 0.8D) {
            hints.add("意图准确率偏低：补充导购意图 Prompt 示例和品类/场景同义词。");
        }
        if (score(summaryMetrics, "retrievalHit") < 0.8D) {
            hints.add("知识检索命中率偏低：检查商品文档绑定、分块元数据和检索 query 扩展。");
        }
        if (score(summaryMetrics, "recommendationHit") < 0.8D) {
            hints.add("推荐命中率偏低：复核商品属性完整性，并调整排序权重。");
        }
        if (score(summaryMetrics, "businessDataUsage") < 1D) {
            hints.add("业务数据使用度不足：推荐必须结合价格、库存、优惠券等结构化商品信号。");
        }
        if (score(summaryMetrics, "recommendationExplainability") < 1D) {
            hints.add("推荐可解释性不足：回答中需要明确说明推荐理由，并保留结构化 reasons。");
        }
        if (score(summaryMetrics, "forbiddenClaimSafe") < 1D) {
            hints.add("出现禁止声明：收紧回答 Prompt，要求未被证据支持的信息必须提示用户确认。");
        }
        if (hints.isEmpty()) {
            hints.add("当前核心指标稳定，可继续补充更多长尾场景用例。");
        }
        return hints;
    }

    private double score(Map<String, Object> metrics, String key) {
        Object value = metrics == null ? null : metrics.get(key);
        return value instanceof Number number ? number.doubleValue() : 1D;
    }
}
