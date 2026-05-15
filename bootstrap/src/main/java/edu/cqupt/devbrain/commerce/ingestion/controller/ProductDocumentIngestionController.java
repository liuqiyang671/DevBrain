package edu.cqupt.devbrain.commerce.ingestion.controller;

import edu.cqupt.devbrain.commerce.catalog.dto.req.ProductDocumentBindReq;
import edu.cqupt.devbrain.commerce.catalog.dto.resp.ProductDocumentLinkResp;
import edu.cqupt.devbrain.commerce.catalog.service.ProductCatalogService;
import edu.cqupt.devbrain.commerce.ingestion.dto.ProductDocumentExtractionResp;
import edu.cqupt.devbrain.commerce.ingestion.service.ProductDocumentExtractionService;
import edu.cqupt.devbrain.framework.convention.Result;
import edu.cqupt.devbrain.framework.web.Results;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 商品文档摄入控制器。
 * 提供商品文档的绑定、属性抽取和文档列表查询API。
 */
@RestController
@RequiredArgsConstructor
public class ProductDocumentIngestionController {

    private final ProductCatalogService productCatalogService;
    private final ProductDocumentExtractionService extractionService;

    /** 绑定文档到商品 */
    @PostMapping("/commerce/products/{productId}/documents/{documentId}/bind")
    public Result<Void> bind(@PathVariable String productId,
                             @PathVariable String documentId,
                             @RequestBody(required = false) @Valid ProductDocumentBindReq request) {
        ProductDocumentBindReq bindRequest = request == null
                ? new ProductDocumentBindReq(documentId, null, "detail", false, null)
                : new ProductDocumentBindReq(documentId, request.chunkId(), request.bindType(),
                request.extractAttributes(), request.metadata());
        productCatalogService.bindDocument(productId, bindRequest);
        return Results.success();
    }

    /** 对已绑定文档执行AI属性抽取 */
    @PostMapping("/commerce/products/{productId}/documents/{documentId}/extract")
    public Result<ProductDocumentExtractionResp> extract(@PathVariable String productId,
                                                        @PathVariable String documentId) {
        return Results.success(extractionService.extractBoundDocument(productId, documentId));
    }

    /** 查询商品绑定的文档列表 */
    @GetMapping("/commerce/products/{productId}/documents")
    public Result<List<ProductDocumentLinkResp>> listDocuments(@PathVariable String productId) {
        return Results.success(productCatalogService.getProduct(productId).documents());
    }
}
