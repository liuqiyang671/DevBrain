package edu.cqupt.devbrain.commerce.catalog.dto.req;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * 商品媒体资源新增/更新请求参数。
 * 用于批量设置商品关联的图片、视频等媒体文件。
 */
public record ProductMediaUpsertReq(
        @Pattern(regexp = "main|detail|upload|ocr", message = "媒体类型不合法")
        String mediaType,
        @NotBlank(message = "媒体 URL 不能为空")
        @Size(max = 512, message = "媒体 URL 不能超过 512 个字符")
        String url,
        String objectKey,
        @Size(max = 256, message = "替代文本不能超过 256 个字符")
        String altText,
        String ocrText,
        String metadata
) {
}
