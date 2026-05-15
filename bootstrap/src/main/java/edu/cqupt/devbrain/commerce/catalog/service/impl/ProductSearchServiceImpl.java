package edu.cqupt.devbrain.commerce.catalog.service.impl;

import com.baomidou.mybatisplus.core.metadata.IPage;
import edu.cqupt.devbrain.commerce.catalog.dto.req.ProductPageReq;
import edu.cqupt.devbrain.commerce.catalog.dto.resp.ProductPageResp;
import edu.cqupt.devbrain.commerce.catalog.service.ProductCatalogService;
import edu.cqupt.devbrain.commerce.catalog.service.ProductSearchService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * 商品搜索服务实现类。
 * 当前直接委托给 ProductCatalogService 进行分页查询，后续可扩展为基于ES的搜索。
 */
@Service
@RequiredArgsConstructor
public class ProductSearchServiceImpl implements ProductSearchService {

    private final ProductCatalogService productCatalogService;

    @Override
    public IPage<ProductPageResp> search(ProductPageReq request) {
        return productCatalogService.pageProducts(request);
    }
}
