package edu.cqupt.devbrain.infra.llm;

import edu.cqupt.devbrain.framework.convention.ChatMessage;
import edu.cqupt.devbrain.framework.convention.ChatRequest;
import edu.cqupt.devbrain.framework.exception.RemoteException;
import edu.cqupt.devbrain.infra.ai.llm.StreamCallback;
import okhttp3.OkHttpClient;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AbstractOpenAIStyleLLMClientTest {

    private MockWebServer server;
    private TestLLMClient client;

    @BeforeEach
    void setUp() throws IOException {
        server = new MockWebServer();
        server.start();
        OkHttpClient httpClient = new OkHttpClient.Builder()
                .connectTimeout(Duration.ofSeconds(2))
                .readTimeout(Duration.ofSeconds(5))
                .writeTimeout(Duration.ofSeconds(2))
                .build();
        client = new TestLLMClient(httpClient);
    }

    @AfterEach
    void tearDown() throws IOException {
        server.shutdown();
    }

    @Test
    void syncChatParsesChoicesContent() throws Exception {
        server.enqueue(jsonResponse("""
                {
                  "choices": [{
                    "message": {"role": "assistant", "content": "Hello World"}
                  }]
                }
                """));

        String answer = client.chat(simpleRequest("Hi"), target("sk-test"));

        assertThat(answer).isEqualTo("Hello World");
        var request = server.takeRequest();
        assertThat(request.getMethod()).isEqualTo("POST");
        assertThat(request.getHeader("Authorization")).isEqualTo("Bearer sk-test");
        String body = request.getBody().readUtf8();
        assertThat(body).contains("\"model\":\"test-model\"");
        assertThat(body).contains("\"stream\":false");
        assertThat(body).contains("\"content\":\"Hi\"");
    }

    @Test
    void syncChatWithTemperatureAndMaxTokens() throws Exception {
        server.enqueue(jsonResponse("""
                {"choices": [{"message": {"content": "OK"}}]}
                """));

        ChatRequest request = ChatRequest.builder()
                .messages(List.of(ChatMessage.user("test")))
                .temperature(0.7)
                .maxTokens(100)
                .build();
        client.chat(request, target("sk-test"));

        String body = server.takeRequest().getBody().readUtf8();
        assertThat(body).contains("\"temperature\":0.7");
        assertThat(body).contains("\"max_tokens\":100");
    }

    @Test
    void syncChatDisablesThinkingWhenRequestThinkingIsFalse() throws Exception {
        server.enqueue(jsonResponse("""
                {"choices": [{"message": {"content": "OK"}}]}
                """));

        ChatRequest request = ChatRequest.builder()
                .messages(List.of(ChatMessage.user("你好")))
                .thinking(false)
                .build();
        client.chat(request, target("sk-test"));

        String body = server.takeRequest().getBody().readUtf8();
        assertThat(body).contains("\"enable_thinking\":false");
    }

    @Test
    void syncChatHttpErrorThrowsRemoteException() {
        server.enqueue(new MockResponse().setResponseCode(500).setBody("server error"));

        assertThatThrownBy(() -> client.chat(simpleRequest("Hi"), target("sk-test")))
                .isInstanceOf(RemoteException.class)
                .hasMessageContaining("HTTP 500");
    }

    @Test
    void syncChatEmptyResponseThrowsRemoteException() {
        server.enqueue(new MockResponse().setResponseCode(200).setBody(""));

        assertThatThrownBy(() -> client.chat(simpleRequest("Hi"), target("sk-test")))
                .isInstanceOf(RemoteException.class)
                .hasMessageContaining("响应为空");
    }

    @Test
    void syncChatMissingApiKeyThrowsRemoteException() {
        assertThatThrownBy(() -> client.chat(simpleRequest("Hi"), target(null)))
                .isInstanceOf(RemoteException.class)
                .hasMessageContaining("API Key 不能为空");
    }

    @Test
    void streamChatDeliversContentChunks() throws Exception {
        String sseBody = """
                data: {"choices":[{"delta":{"content":"Hello "}}]}
                
                data: {"choices":[{"delta":{"content":"World"}}]}
                
                data: [DONE]
                
                """;
        server.enqueue(new MockResponse()
                .setResponseCode(200)
                .addHeader("Content-Type", "text/event-stream")
                .setBody(sseBody));

        List<String> contents = new ArrayList<>();
        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<Throwable> error = new AtomicReference<>();

        client.streamChat(simpleRequest("Hi"), new StreamCallback() {
            @Override
            public void onContent(String content) {
                contents.add(content);
            }

            @Override
            public void onThinking(String thinking) {
            }

            @Override
            public void onComplete() {
                latch.countDown();
            }

            @Override
            public void onError(Throwable throwable) {
                error.set(throwable);
                latch.countDown();
            }
        }, target("sk-test"));

        assertThat(latch.await(5, TimeUnit.SECONDS)).isTrue();
        assertThat(error.get()).isNull();
        assertThat(contents).containsExactly("Hello ", "World");
    }

    @Test
    void streamChatDeliversThinkingChunks() throws Exception {
        String sseBody = """
                data: {"choices":[{"delta":{"reasoning_content":"Thinking..."}}]}
                
                data: {"choices":[{"delta":{"content":"Answer"}}]}
                
                data: [DONE]
                
                """;
        server.enqueue(new MockResponse()
                .setResponseCode(200)
                .addHeader("Content-Type", "text/event-stream")
                .setBody(sseBody));

        List<String> thinkings = new ArrayList<>();
        List<String> contents = new ArrayList<>();
        CountDownLatch latch = new CountDownLatch(1);

        client.streamChat(simpleRequest("Hi"), new StreamCallback() {
            @Override
            public void onContent(String content) {
                contents.add(content);
            }

            @Override
            public void onThinking(String thinking) {
                thinkings.add(thinking);
            }

            @Override
            public void onComplete() {
                latch.countDown();
            }

            @Override
            public void onError(Throwable throwable) {
                latch.countDown();
            }
        }, target("sk-test"));

        assertThat(latch.await(5, TimeUnit.SECONDS)).isTrue();
        assertThat(thinkings).containsExactly("Thinking...");
        assertThat(contents).containsExactly("Answer");
    }

    @Test
    void streamChatHttpErrorCallsOnError() throws Exception {
        server.enqueue(new MockResponse()
                .setResponseCode(500)
                .setBody("server error"));

        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<Throwable> error = new AtomicReference<>();

        client.streamChat(simpleRequest("Hi"), new StreamCallback() {
            @Override
            public void onContent(String content) {
            }

            @Override
            public void onThinking(String thinking) {
            }

            @Override
            public void onComplete() {
                latch.countDown();
            }

            @Override
            public void onError(Throwable throwable) {
                error.set(throwable);
                latch.countDown();
            }
        }, target("sk-test"));

        assertThat(latch.await(5, TimeUnit.SECONDS)).isTrue();
        assertThat(error.get()).isInstanceOf(RemoteException.class);
        assertThat(error.get().getMessage()).contains("HTTP 500");
    }

    @Test
    void streamChatCancellationStopsProcessing() throws Exception {
        // Very large SSE response that would take a long time to process
        StringBuilder sseBody = new StringBuilder();
        for (int i = 0; i < 1000; i++) {
            sseBody.append("data: {\"choices\":[{\"delta\":{\"content\":\"chunk-").append(i).append("\"}}]}\n\n");
        }
        sseBody.append("data: [DONE]\n\n");

        server.enqueue(new MockResponse()
                .setResponseCode(200)
                .addHeader("Content-Type", "text/event-stream")
                .setBody(sseBody.toString())
                .throttleBody(50, 100, TimeUnit.MILLISECONDS));

        List<String> contents = new ArrayList<>();
        CountDownLatch started = new CountDownLatch(1);

        var handle = client.streamChat(simpleRequest("Hi"), new StreamCallback() {
            @Override
            public void onContent(String content) {
                contents.add(content);
                started.countDown();
            }

            @Override
            public void onThinking(String thinking) {
            }

            @Override
            public void onComplete() {
            }

            @Override
            public void onError(Throwable throwable) {
            }
        }, target("sk-test"));

        assertThat(started.await(5, TimeUnit.SECONDS)).isTrue();
        handle.cancel();

        // Give time for cancellation to propagate
        Thread.sleep(200);
        int sizeAfterCancel = contents.size();
        Thread.sleep(200);
        // After cancellation, no more chunks should be delivered
        assertThat(contents.size()).isEqualTo(sizeAfterCancel);
    }

    // ────────── helpers ──────────

    private ChatRequest simpleRequest(String prompt) {
        return ChatRequest.builder()
                .messages(List.of(ChatMessage.user(prompt)))
                .build();
    }

    private MockResponse jsonResponse(String body) {
        return new MockResponse()
                .setResponseCode(200)
                .addHeader("Content-Type", "application/json")
                .setBody(body);
    }

    private ChatTarget target(String apiKey) {
        return new ChatTarget(
                "test-provider",
                "test-model",
                server.url("/v1/chat/completions").toString(),
                apiKey
        );
    }

    private static class TestLLMClient extends AbstractOpenAIStyleLLMClient {

        TestLLMClient(OkHttpClient httpClient) {
            super(httpClient);
        }

        @Override
        public String provider() {
            return "test-provider";
        }
    }
}
