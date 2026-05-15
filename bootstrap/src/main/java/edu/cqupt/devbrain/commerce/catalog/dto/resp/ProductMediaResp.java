package edu.cqupt.devbrain.commerce.catalog.dto.resp;

/**
 * 商品媒体资源响应。
 * 包含媒体文件的URL、类型和OCR识别文本。
 */
public record ProductMediaResp(
        String id,
        String mediaType,
        String url,
        String objectKey,
        String altText,
        String ocrText,
        String metadata
) {
}
