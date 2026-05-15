package edu.cqupt.devbrain.commerce.guide.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import edu.cqupt.devbrain.commerce.guide.config.GuideAnswerProperties;
import edu.cqupt.devbrain.commerce.guide.domain.GuideRecommendation;
import edu.cqupt.devbrain.commerce.guide.domain.GuideState;
import edu.cqupt.devbrain.framework.convention.ChatMessage;
import edu.cqupt.devbrain.framework.convention.ChatRequest;
import edu.cqupt.devbrain.infra.ai.llm.LLMService;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.DefaultResourceLoader;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LLMGuideAnswerGeneratorTest {

    @Test
    void buildsChatRequestWithPromptRulesAndRecommendationPayload() {
        RecordingLLMService llmService = new RecordingLLMService("模型生成的导购回答");
        GuideAnswerProperties properties = new GuideAnswerProperties();
        LLMGuideAnswerGenerator generator = new LLMGuideAnswerGenerator(
                llmService,
                new ObjectMapper(),
                properties,
                new DefaultResourceLoader()
        );
        GuideState state = GuideState.builder()
                .userText("你好，我想购买一个手机")
                .recommendations(List.of(
                        recommendation("product-1", "商旅 Max Pro 商务续航手机"),
                        recommendation("product-2", "影像 Lite 手机")
                ))
                .build();

        Optional<String> answer = generator.generate(state);

        assertEquals(Optional.of("模型生成的导购回答"), answer);
        ChatRequest request = llmService.lastRequest;
        assertNotNull(request);
        assertEquals(2, request.getMessages().size());
        assertEquals(ChatMessage.Role.SYSTEM, request.getMessages().get(0).getRole());
        assertEquals(ChatMessage.Role.USER, request.getMessages().get(1).getRole());
        String systemPrompt = request.getMessages().get(0).getContent();
        assertTrue(systemPrompt.contains("先给 2-3 个可选项"));
        assertTrue(systemPrompt.contains("不要只给一个结论"));
        assertTrue(systemPrompt.contains("预算、品牌、主要用途"));
        String payload = request.getMessages().get(1).getContent();
        assertTrue(payload.contains("你好，我想购买一个手机"));
        assertTrue(payload.contains("商旅 Max Pro 商务续航手机"));
        assertTrue(payload.contains("影像 Lite 手机"));
        assertEquals(properties.getTemperature(), request.getTemperature());
        assertEquals(properties.getMaxTokens(), request.getMaxTokens());
        assertEquals(properties.getTimeoutMillis(), request.getTimeoutMillis());
    }

    private static GuideRecommendation recommendation(String productId, String name) {
        return GuideRecommendation.builder()
                .productId(productId)
                .name(name)
                .priceMin(new BigDecimal("3299"))
                .priceMax(new BigDecimal("3799"))
                .stockStatus("in_stock")
                .score(81D)
                .reasons(List.of("综合匹配较高"))
                .build();
    }

    private static final class RecordingLLMService implements LLMService {

        private final String answer;
        private ChatRequest lastRequest;

        private RecordingLLMService(String answer) {
            this.answer = answer;
        }

        @Override
        public String chat(String prompt) {
            throw new AssertionError("应使用 ChatRequest 调用 LLM");
        }

        @Override
        public String chat(ChatRequest request) {
            this.lastRequest = request;
            return answer;
        }
    }
}
