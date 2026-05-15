package edu.cqupt.devbrain.commerce.multimodal.dto;

import java.util.List;
import java.util.Map;

/**
 * 图片理解结果。
 * 包含AI对图片的完整分析结果：OCR文本、视觉摘要、商品识别和风险标记。
 */
public record ImageUnderstandingResult(
        String imageId,
        String ocrText,
        String visualSummary,
        List<String> detectedProductNames,
        Map<String, String> detectedAttributes,
        List<String> riskFlags,
        Double confidence
) {
}
