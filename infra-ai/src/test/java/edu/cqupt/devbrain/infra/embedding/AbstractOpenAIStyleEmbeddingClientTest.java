package edu.cqupt.devbrain.infra.embedding;

import edu.cqupt.devbrain.framework.exception.RemoteException;
import okhttp3.OkHttpClient;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AbstractOpenAIStyleEmbeddingClientTest {

    private MockWebServer server;
    private TestEmbeddingClient client;

    @BeforeEach
    void setUp() throws IOException {
        server = new MockWebServer();
        server.start();
        OkHttpClient httpClient = new OkHttpClient.Builder()
                .connectTimeout(Duration.ofSeconds(2))
                .readTimeout(Duration.ofSeconds(2))
                .writeTimeout(Duration.ofSeconds(2))
                .build();
        client = new TestEmbeddingClient(httpClient);
    }

    @AfterEach
    void tearDown() throws IOException {
        server.shutdown();
    }

    @Test
    void embedBatchSendsOpenAIStyleRequestAndParsesEmbeddings() throws Exception {
        server.enqueue(jsonResponse("""
                {
                  "data": [
                    {"index": 0, "embedding": [0.1, 0.2]},
                    {"index": 1, "embedding": [0.3, 0.4]}
                  ]
                }
                """));

        List<List<Float>> embeddings = client.embedBatch(List.of("text1", "text2"), target("sk-test"));

        assertThat(embeddings).containsExactly(List.of(0.1f, 0.2f), List.of(0.3f, 0.4f));
        var request = server.takeRequest();
        assertThat(request.getMethod()).isEqualTo("POST");
        assertThat(request.getHeader("Authorization")).isEqualTo("Bearer sk-test");
        assertThat(request.getHeader("Content-Type")).contains("application/json");
        assertThat(request.getBody().readUtf8()).contains(
                "\"model\":\"test-model\"",
                "\"input\":[\"text1\",\"text2\"]",
                "\"encoding_format\":\"float\""
        );
    }

    @Test
    void embedReturnsFirstVectorFromBatchCall() {
        server.enqueue(jsonResponse("""
                {"data": [{"index": 0, "embedding": [0.5, 0.6]}]}
                """));

        List<Float> embedding = client.embed("text", target("sk-test"));

        assertThat(embedding).containsExactly(0.5f, 0.6f);
    }

    @Test
    void embedBatchSplitsRequestsWhenMaxBatchSizeIsConfigured() throws Exception {
        client.setMaxBatchSize(2);
        server.enqueue(jsonResponse("""
                {"data": [{"index": 0, "embedding": [1.0]}, {"index": 1, "embedding": [2.0]}]}
                """));
        server.enqueue(jsonResponse("""
                {"data": [{"index": 0, "embedding": [3.0]}, {"index": 1, "embedding": [4.0]}]}
                """));
        server.enqueue(jsonResponse("""
                {"data": [{"index": 0, "embedding": [5.0]}]}
                """));

        List<List<Float>> embeddings = client.embedBatch(List.of("a", "b", "c", "d", "e"), target("sk-test"));

        assertThat(embeddings).containsExactly(List.of(1.0f), List.of(2.0f), List.of(3.0f), List.of(4.0f), List.of(5.0f));
        assertThat(server.getRequestCount()).isEqualTo(3);
    }

    @Test
    void localClientCanSkipAuthorizationHeader() throws Exception {
        client.setRequiresApiKey(false);
        server.enqueue(jsonResponse("""
                {"data": [{"index": 0, "embedding": [0.1]}]}
                """));

        client.embedBatch(List.of("text"), target(null));

        assertThat(server.takeRequest().getHeader("Authorization")).isNull();
    }

    @Test
    void missingApiKeyThrowsRemoteExceptionWhenRequired() {
        assertThatThrownBy(() -> client.embedBatch(List.of("text"), target(null)))
                .isInstanceOf(RemoteException.class)
                .hasMessageContaining("API Key 不能为空");
    }

    @Test
    void httpErrorIsWrappedAsRemoteException() {
        server.enqueue(new MockResponse()
                .setResponseCode(500)
                .setBody("server error"));

        assertThatThrownBy(() -> client.embedBatch(List.of("text"), target("sk-test")))
                .isInstanceOf(RemoteException.class)
                .hasMessageContaining("HTTP 500");
    }

    @Test
    void emptyBodyIsWrappedAsRemoteException() {
        server.enqueue(new MockResponse()
                .setResponseCode(200)
                .setBody(""));

        assertThatThrownBy(() -> client.embedBatch(List.of("text"), target("sk-test")))
                .isInstanceOf(RemoteException.class)
                .hasMessageContaining("响应为空");
    }

    @Test
    void invalidJsonIsWrappedAsRemoteException() {
        server.enqueue(new MockResponse()
                .setResponseCode(200)
                .setBody("{"));

        assertThatThrownBy(() -> client.embedBatch(List.of("text"), target("sk-test")))
                .isInstanceOf(RemoteException.class)
                .hasMessageContaining("响应解析失败");
    }

    @Test
    void mismatchedEmbeddingCountIsWrappedAsRemoteException() {
        server.enqueue(jsonResponse("""
                {"data": [{"index": 0, "embedding": [0.1]}]}
                """));

        assertThatThrownBy(() -> client.embedBatch(List.of("a", "b"), target("sk-test")))
                .isInstanceOf(RemoteException.class)
                .hasMessageContaining("向量数量不匹配");
    }

    @Test
    void invalidEmbeddingArrayIsWrappedAsRemoteException() {
        server.enqueue(jsonResponse("""
                {"data": [{"index": 0, "embedding": "bad"}]}
                """));

        assertThatThrownBy(() -> client.embedBatch(List.of("text"), target("sk-test")))
                .isInstanceOf(RemoteException.class)
                .hasMessageContaining("embedding 格式错误");
    }

    private MockResponse jsonResponse(String body) {
        return new MockResponse()
                .setResponseCode(200)
                .addHeader("Content-Type", "application/json")
                .setBody(body);
    }

    private ModelTarget target(String apiKey) {
        return new ModelTarget(
                "test-provider",
                "test-model",
                server.url("/v1/embeddings").toString(),
                apiKey,
                2
        );
    }

    private static class TestEmbeddingClient extends AbstractOpenAIStyleEmbeddingClient {

        private boolean requiresApiKey = true;
        private int maxBatchSize;

        TestEmbeddingClient(OkHttpClient httpClient) {
            super(httpClient);
        }

        @Override
        public String provider() {
            return "test-provider";
        }

        @Override
        protected boolean requiresApiKey() {
            return requiresApiKey;
        }

        @Override
        protected int maxBatchSize() {
            return maxBatchSize;
        }

        void setRequiresApiKey(boolean requiresApiKey) {
            this.requiresApiKey = requiresApiKey;
        }

        void setMaxBatchSize(int maxBatchSize) {
            this.maxBatchSize = maxBatchSize;
        }
    }
}
