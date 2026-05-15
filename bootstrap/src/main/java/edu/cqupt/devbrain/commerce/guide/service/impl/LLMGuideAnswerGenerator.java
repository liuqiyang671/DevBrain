package edu.cqupt.devbrain.commerce.guide.service.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import edu.cqupt.devbrain.commerce.guide.config.GuideAnswerProperties;
import edu.cqupt.devbrain.commerce.guide.domain.GuideRecommendation;
import edu.cqupt.devbrain.commerce.guide.domain.GuideState;
import edu.cqupt.devbrain.commerce.guide.service.GuideAnswerGenerator;
import edu.cqupt.devbrain.framework.convention.ChatMessage;
import edu.cqupt.devbrain.framework.convention.ChatRequest;
import edu.cqupt.devbrain.infra.ai.llm.LLMService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 基于 LLM 的导购回答生成器。
 * <p>
 * 将推荐结果和用户上下文组装成 prompt，调用 LLM 生成自然语言回答。
 * <ul>
 *   <li><b>System Prompt</b> — 从配置文件加载（默认 classpath:prompts/guide/answer-generation.md）</li>
 *   <li><b>User Payload</b> — JSON 格式，包含 userText、imageRefs、recommendations（最多 5 条）</li>
 *   <li><b>推荐信息</b> — 每条推荐包含 productId、name、brand、price、stockStatus、promotions、score、role、reasons、riskFlags</li>
 * </ul>
 * <p>
 * 降级策略：LLM 调用失败时返回 Optional.empty()，由调用方降级到本地模板回答。
 * 配置开关：通过 {@link GuideAnswerProperties#isLlmEnabled()} 控制是否启用 LLM 生成。
 *
 * @author liuqiyang
 * @since 2026-05-15
 * @see GuideAnswerGenerator 接口
 * @see GuideAnswerProperties 回答生成配置
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class LLMGuideAnswerGenerator implements GuideAnswerGenerator {

    /** LLM 服务（用于调用大模型生成回答） */
    private final LLMService llmService;

    /** JSON 序列化器 */
    private final ObjectMapper objectMapper;

    /** 回答生成配置（prompt 位置、温度、超时等） */
    private final GuideAnswerProperties properties;

    /** 资源加载器（用于加载 prompt 文件） */
    private final ResourceLoader resourceLoader;

    /**
     * 生成导购回答。
     * <p>
     * 前置条件：LLM 开关开启、state 非空、推荐列表非空。
     * 调用 LLM 后返回 trim 后的回答文本；失败时降级返回 empty。
     */
    @Override
    public Optional<String> generate(GuideState state) {
        if (!properties.isLlmEnabled()
                || state == null
                || state.getRecommendations() == null
                || state.getRecommendations().isEmpty()) {
            return Optional.empty();
        }
        try {
            String answer = llmService.chat(ChatRequest.builder()
                    .messages(List.of(
                            ChatMessage.system(systemPrompt()),
                            ChatMessage.user(userPayload(state))
                    ))
                    .temperature(properties.getTemperature())
                    .maxTokens(properties.getMaxTokens())
                    .timeoutMillis(properties.getTimeoutMillis())
                    .build());
            return StringUtils.hasText(answer) ? Optional.of(answer.trim()) : Optional.empty();
        } catch (RuntimeException | IOException ex) {
            log.warn("导购回答 LLM 生成失败，降级到本地模板：{}", ex.getMessage());
            return Optional.empty();
        }
    }

    /** 从配置的 prompt 文件加载 system prompt */
    private String systemPrompt() throws IOException {
        Resource resource = resourceLoader.getResource(properties.getPromptLocation());
        return resource.getContentAsString(StandardCharsets.UTF_8);
    }

    /** 构建 user 消息的 JSON payload：userText + imageRefs + recommendations（最多 5 条） */
    private String userPayload(GuideState state) throws JsonProcessingException {
        Map<String, Object> payload = Map.of(
                "userText", state.getUserText() == null ? "" : state.getUserText(),
                "imageRefs", state.getImageRefs() == null ? List.of() : state.getImageRefs(),
                "recommendations", state.getRecommendations().stream().limit(5).map(this::recommendationPayload).toList()
        );
        return objectMapper.writeValueAsString(payload);
    }

    /** 将单条推荐转换为 LLM 可理解的 Map 结构 */
    private Map<String, Object> recommendationPayload(GuideRecommendation recommendation) {
        return Map.of(
                "productId", safe(recommendation.getProductId()),
                "name", safe(recommendation.getName()),
                "brand", safe(recommendation.getBrand()),
                "price", priceRange(recommendation),
                "stockStatus", stockText(recommendation.getStockStatus()),
                "promotions", recommendation.getPromotions() == null ? List.of() : recommendation.getPromotions(),
                "score", recommendation.getScore() == null ? "" : Math.round(recommendation.getScore()),
                "role", safe(recommendation.getRecommendationRole()),
                "reasons", recommendation.getReasons() == null ? List.of() : recommendation.getReasons(),
                "riskFlags", recommendation.getRiskFlags() == null ? List.of() : recommendation.getRiskFlags()
        );
    }

    private String safe(String value) {
        return value == null ? "" : value;
    }

    /** 格式化价格区间：单价格 → "XX 元"，区间 → "min-max 元"，无价格 → "待确认" */
    private String priceRange(GuideRecommendation recommendation) {
        BigDecimal min = recommendation.getPriceMin();
        BigDecimal max = recommendation.getPriceMax();
        if (min == null && max == null) {
            return "待确认";
        }
        if (min != null && max != null && min.compareTo(max) != 0) {
            return min.stripTrailingZeros().toPlainString() + "-" + max.stripTrailingZeros().toPlainString() + " 元";
        }
        return (min == null ? max : min).stripTrailingZeros().toPlainString() + " 元";
    }

    private String stockText(String stockStatus) {
        return switch (String.valueOf(stockStatus)) {
            case "in_stock" -> "有货";
            case "out_of_stock" -> "缺货";
            default -> "待确认";
        };
    }
}
