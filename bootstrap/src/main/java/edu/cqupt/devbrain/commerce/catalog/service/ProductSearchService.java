package edu.cqupt.devbrain.commerce.catalog.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import edu.cqupt.devbrain.commerce.catalog.dto.req.ProductPageReq;
import edu.cqupt.devbrain.commerce.catalog.dto.resp.ProductPageResp;

/**
 * 商品搜索服务接口。
 * 提供商品的检索能力，当前委托给 ProductCatalogService 实现。
 */
public interface ProductSearchService {

    /** 搜索商品，支持关键词、品牌、类目等多维度筛选 */
    IPage<ProductPageResp> search(ProductPageReq request);
}
