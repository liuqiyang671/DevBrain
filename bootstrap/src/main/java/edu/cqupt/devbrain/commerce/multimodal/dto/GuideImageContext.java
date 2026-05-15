package edu.cqupt.devbrain.commerce.multimodal.dto;

import java.util.List;

/**
 * 导购图片上下文。
 * 将多张图片的分析结果聚合为文本上下文，注入到导购对话中。
 */
public record GuideImageContext(
        List<GuideImageRef> images,
        String contextText
) {
}
