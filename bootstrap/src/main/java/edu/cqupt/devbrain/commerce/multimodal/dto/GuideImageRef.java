package edu.cqupt.devbrain.commerce.multimodal.dto;

import java.util.List;
import java.util.Map;

/**
 * 导购图片引用。
 * 包含图片的分析摘要信息，用于注入导购对话上下文。
 */
public record GuideImageRef(
        String imageId,
        String fileName,
        String previewUrl,
        String ocrText,
        String visualSummary,
        List<String> detectedProductNames,
        Map<String, String> detectedAttributes,
        List<String> riskFlags
) {
}
