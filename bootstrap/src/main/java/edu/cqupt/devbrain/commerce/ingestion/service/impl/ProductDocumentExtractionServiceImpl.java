package edu.cqupt.devbrain.commerce.ingestion.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import edu.cqupt.devbrain.commerce.catalog.dao.entity.ProductDO;
import edu.cqupt.devbrain.commerce.catalog.dao.entity.ProductDocumentLinkDO;
import edu.cqupt.devbrain.commerce.catalog.dao.mapper.ProductDocumentLinkMapper;
import edu.cqupt.devbrain.commerce.catalog.dao.mapper.ProductMapper;
import edu.cqupt.devbrain.commerce.ingestion.dto.ProductDocumentExtractionResp;
import edu.cqupt.devbrain.commerce.ingestion.dto.ProductExtractionInput;
import edu.cqupt.devbrain.commerce.ingestion.dto.ProductExtractionResult;
import edu.cqupt.devbrain.commerce.ingestion.service.ProductAttributeExtractionService;
import edu.cqupt.devbrain.commerce.ingestion.service.ProductDocumentExtractionService;
import edu.cqupt.devbrain.commerce.ingestion.service.ProductMetadataWriteBackService;
import edu.cqupt.devbrain.framework.exception.ClientException;
import edu.cqupt.devbrain.knowledge.dao.entity.KnowledgeChunkDO;
import edu.cqupt.devbrain.knowledge.dao.entity.KnowledgeDocumentDO;
import edu.cqupt.devbrain.knowledge.dao.mapper.KnowledgeChunkMapper;
import edu.cqupt.devbrain.knowledge.dao.mapper.KnowledgeDocumentMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.List;

/**
 * 商品文档抽取服务实现类。
 * 编排完整的文档属性抽取流程：
 * 1. 校验商品和文档的存在性及绑定关系
 * 2. 拼接文档分块内容作为抽取输入
 * 3. 调用AI抽取服务获取结构化结果
 * 4. 将结果回写到商品元数据
 */
@Service
@RequiredArgsConstructor
public class ProductDocumentExtractionServiceImpl implements ProductDocumentExtractionService {

    private static final int MAX_SOURCE_TEXT_LENGTH = 16_000;

    private final ProductMapper productMapper;
    private final ProductDocumentLinkMapper productDocumentLinkMapper;
    private final KnowledgeDocumentMapper knowledgeDocumentMapper;
    private final KnowledgeChunkMapper knowledgeChunkMapper;
    private final ProductAttributeExtractionService extractionService;
    private final ProductMetadataWriteBackService writeBackService;

    @Override
    @Transactional
    public ProductDocumentExtractionResp extractBoundDocument(String productId, String documentId) {
        ProductDO product = requireProduct(productId);
        KnowledgeDocumentDO document = requireDocument(documentId);
        requireDocumentBinding(productId, documentId);
        List<KnowledgeChunkDO> chunks = knowledgeChunkMapper.selectByDocId(documentId);
        String content = buildSourceText(document, chunks);
        ProductExtractionResult result = extractionService.extract(new ProductExtractionInput(
                productId,
                documentId,
                document.getDocName(),
                content,
                product.getBrand(),
                product.getCategoryId(),
                document.getSourceType()
        ));
        writeBackService.applyExtraction(productId, documentId, result);
        return toResponse(result);
    }

    private ProductDocumentExtractionResp toResponse(ProductExtractionResult result) {
        return new ProductDocumentExtractionResp(
                result.productId(),
                result.documentId(),
                size(result.attributes()),
                size(result.sellingPoints()),
                size(result.audiences()),
                size(result.constraints()),
                size(result.promotions()),
                result.failureReason()
        );
    }

    private String buildSourceText(KnowledgeDocumentDO document, List<KnowledgeChunkDO> chunks) {
        StringBuilder builder = new StringBuilder();
        if (StringUtils.hasText(document.getDocName())) {
            builder.append("# ").append(document.getDocName()).append('\n');
        }
        for (KnowledgeChunkDO chunk : chunks == null ? List.<KnowledgeChunkDO>of() : chunks) {
            if (StringUtils.hasText(chunk.getContent())) {
                builder.append(chunk.getContent()).append("\n\n");
                if (builder.length() >= MAX_SOURCE_TEXT_LENGTH) {
                    return builder.substring(0, MAX_SOURCE_TEXT_LENGTH);
                }
            }
        }
        return builder.toString();
    }

    private void requireDocumentBinding(String productId, String documentId) {
        Long count = productDocumentLinkMapper.selectCount(Wrappers.lambdaQuery(ProductDocumentLinkDO.class)
                .eq(ProductDocumentLinkDO::getProductId, productId)
                .eq(ProductDocumentLinkDO::getDocId, documentId)
                .eq(ProductDocumentLinkDO::getDeleted, 0));
        if (count == null || count == 0) {
            throw new ClientException("商品尚未绑定该文档");
        }
    }

    private ProductDO requireProduct(String productId) {
        ProductDO product = productMapper.selectById(productId);
        if (product == null || Integer.valueOf(1).equals(product.getDeleted())) {
            throw new ClientException("商品不存在或已删除");
        }
        return product;
    }

    private KnowledgeDocumentDO requireDocument(String documentId) {
        KnowledgeDocumentDO document = knowledgeDocumentMapper.selectById(documentId);
        if (document == null || Integer.valueOf(1).equals(document.getDeleted())) {
            throw new ClientException("文档不存在或已删除");
        }
        return document;
    }

    private int size(List<?> values) {
        return values == null ? 0 : values.size();
    }
}
