package edu.cqupt.devbrain.commerce.evaluation.dto.req;

import jakarta.validation.constraints.NotBlank;

import java.util.List;
import java.util.Map;

/**
 * 评测用例创建/更新请求DTO。
 * 包含用例的场景描述、测试输入及各项预期输出。
 */
public record EvaluationCaseReq(
        String caseNo,
        String scenario,
        @NotBlank(message = "问题不能为空")
        String question,
        List<String> turns,
        Map<String, Object> context,
        String expectedAnswer,
        String expectedIntent,
        Map<String, Object> expectedSlots,
        List<String> expectedProductIds,
        List<String> expectedChunkIds,
        List<String> mustHitKeywords,
        List<String> forbiddenClaims,
        List<String> tags
) {
}
