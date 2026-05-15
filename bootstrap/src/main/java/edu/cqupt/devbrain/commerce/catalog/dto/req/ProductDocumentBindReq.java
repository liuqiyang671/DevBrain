package edu.cqupt.devbrain.commerce.catalog.dto.req;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

/**
 * 商品绑定文档请求参数。
 * 将知识库中的文档关联到指定商品，支持自动触发属性抽取。
 */
public record ProductDocumentBindReq(
        @NotBlank(message = "文档 ID 不能为空")
        String documentId,
        String chunkId,
        @Pattern(regexp = "detail|marketing|faq|policy|review", message = "文档绑定类型不合法")
        String bindType,
        Boolean extractAttributes,
        String metadata
) {
}
