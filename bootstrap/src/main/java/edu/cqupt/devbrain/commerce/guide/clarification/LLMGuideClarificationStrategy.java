package edu.cqupt.devbrain.commerce.guide.clarification;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import edu.cqupt.devbrain.framework.convention.ChatMessage;
import edu.cqupt.devbrain.framework.convention.ChatRequest;
import edu.cqupt.devbrain.infra.ai.gateway.structured.AiJsonOutputParser;
import edu.cqupt.devbrain.infra.ai.llm.LLMService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 基于 LLM 的结构化追问策略。
 * <p>
 * 调用 LLM 分析用户意图、槽位和候选商品状态，生成结构化的追问计划。
 * 核心流程：
 * <ol>
 *   <li>加载 System Prompt（从配置文件或内置默认模板）</li>
 *   <li>构建包含上下文、策略和期望输出格式的 User Payload</li>
 *   <li>调用 LLM 获取 JSON 响应</li>
 *   <li>解析为 {@link ClarificationPlan}</li>
 * </ol>
 * <p>
 * LLM Prompt 约束：
 * <ul>
 *   <li>优先使用 recommend_then_ask（先推荐再追问）</li>
 *   <li>只有品类不明确、候选质量过低或约束冲突时才用 ask_only</li>
 *   <li>禁止臆造用户未表达的信息</li>
 * </ul>
 * <p>
 * 注意：此策略需要 {@code llmEnabled=true} 才能使用，否则抛出 IllegalStateException。
 * 生产环境通常通过 {@link ValidatingGuideClarificationStrategy} 包装使用，
 * LLM 失败时自动降级到 {@link PolicyGuideClarificationStrategy}。
 *
 * @author liuqiyang
 * @since 2026-05-15
 * @see GuideClarificationStrategy 追问策略接口
 * @see ValidatingGuideClarificationStrategy 校验包装器
 * @see PolicyGuideClarificationStrategy 确定性策略（降级方案）
 */
@Slf4j
@Component
public class LLMGuideClarificationStrategy implements GuideClarificationStrategy {

    /** LLM 服务 */
    private final LLMService llmService;

    /** JSON 序列化器 */
    private final ObjectMapper objectMapper;

    /** 追问配置属性 */
    private final GuideClarificationProperties properties;

    /** 资源加载器（用于加载 Prompt 模板） */
    private final ResourceLoader resourceLoader;

    /** JSON 输出解析器 */
    private final AiJsonOutputParser parser;

    public LLMGuideClarificationStrategy(LLMService llmService,
                                         ObjectMapper objectMapper,
                                         GuideClarificationProperties properties,
                                         ResourceLoader resourceLoader) {
        this.llmService = llmService;
        this.objectMapper = objectMapper == null ? new ObjectMapper() : objectMapper;
        this.properties = properties == null ? GuideClarificationProperties.defaults() : properties;
        this.resourceLoader = resourceLoader;
        this.parser = new AiJsonOutputParser(this.objectMapper);
    }

    /**
     * 使用 LLM 生成追问计划。
     *
     * @param context 追问上下文
     * @return 追问计划
     * @throws IllegalStateException 如果 LLM 未启用或调用失败
     */
        if (!properties.isLlmEnabled()) {
            throw new IllegalStateException("LLM clarification strategy disabled");
        }
        try {
            String answer = llmService.chat(ChatRequest.builder()
                    .messages(List.of(
                            ChatMessage.system(systemPrompt()),
                            ChatMessage.user(userPayload(context))
                    ))
                    .responseFormat(ChatRequest.ResponseFormat.jsonObject())
                    .temperature(properties.getTemperature())
                    .maxTokens(properties.getMaxTokens())
                    .timeoutMillis(properties.getTimeoutMillis())
                    .build());
            ClarificationPlan plan = parser.parse(answer, ClarificationPlan.class);
            if (plan == null) {
                throw new IllegalStateException("LLM clarification plan is null");
            }
            return plan;
        } catch (RuntimeException | IOException ex) {
            log.warn("导购追问 LLM 策略失败：{}", ex.getMessage());
            throw new IllegalStateException("LLM clarification strategy failed: " + ex.getMessage(), ex);
        }
    }

    private String systemPrompt() throws IOException {
        if (resourceLoader == null) {
            return defaultPrompt();
        }
        Resource resource = resourceLoader.getResource(properties.getPromptLocation());
        if (resource == null || !resource.exists()) {
            return defaultPrompt();
        }
        String prompt = resource.getContentAsString(StandardCharsets.UTF_8);
        return StringUtils.hasText(prompt) ? prompt : defaultPrompt();
    }

    private String userPayload(ClarificationContext context) throws JsonProcessingException {
        ClarificationContext safeContext = context == null ? ClarificationContext.from(null) : context;
        ClarificationPolicy policy = properties.policyFor(safeContext.category());
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("conversationId", safeContext.conversationId());
        payload.put("userMessage", safeContext.userMessage());
        payload.put("intentType", safeContext.intentType());
        payload.put("category", safeContext.category());
        payload.put("slots", safeContext.slots());
        payload.put("missingSlots", safeContext.missingSlots());
        payload.put("candidateCount", safeContext.candidateCount());
        payload.put("candidateQuality", safeContext.candidateQuality());
        payload.put("clarificationTurnCount", safeContext.clarificationTurnCount());
        payload.put("memorySnapshot", safeContext.memorySnapshot());
        payload.put("policy", policy);
        payload.put("candidateProductSignals", List.of("候选商品数量", "候选商品质量", "商品属性", "商品文档证据"));
        payload.put("businessDataSignals", List.of("价格", "库存", "优惠", "商品属性", "商品文档证据"));
        payload.put("expectedOutput", Map.of(
                "shouldAsk", "boolean",
                "mode", List.of("ask_only", "recommend_then_ask", "skip", "confirm_then_continue"),
                "question", "string",
                "targetSlots", "array<string>",
                "reason", "string",
                "confidence", "0~1"
        ));
        return objectMapper.writeValueAsString(payload);
    }

    private String defaultPrompt() {
        return """
                你是电商导购追问策略师。你只决定本轮是否追问，不直接编造商品事实。
                输入包含用户原话、意图、槽位、候选商品数量/质量、历史偏好和策略配置。
                当用户只有泛购买意图且商品候选可用或品类明确时，不要只追问。
                优先使用 recommend_then_ask：先允许系统结合真实商品库里的价格、库存、优惠和证据给出可用推荐，再温和邀请用户补充预算、用途、品牌或优惠偏好。
                只有品类不明确、候选召回质量过低、对比对象不足或约束互相冲突时，才使用 ask_only 或 confirm_then_continue。
                禁止臆造用户没有表达的预算、品牌、用途。禁止提出敏感或无关问题。
                仅输出 JSON 对象，不输出 Markdown。
                """;
    }
}
