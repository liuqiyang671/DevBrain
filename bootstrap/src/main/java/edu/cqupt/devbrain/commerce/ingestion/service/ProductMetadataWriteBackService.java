package edu.cqupt.devbrain.commerce.ingestion.service;

import edu.cqupt.devbrain.commerce.ingestion.dto.ProductExtractionResult;

/**
 * 商品元数据回写服务接口。
 * 将AI抽取的结果持久化到商品的属性、标签和摘要字段中。
 */
public interface ProductMetadataWriteBackService {

    /** 将抽取结果应用到商品元数据（属性、标签、卖点、受众等） */
    void applyExtraction(String productId, String documentId, ProductExtractionResult result);
}
