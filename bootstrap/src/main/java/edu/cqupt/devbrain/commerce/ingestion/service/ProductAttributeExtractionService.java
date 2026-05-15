package edu.cqupt.devbrain.commerce.ingestion.service;

import edu.cqupt.devbrain.commerce.ingestion.dto.ProductExtractionInput;
import edu.cqupt.devbrain.commerce.ingestion.dto.ProductExtractionResult;

/**
 * 商品属性AI抽取服务接口。
 * 调用AI模型从文档内容中抽取结构化的商品属性信息。
 */
public interface ProductAttributeExtractionService {

    /** 从文档内容中抽取商品属性，返回结构化抽取结果 */
    ProductExtractionResult extract(ProductExtractionInput input);
}
