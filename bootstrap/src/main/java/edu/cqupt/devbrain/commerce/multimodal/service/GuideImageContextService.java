package edu.cqupt.devbrain.commerce.multimodal.service;

import edu.cqupt.devbrain.commerce.multimodal.dto.GuideImageContext;

import java.util.List;

/**
 * 导购图片上下文服务接口。
 * 将多张图片的分析结果聚合为文本上下文，注入到导购对话中。
 */
public interface GuideImageContextService {

    /** 根据图片ID列表构建图片上下文 */
    GuideImageContext buildContext(List<String> imageIds, String userId);
}
