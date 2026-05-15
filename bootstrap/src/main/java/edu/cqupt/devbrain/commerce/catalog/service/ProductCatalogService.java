package edu.cqupt.devbrain.commerce.catalog.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import edu.cqupt.devbrain.commerce.catalog.dto.req.ProductAttributeUpsertReq;
import edu.cqupt.devbrain.commerce.catalog.dto.req.ProductCreateReq;
import edu.cqupt.devbrain.commerce.catalog.dto.req.ProductDocumentBindReq;
import edu.cqupt.devbrain.commerce.catalog.dto.req.ProductMediaUpsertReq;
import edu.cqupt.devbrain.commerce.catalog.dto.req.ProductPageReq;
import edu.cqupt.devbrain.commerce.catalog.dto.req.ProductSkuUpsertReq;
import edu.cqupt.devbrain.commerce.catalog.dto.req.ProductUpdateReq;
import edu.cqupt.devbrain.commerce.catalog.dto.resp.ProductDetailResp;
import edu.cqupt.devbrain.commerce.catalog.dto.resp.ProductPageResp;

import java.util.List;

/**
 * 商品目录服务接口。
 * 提供商品SPU的完整生命周期管理，包括增删改查及子实体（SKU、属性、媒体、文档）的关联操作。
 */
public interface ProductCatalogService {

    /** 创建商品SPU */
    ProductDetailResp createProduct(ProductCreateReq request);

    /** 更新商品基本信息 */
    ProductDetailResp updateProduct(String productId, ProductUpdateReq request);

    /** 删除商品及其所有关联数据（SKU、属性、媒体、文档链接） */
    void deleteProduct(String productId);

    /** 查询商品详情，包含所有关联子实体 */
    ProductDetailResp getProduct(String productId);

    /** 分页查询商品列表，支持多条件筛选 */
    IPage<ProductPageResp> pageProducts(ProductPageReq request);

    /** 批量更新商品SKU（全量替换模式） */
    void upsertSkus(String productId, List<ProductSkuUpsertReq> requests);

    /** 批量更新商品属性（全量替换模式） */
    void upsertAttributes(String productId, List<ProductAttributeUpsertReq> requests);

    /** 批量更新商品媒体资源（全量替换模式） */
    void upsertMedia(String productId, List<ProductMediaUpsertReq> requests);

    /** 将知识库文档绑定到商品 */
    void bindDocument(String productId, ProductDocumentBindReq request);
}
