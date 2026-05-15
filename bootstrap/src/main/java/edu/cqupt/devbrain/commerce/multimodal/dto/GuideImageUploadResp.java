package edu.cqupt.devbrain.commerce.multimodal.dto;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * 图片上传响应。
 * 包含上传后的图片信息和即时AI分析结果。
 */
public record GuideImageUploadResp(
        String imageId,
        String fileName,
        String contentType,
        Long size,
        String previewUrl,
        String ocrText,
        String visualSummary,
        List<String> detectedProductNames,
        Map<String, String> detectedAttributes,
        List<String> riskFlags,
        Instant createdAt
) {
}
