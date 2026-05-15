package edu.cqupt.devbrain.commerce.multimodal.service;

import edu.cqupt.devbrain.commerce.multimodal.dto.ImageUnderstandingResult;

/**
 * 图片理解服务接口。
 * 调用AI视觉模型对图片进行分析，提取OCR文本、商品信息和风险标记。
 */
public interface ImageUnderstandingService {

    /** 分析指定图片，返回视觉理解结果 */
    ImageUnderstandingResult analyze(String imageId);
}
