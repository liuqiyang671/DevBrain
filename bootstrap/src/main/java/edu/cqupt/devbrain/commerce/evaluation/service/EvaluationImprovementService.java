package edu.cqupt.devbrain.commerce.evaluation.service;

import java.util.List;
import java.util.Map;

/**
 * 评测改进建议服务接口。
 * 根据评测汇总指标自动生成优化建议。
 */
public interface EvaluationImprovementService {

    /**
     * 根据评测汇总指标生成改进建议列表。
     *
     * @param summaryMetrics 评测汇总指标
     * @return 改进建议列表
     */
    List<String> suggest(Map<String, Object> summaryMetrics);
}
