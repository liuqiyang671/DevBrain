package edu.cqupt.devbrain.infra.embedding;

import okhttp3.OkHttpClient;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.time.Duration;
import java.util.List;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;

class ProviderEmbeddingClientTest {

    private MockWebServer server;
    private OkHttpClient httpClient;

    @BeforeEach
    void setUp() throws IOException {
        server = new MockWebServer();
        server.start();
        httpClient = new OkHttpClient.Builder()
                .connectTimeout(Duration.ofSeconds(2))
                .readTimeout(Duration.ofSeconds(2))
                .writeTimeout(Duration.ofSeconds(2))
                .build();
    }

    @AfterEach
    void tearDown() throws IOException {
        server.shutdown();
    }

    @Test
    void siliconFlowClientUsesApiKeyAndSplitsBatchAtThirtyTwo() throws Exception {
        SiliconFlowEmbeddingClient client = new SiliconFlowEmbeddingClient(httpClient);
        server.enqueue(responseWithEmbeddings(32));
        server.enqueue(responseWithEmbeddings(1));

        List<String> texts = IntStream.range(0, 33)
                .mapToObj(index -> "text-" + index)
                .toList();
        List<List<Float>> embeddings = client.embedBatch(texts, target("siliconflow", "sk-test"));

        assertThat(client.provider()).isEqualTo("siliconflow");
        assertThat(SiliconFlowEmbeddingClient.class).hasAnnotation(Component.class);
        assertThat(embeddings).hasSize(33);
        assertThat(server.getRequestCount()).isEqualTo(2);

        var firstRequest = server.takeRequest();
        assertThat(firstRequest.getHeader("Authorization")).isEqualTo("Bearer sk-test");
        assertThat(firstRequest.getBody().readUtf8()).contains(
                "\"model\":\"test-model\"",
                "\"encoding_format\":\"float\"",
                "\"text-31\""
        );
        assertThat(server.takeRequest().getBody().readUtf8()).contains("\"text-32\"");
    }

    @Test
    void ollamaClientSkipsApiKeyAndEncodingFormatAndRequestsConfiguredDimensions() throws Exception {
        OllamaEmbeddingClient client = new OllamaEmbeddingClient(httpClient);
        server.enqueue(responseWithEmbeddings(1));

        List<List<Float>> embeddings = client.embedBatch(List.of("local-text"), target("ollama", null));

        assertThat(client.provider()).isEqualTo("ollama");
        assertThat(OllamaEmbeddingClient.class).hasAnnotation(Component.class);
        assertThat(embeddings).containsExactly(List.of(0.0f));

        var request = server.takeRequest();
        String requestBody = request.getBody().readUtf8();
        assertThat(request.getHeader("Authorization")).isNull();
        assertThat(requestBody).contains(
                "\"model\":\"test-model\"",
                "\"input\":[\"local-text\"]",
                "\"dimensions\":1536"
        );
        assertThat(requestBody).doesNotContain("encoding_format");
    }

    private MockResponse responseWithEmbeddings(int count) {
        String data = IntStream.range(0, count)
                .mapToObj(index -> "{\"index\":" + index + ",\"embedding\":[" + index + ".0]}")
                .reduce((left, right) -> left + "," + right)
                .orElse("");
        return new MockResponse()
                .setResponseCode(200)
                .addHeader("Content-Type", "application/json")
                .setBody("{\"data\":[" + data + "]}");
    }

    private ModelTarget target(String provider, String apiKey) {
        return new ModelTarget(
                provider,
                "test-model",
                server.url("/v1/embeddings").toString(),
                apiKey,
                1536
        );
    }
}
