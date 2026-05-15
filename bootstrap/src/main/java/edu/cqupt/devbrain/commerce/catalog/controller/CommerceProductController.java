package edu.cqupt.devbrain.commerce.catalog.controller;

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
import edu.cqupt.devbrain.commerce.catalog.service.ProductCatalogService;
import edu.cqupt.devbrain.framework.convention.Result;
import edu.cqupt.devbrain.framework.web.Results;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 商品管理控制器。
 * 提供商品SPU的RESTful API，包括商品的增删改查及SKU、属性、媒体、文档的关联管理。
 */
@RestController
@RequiredArgsConstructor
public class CommerceProductController {

    private final ProductCatalogService productCatalogService;

    /** 创建商品SPU */
    @PostMapping("/commerce/products")
    public Result<ProductDetailResp> create(@RequestBody @Valid ProductCreateReq request) {
        return Results.success(productCatalogService.createProduct(request));
    }

    /** 更新商品信息 */
    @PutMapping("/commerce/products/{productId}")
    public Result<ProductDetailResp> update(@PathVariable String productId,
                                            @RequestBody @Valid ProductUpdateReq request) {
        return Results.success(productCatalogService.updateProduct(productId, request));
    }

    /** 删除商品及其所有关联数据 */
    @DeleteMapping("/commerce/products/{productId}")
    public Result<Void> delete(@PathVariable String productId) {
        productCatalogService.deleteProduct(productId);
        return Results.success();
    }

    /** 查询商品详情 */
    @GetMapping("/commerce/products/{productId}")
    public Result<ProductDetailResp> detail(@PathVariable String productId) {
        return Results.success(productCatalogService.getProduct(productId));
    }

    /** 分页查询商品列表 */
    @GetMapping("/commerce/products")
    public Result<IPage<ProductPageResp>> page(@Valid ProductPageReq request) {
        return Results.success(productCatalogService.pageProducts(request));
    }

    /** 批量更新商品SKU（全量替换） */
    @PutMapping("/commerce/products/{productId}/skus")
    public Result<Void> upsertSkus(@PathVariable String productId,
                                   @RequestBody @Valid List<ProductSkuUpsertReq> request) {
        productCatalogService.upsertSkus(productId, request);
        return Results.success();
    }

    /** 批量更新商品属性（全量替换） */
    @PutMapping("/commerce/products/{productId}/attributes")
    public Result<Void> upsertAttributes(@PathVariable String productId,
                                         @RequestBody @Valid List<ProductAttributeUpsertReq> request) {
        productCatalogService.upsertAttributes(productId, request);
        return Results.success();
    }

    /** 批量更新商品媒体资源（全量替换） */
    @PutMapping("/commerce/products/{productId}/media")
    public Result<Void> upsertMedia(@PathVariable String productId,
                                    @RequestBody @Valid List<ProductMediaUpsertReq> request) {
        productCatalogService.upsertMedia(productId, request);
        return Results.success();
    }

    /** 绑定知识库文档到商品 */
    @PostMapping("/commerce/products/{productId}/documents")
    public Result<Void> bindDocument(@PathVariable String productId,
                                     @RequestBody @Valid ProductDocumentBindReq request) {
        productCatalogService.bindDocument(productId, request);
        return Results.success();
    }
}
