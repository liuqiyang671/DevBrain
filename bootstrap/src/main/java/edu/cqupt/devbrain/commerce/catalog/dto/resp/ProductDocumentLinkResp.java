package edu.cqupt.devbrain.commerce.catalog.dto.resp;

/**
 * 商品-文档关联响应。
 * 包含绑定的文档ID、分块ID和绑定类型。
 */
public record ProductDocumentLinkResp(
        String id,
        String documentId,
        String chunkId,
        String bindType,
        String metadata
) {
}
