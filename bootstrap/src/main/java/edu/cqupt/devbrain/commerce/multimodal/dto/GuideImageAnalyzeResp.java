package edu.cqupt.devbrain.commerce.multimodal.dto;

import java.time.Instant;

/**
 * 图片分析响应。
 * 包含AI视觉理解的详细结果。
 */
public record GuideImageAnalyzeResp(
        String imageId,
        ImageUnderstandingResult result,
        Instant analyzedAt
) {
}
