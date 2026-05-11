package edu.cqupt.devbrain.rag.core.websearch;

import edu.cqupt.devbrain.rag.config.RAGChatProperties;
import okhttp3.OkHttpClient;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.Proxy;
import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class DuckDuckGoWebSearchServiceTest {

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
    void searchShouldParseDuckDuckGoHtmlResults() throws Exception {
        server.enqueue(new MockResponse()
                .setResponseCode(200)
                .addHeader("Content-Type", "text/html; charset=utf-8")
                .setBody("""
                        <html>
                          <body>
                            <div class="result">
                              <a class="result__a" href="/l/?uddg=https%3A%2F%2Fexample.com%2Fweather">重庆天气</a>
                              <a class="result__snippet">重庆今日天气信息。</a>
                            </div>
                            <div class="result">
                              <a class="result__a" href="https://news.example.com/item">新闻标题</a>
                              <div class="result__snippet">新闻摘要内容。</div>
                            </div>
                          </body>
                        </html>
                        """));
        RAGChatProperties properties = new RAGChatProperties();
        properties.getWebSearch().setEnabled(true);
        properties.getWebSearch().setEndpoint(server.url("/html/").toString());
        properties.getWebSearch().setMaxResults(5);
        DuckDuckGoWebSearchService service = new DuckDuckGoWebSearchService(
                properties,
                new OkHttpClient.Builder()
                        .proxy(Proxy.NO_PROXY)
                        .connectTimeout(Duration.ofSeconds(2))
                        .readTimeout(Duration.ofSeconds(2))
                        .build()
        );

        List<WebSearchResult> results = service.search("重庆新闻", 5);

        assertThat(results).hasSize(2);
        assertThat(results.get(0).title()).isEqualTo("重庆天气");
        assertThat(results.get(0).url()).isEqualTo("https://example.com/weather");
        assertThat(results.get(0).snippet()).isEqualTo("重庆今日天气信息。");
        assertThat(results.get(1).title()).isEqualTo("新闻标题");
        assertThat(results.get(1).url()).isEqualTo("https://news.example.com/item");
        assertThat(server.takeRequest().getRequestUrl().queryParameter("q")).isEqualTo("重庆新闻");
    }

    @Test
    void searchShouldUseOpenMeteoForWeatherQuery() throws Exception {
        server.enqueue(new MockResponse()
                .setResponseCode(200)
                .addHeader("Content-Type", "application/json; charset=utf-8")
                .setBody("""
                        {
                          "current": {
                            "time": "2026-05-10T21:45",
                            "temperature_2m": 23.5,
                            "relative_humidity_2m": 68,
                            "apparent_temperature": 25.1,
                            "precipitation": 0.2,
                            "weather_code": 3,
                            "wind_speed_10m": 7.4
                          },
                          "daily": {
                            "temperature_2m_max": [28.2],
                            "temperature_2m_min": [20.4],
                            "precipitation_probability_max": [70]
                          }
                        }
                        """));
        RAGChatProperties properties = new RAGChatProperties();
        properties.getWebSearch().setEnabled(true);
        properties.getWebSearch().setEndpoint(server.url("/html/").toString());
        properties.getWebSearch().setWeatherEndpoint(server.url("/v1/forecast").toString());
        DuckDuckGoWebSearchService service = new DuckDuckGoWebSearchService(
                properties,
                new OkHttpClient.Builder()
                        .proxy(Proxy.NO_PROXY)
                        .connectTimeout(Duration.ofSeconds(2))
                        .readTimeout(Duration.ofSeconds(2))
                        .build()
        );

        List<WebSearchResult> results = service.search("你好，今天重庆南岸区的天气怎么样", 5);

        assertThat(results).hasSize(1);
        assertThat(results.get(0).title()).isEqualTo("重庆南岸区天气");
        assertThat(results.get(0).url()).isEqualTo("https://open-meteo.com/");
        assertThat(results.get(0).snippet())
                .contains("气温 23.5°C")
                .contains("体感 25.1°C")
                .contains("相对湿度 68%")
                .contains("今日最高 28.2°C")
                .contains("最低 20.4°C")
                .contains("降水概率 70%");
        RecordedRequest request = server.takeRequest();
        assertThat(request.getPath()).startsWith("/v1/forecast?");
        assertThat(request.getRequestUrl().queryParameter("latitude")).isEqualTo("29.523");
        assertThat(request.getRequestUrl().queryParameter("longitude")).isEqualTo("106.563");
        assertThat(server.getRequestCount()).isEqualTo(1);
    }

    @Test
    void buildHttpClientShouldUseConfiguredProxyUrl() {
        RAGChatProperties properties = new RAGChatProperties();
        properties.getWebSearch().setUseSystemProxies(false);
        properties.getWebSearch().setProxyUrl("http://127.0.0.1:7892");

        OkHttpClient client = DuckDuckGoWebSearchService.buildHttpClient(properties);

        assertThat(client.proxy()).isNotNull();
        assertThat(client.proxy().type()).isEqualTo(Proxy.Type.HTTP);
        assertThat(client.proxy().address().toString()).contains("127.0.0.1");
        assertThat(client.proxy().address().toString()).contains("7892");
    }

    @Test
    void proxyDescriptionShouldExposeConfiguredProxy() {
        RAGChatProperties properties = new RAGChatProperties();
        properties.getWebSearch().setUseSystemProxies(false);
        properties.getWebSearch().setProxyUrl("http://127.0.0.1:7892");

        assertThat(DuckDuckGoWebSearchService.proxyDescription(properties))
                .isEqualTo("HTTP @ 127.0.0.1:7892");
    }

    @Test
    void windowsUserProxyFromRegistryOutputShouldParseEnabledProxyServer() {
        String output = """
                HKEY_CURRENT_USER\\Software\\Microsoft\\Windows\\CurrentVersion\\Internet Settings
                    ProxyEnable    REG_DWORD    0x1
                    ProxyServer    REG_SZ       127.0.0.1:7892
                """;

        assertThat(DuckDuckGoWebSearchService.windowsUserProxyFromRegistryOutput(output))
                .isEqualTo("127.0.0.1:7892");
    }

    @Test
    void windowsUserProxyFromRegistryOutputShouldParseSchemeSpecificProxyServer() {
        String output = """
                HKEY_CURRENT_USER\\Software\\Microsoft\\Windows\\CurrentVersion\\Internet Settings
                    ProxyEnable    REG_DWORD    0x1
                    ProxyServer    REG_SZ       http=127.0.0.1:7892;https=127.0.0.1:7892
                """;

        assertThat(DuckDuckGoWebSearchService.windowsUserProxyFromRegistryOutput(output))
                .isEqualTo("127.0.0.1:7892");
    }
}
