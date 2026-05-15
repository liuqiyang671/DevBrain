package edu.cqupt.devbrain.commerce.guide.graph.node;

import edu.cqupt.devbrain.commerce.catalog.dao.mapper.ProductDocumentLinkMapper;
import edu.cqupt.devbrain.commerce.guide.domain.GuideEvidence;
import edu.cqupt.devbrain.commerce.guide.domain.GuideState;
import edu.cqupt.devbrain.commerce.guide.service.ProductEvidenceRetrievalService;
import edu.cqupt.devbrain.commerce.guide.service.impl.ProductEvidenceRetrievalServiceImpl;
import edu.cqupt.devbrain.knowledge.dao.mapper.KnowledgeChunkMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 证据检索节点。
 * <p>
 * 为每个候选商品从关联的知识库文档中检索相关分块作为推荐证据。
 * 证据来源包括商品详情页、评测文章、FAQ、政策文档等。
 * <p>
 * 检索策略：
 * <ul>
 *   <li>基于商品 ID 关联知识库文档</li>
 *   <li>通过关键词匹配评分排序</li>
 *   <li>每个商品取 Top-K 证据（默认 2）</li>
 * </ul>
 * <p>
 * 支持的参数：
 * <ul>
 *   <li>topK — 每个商品取几个证据（默认 2）</li>
 *   <li>productIds — 指定商品 ID 列表（为空时检索所有候选商品）</li>
 *   <li>docTypes — 指定文档类型过滤（如 detail / marketing / faq）</li>
 * </ul>
 *
 * @author liuqiyang
 * @since 2026-05-15
 */
@Component
public class RetrieveEvidenceNode implements GuideWorkflowNode {

    private final ProductEvidenceRetrievalService evidenceRetrievalService;

    @Autowired
    public RetrieveEvidenceNode(ProductEvidenceRetrievalService evidenceRetrievalService) {
        this.evidenceRetrievalService = evidenceRetrievalService;
    }

    public RetrieveEvidenceNode(ProductDocumentLinkMapper productDocumentLinkMapper,
                                KnowledgeChunkMapper knowledgeChunkMapper) {
        this.evidenceRetrievalService = new ProductEvidenceRetrievalServiceImpl(
                productDocumentLinkMapper, knowledgeChunkMapper, Optional.empty());
    }

    @Override
    public String name() {
        return "retrieve_evidence";
    }

    @Override
    public GuideState execute(GuideState state) {
        return execute(state, Map.of());
    }

    public GuideState execute(GuideState state, Map<String, Object> arguments) {
        if (state.getCandidateProducts() == null || state.getCandidateProducts().isEmpty()) {
            state.setEvidences(List.of());
            return state;
        }
        int topK = intArgument(arguments, "topK", 2);
        List<String> productIds = stringListArgument(arguments, "productIds");
        List<String> docTypes = stringListArgument(arguments, "docTypes");
        List<GuideEvidence> evidences = evidenceRetrievalService.retrieve(state, productIds, topK, docTypes);
        state.setEvidences(evidences);
        return state;
    }

    private int intArgument(Map<String, Object> arguments, String key, int fallback) {
        if (arguments == null || !arguments.containsKey(key) || arguments.get(key) == null) {
            return fallback;
        }
        Object value = arguments.get(key);
        if (value instanceof Number number) {
            return number.intValue();
        }
        try {
            return Integer.parseInt(String.valueOf(value));
        } catch (NumberFormatException ex) {
            return fallback;
        }
    }

    private List<String> stringListArgument(Map<String, Object> arguments, String key) {
        if (arguments == null || !(arguments.get(key) instanceof List<?> values)) {
            return List.of();
        }
        return values.stream()
                .map(String::valueOf)
                .filter(StringUtils::hasText)
                .toList();
    }
}
