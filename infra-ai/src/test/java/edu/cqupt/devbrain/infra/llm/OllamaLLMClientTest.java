package edu.cqupt.devbrain.infra.llm;

import edu.cqupt.devbrain.framework.convention.ChatMessage;
import edu.cqupt.devbrain.framework.convention.ChatRequest;
import edu.cqupt.devbrain.infra.config.AIModelProperties;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class OllamaLLMClientTest {

    private MockWebServer server;

    @BeforeEach
    void setUp() throws IOException {
        server = new MockWebServer();
        server.start();
    }

    @AfterEach
    void tearDown() throws IOException {
        server.shutdown();
    }

    @Test
    void localChatUsesOpenAiCompatibleEndpointWithoutApiKey() throws Exception {
        OllamaLLMClient client = new OllamaLLMClient(new AIModelProperties());
        server.enqueue(new MockResponse()
                .setResponseCode(200)
                .addHeader("Content-Type", "application/json")
                .setBody("""
                        {
                          "choices": [{
                            "message": {"role": "assistant", "content": "local answer"}
                          }]
                        }
                        """));

        String answer = client.chat(
                ChatRequest.builder()
                        .messages(List.of(ChatMessage.user("你好")))
                        .build(),
                new ChatTarget(
                        "ollama",
                        "qwen3.6:35b-a3b",
                        server.url("/v1/chat/completions").toString(),
                        null
                )
        );

        assertThat(client.provider()).isEqualTo("ollama");
        assertThat(OllamaLLMClient.class).hasAnnotation(Component.class);
        assertThat(answer).isEqualTo("local answer");

        var request = server.takeRequest();
        String requestBody = request.getBody().readUtf8();
        assertThat(request.getHeader("Authorization")).isNull();
        assertThat(requestBody).contains(
                "\"model\":\"qwen3.6:35b-a3b\"",
                "\"stream\":false",
                "\"content\":\"你好\""
        );
    }

    @Test
    void thinkingRequestUsesOllamaReasoningField() throws Exception {
        OllamaLLMClient client = new OllamaLLMClient(new AIModelProperties());
        server.enqueue(new MockResponse()
                .setResponseCode(200)
                .addHeader("Content-Type", "application/json")
                .setBody("""
                        {
                          "choices": [{
                            "message": {"role": "assistant", "content": "reasoned answer"}
                          }]
                        }
                        """));

        client.chat(
                ChatRequest.builder()
                        .messages(List.of(ChatMessage.user("分析一下")))
                        .thinking(true)
                        .build(),
                new ChatTarget(
                        "ollama",
                        "qwen3.6:35b-a3b",
                        server.url("/v1/chat/completions").toString(),
                        null
                )
        );

        String requestBody = server.takeRequest().getBody().readUtf8();
        assertThat(requestBody)
                .contains("\"reasoning_effort\":\"medium\"")
                .doesNotContain("enable_thinking");
    }
}
