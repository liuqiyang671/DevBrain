package edu.cqupt.devbrain.commerce.multimodal.dto;

/**
 * 图片分析请求参数。
 * 控制是否强制重新分析已分析过的图片。
 */
public record GuideImageAnalyzeReq(
        Boolean force
) {
}
