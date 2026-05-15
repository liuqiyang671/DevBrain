package edu.cqupt.devbrain.commerce.ingestion.service;

import edu.cqupt.devbrain.commerce.ingestion.dto.ProductDocumentExtractionResp;

/**
 * 商品文档抽取服务接口。
 * 编排完整的文档抽取流程：读取文档内容 -> AI抽取 -> 结果回写。
 */
public interface ProductDocumentExtractionService {

    /** 对已绑定的商品文档执行属性抽取并回写结果 */
    ProductDocumentExtractionResp extractBoundDocument(String productId, String documentId);
}
