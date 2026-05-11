package edu.cqupt.devbrain.rag.core.websearch;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import edu.cqupt.devbrain.framework.errorcode.BaseErrorCode;
import edu.cqupt.devbrain.framework.exception.RemoteException;
import edu.cqupt.devbrain.rag.config.RAGChatProperties;
import okhttp3.HttpUrl;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Proxy;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * 基于 DuckDuckGo HTML 页面的免 Key 搜索实现。
 */
@Service
@ConditionalOnProperty(prefix = "rag.chat.web-search", name = "provider", havingValue = "duckduckgo", matchIfMissing = true)
public class DuckDuckGoWebSearchService implements WebSearchService {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final RAGChatProperties properties;
    private final OkHttpClient httpClient;

    @Autowired
    public DuckDuckGoWebSearchService(RAGChatProperties properties) {
        this(properties, buildHttpClient(properties));
    }

    DuckDuckGoWebSearchService(RAGChatProperties properties, OkHttpClient httpClient) {
        this.properties = properties;
        this.httpClient = httpClient;
    }

    @Override
    public List<WebSearchResult> search(String query, int limit) {
        if (!properties.getWebSearch().isEnabled() || !StringUtils.hasText(query)) {
            return List.of();
        }
        int maxResults = normalizeLimit(limit);
        List<WebSearchResult> weatherResults = searchWeatherIfNeeded(query);
        if (!weatherResults.isEmpty()) {
            return weatherResults.stream().limit(maxResults).toList();
        }
        HttpUrl baseUrl = HttpUrl.parse(properties.getWebSearch().getEndpoint());
        if (baseUrl == null) {
            throw new RemoteException("联网搜索地址配置无效：" + properties.getWebSearch().getEndpoint());
        }
        HttpUrl url = baseUrl.newBuilder()
                .addQueryParameter("q", query)
                .build();
        Request request = new Request.Builder()
                .url(url)
                .header("User-Agent", "DevBrainBot/1.0")
                .get()
                .build();
        try (Response response = httpClient.newCall(request).execute()) {
            ResponseBody body = response.body();
            String html = body == null ? "" : body.string();
            if (!response.isSuccessful()) {
                throw new RemoteException("联网搜索请求失败，HTTP " + response.code());
            }
            return parseResults(html, maxResults);
        } catch (IOException ex) {
            throw new RemoteException("联网搜索网络请求失败（代理：" + proxyDescription() + "）：" + ex.getClass().getSimpleName()
                    + (StringUtils.hasText(ex.getMessage()) ? " - " + ex.getMessage() : ""),
                    ex,
                    BaseErrorCode.REMOTE_ERROR);
        }
    }

    public String proxyDescription() {
        return proxyDescription(properties);
    }

    private List<WebSearchResult> parseResults(String html, int limit) {
        if (!StringUtils.hasText(html)) {
            return List.of();
        }
        Document document = Jsoup.parse(html);
        List<WebSearchResult> results = new ArrayList<>();
        Set<String> seenUrls = new LinkedHashSet<>();
        for (Element result : document.select(".result")) {
            Element titleElement = result.selectFirst(".result__a");
            if (titleElement == null) {
                continue;
            }
            String title = titleElement.text().strip();
            String url = normalizeDuckDuckGoUrl(titleElement.attr("href"));
            String snippet = snippetOf(result);
            if (!StringUtils.hasText(title) || !StringUtils.hasText(url) || !seenUrls.add(url)) {
                continue;
            }
            results.add(new WebSearchResult(title, url, snippet));
            if (results.size() >= limit) {
                break;
            }
        }
        return results;
    }

    private List<WebSearchResult> searchWeatherIfNeeded(String query) {
        if (!isWeatherQuery(query)) {
            return List.of();
        }
        WeatherLocation location = weatherLocation(query);
        if (location == null) {
            return List.of();
        }
        HttpUrl baseUrl = HttpUrl.parse(properties.getWebSearch().getWeatherEndpoint());
        if (baseUrl == null) {
            return List.of();
        }
        HttpUrl url = baseUrl.newBuilder()
                .addQueryParameter("latitude", location.latitude())
                .addQueryParameter("longitude", location.longitude())
                .addQueryParameter("current",
                        "temperature_2m,relative_humidity_2m,apparent_temperature,precipitation,weather_code,wind_speed_10m")
                .addQueryParameter("daily",
                        "temperature_2m_max,temperature_2m_min,precipitation_probability_max")
                .addQueryParameter("timezone", "Asia/Shanghai")
                .build();
        Request request = new Request.Builder()
                .url(url)
                .header("User-Agent", "DevBrainBot/1.0")
                .get()
                .build();
        try (Response response = httpClient.newCall(request).execute()) {
            ResponseBody body = response.body();
            String json = body == null ? "" : body.string();
            if (!response.isSuccessful() || !StringUtils.hasText(json)) {
                return List.of();
            }
            return weatherResult(location, OBJECT_MAPPER.readTree(json));
        } catch (IOException | RuntimeException ex) {
            return List.of();
        }
    }

    private boolean isWeatherQuery(String query) {
        String normalized = query.toLowerCase(Locale.ROOT);
        return normalized.contains("天气")
                || normalized.contains("气温")
                || normalized.contains("下雨")
                || normalized.contains("降雨")
                || normalized.contains("weather");
    }

    private WeatherLocation weatherLocation(String query) {
        if (query.contains("南岸")) {
            return new WeatherLocation("重庆南岸区", "29.523", "106.563");
        }
        if (query.contains("重庆")) {
            return new WeatherLocation("重庆", "29.563", "106.552");
        }
        return null;
    }

    private List<WebSearchResult> weatherResult(WeatherLocation location, JsonNode root) {
        JsonNode current = root.path("current");
        if (current.isMissingNode() || current.isNull()) {
            return List.of();
        }
        StringBuilder snippet = new StringBuilder(location.name()).append("当前天气：")
                .append(weatherText(current.path("weather_code").asInt(-1)))
                .append("，气温 ").append(formatNumber(current.path("temperature_2m"))).append("°C")
                .append("，体感 ").append(formatNumber(current.path("apparent_temperature"))).append("°C")
                .append("，相对湿度 ").append(formatInteger(current.path("relative_humidity_2m"))).append("%")
                .append("，风速 ").append(formatNumber(current.path("wind_speed_10m"))).append(" km/h")
                .append("，降水 ").append(formatNumber(current.path("precipitation"))).append(" mm");
        JsonNode daily = root.path("daily");
        if (!daily.isMissingNode() && !daily.isNull()) {
            snippet.append("。今日最高 ").append(formatNumber(first(daily.path("temperature_2m_max")))).append("°C")
                    .append("，最低 ").append(formatNumber(first(daily.path("temperature_2m_min")))).append("°C")
                    .append("，降水概率 ").append(formatInteger(first(daily.path("precipitation_probability_max")))).append("%");
        }
        snippet.append("。数据来源 Open-Meteo。");
        return List.of(new WebSearchResult(location.name() + "天气", "https://open-meteo.com/", snippet.toString()));
    }

    private JsonNode first(JsonNode array) {
        return array.isArray() && !array.isEmpty() ? array.get(0) : array;
    }

    private String formatNumber(JsonNode node) {
        if (node == null || node.isMissingNode() || node.isNull() || !node.isNumber()) {
            return "未知";
        }
        return String.format(Locale.ROOT, "%.1f", node.asDouble());
    }

    private String formatInteger(JsonNode node) {
        if (node == null || node.isMissingNode() || node.isNull() || !node.isNumber()) {
            return "未知";
        }
        return String.valueOf(Math.round(node.asDouble()));
    }

    private String weatherText(int code) {
        return switch (code) {
            case 0 -> "晴";
            case 1, 2 -> "大部晴朗";
            case 3 -> "多云";
            case 45, 48 -> "有雾";
            case 51, 53, 55 -> "毛毛雨";
            case 56, 57 -> "冻毛毛雨";
            case 61, 63, 65 -> "降雨";
            case 66, 67 -> "冻雨";
            case 71, 73, 75 -> "降雪";
            case 77 -> "雪粒";
            case 80, 81, 82 -> "阵雨";
            case 85, 86 -> "阵雪";
            case 95 -> "雷暴";
            case 96, 99 -> "雷暴伴冰雹";
            default -> "天气代码 " + code;
        };
    }

    private String snippetOf(Element result) {
        Element snippet = result.selectFirst(".result__snippet");
        return snippet == null ? "" : snippet.text().strip();
    }

    private String normalizeDuckDuckGoUrl(String href) {
        if (!StringUtils.hasText(href)) {
            return "";
        }
        if (href.startsWith("//")) {
            return "https:" + href;
        }
        if (href.startsWith("http://") || href.startsWith("https://")) {
            return href;
        }
        int index = href.indexOf("uddg=");
        if (index >= 0) {
            String encoded = href.substring(index + "uddg=".length());
            int end = encoded.indexOf('&');
            if (end >= 0) {
                encoded = encoded.substring(0, end);
            }
            return URLDecoder.decode(encoded, StandardCharsets.UTF_8);
        }
        return href;
    }

    private int normalizeLimit(int limit) {
        int configured = properties.getWebSearch().getMaxResults();
        int fallback = configured <= 0 ? 5 : configured;
        return Math.max(1, Math.min(limit <= 0 ? fallback : limit, fallback));
    }

    static OkHttpClient buildHttpClient(RAGChatProperties properties) {
        configureSystemProxies(properties);
        int timeoutMillis = properties.getWebSearch().getTimeoutMillis();
        OkHttpClient.Builder builder = new OkHttpClient.Builder()
                .connectTimeout(Duration.ofMillis(timeoutMillis))
                .readTimeout(Duration.ofMillis(timeoutMillis))
                .writeTimeout(Duration.ofMillis(timeoutMillis));
        Proxy explicitProxy = explicitProxy(properties);
        if (explicitProxy != null) {
            builder.proxy(explicitProxy);
        }
        return builder.build();
    }

    private static void configureSystemProxies(RAGChatProperties properties) {
        if (properties != null
                && properties.getWebSearch() != null
                && properties.getWebSearch().isUseSystemProxies()) {
            System.setProperty("java.net.useSystemProxies", "true");
        }
    }

    private static Proxy explicitProxy(RAGChatProperties properties) {
        if (properties == null || properties.getWebSearch() == null) {
            return null;
        }
        String proxyUrl = properties.getWebSearch().getProxyUrl();
        if (!StringUtils.hasText(proxyUrl)) {
            proxyUrl = firstText(
                    System.getenv("RAG_WEB_SEARCH_PROXY"),
                    System.getenv("HTTPS_PROXY"),
                    System.getenv("HTTP_PROXY"),
                    windowsUserProxy()
            );
        }
        if (!StringUtils.hasText(proxyUrl)) {
            return null;
        }
        try {
            URI uri = new URI(proxyUrl.contains("://") ? proxyUrl : "http://" + proxyUrl);
            String host = uri.getHost();
            int port = uri.getPort();
            if (!StringUtils.hasText(host) || port <= 0) {
                throw new RemoteException("联网搜索代理地址配置无效：" + proxyUrl);
            }
            String scheme = uri.getScheme();
            Proxy.Type type = "socks".equalsIgnoreCase(scheme) || "socks5".equalsIgnoreCase(scheme)
                    ? Proxy.Type.SOCKS
                    : Proxy.Type.HTTP;
            return new Proxy(type, new InetSocketAddress(host, port));
        } catch (URISyntaxException ex) {
            throw new RemoteException("联网搜索代理地址配置无效：" + proxyUrl, ex, BaseErrorCode.REMOTE_ERROR);
        }
    }

    static String proxyDescription(RAGChatProperties properties) {
        Proxy proxy = explicitProxy(properties);
        if (proxy == null) {
            return "system/default";
        }
        if (!(proxy.address() instanceof InetSocketAddress address)) {
            return proxy.type().name();
        }
        return proxy.type().name() + " @ " + address.getHostString() + ":" + address.getPort();
    }

    private static String windowsUserProxy() {
        if (!System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win")) {
            return null;
        }
        try {
            Process process = new ProcessBuilder(
                    "reg",
                    "query",
                    "HKCU\\Software\\Microsoft\\Windows\\CurrentVersion\\Internet Settings",
                    "/v",
                    "ProxyEnable"
            ).redirectErrorStream(true).start();
            String enableOutput = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            process.waitFor();
            Process serverProcess = new ProcessBuilder(
                    "reg",
                    "query",
                    "HKCU\\Software\\Microsoft\\Windows\\CurrentVersion\\Internet Settings",
                    "/v",
                    "ProxyServer"
            ).redirectErrorStream(true).start();
            String serverOutput = new String(serverProcess.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            serverProcess.waitFor();
            return windowsUserProxyFromRegistryOutput(enableOutput + System.lineSeparator() + serverOutput);
        } catch (IOException | InterruptedException | RuntimeException ex) {
            if (ex instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            return null;
        }
    }

    static String windowsUserProxyFromRegistryOutput(String output) {
        if (!StringUtils.hasText(output) || !output.toLowerCase(Locale.ROOT).contains("proxyenable")) {
            return null;
        }
        String normalized = output.toLowerCase(Locale.ROOT);
        if (!normalized.contains("0x1") && !normalized.matches("(?s).*proxyenable\\s+reg_dword\\s+1.*")) {
            return null;
        }
        for (String line : output.split("\\R")) {
            String trimmed = line.trim();
            if (trimmed.toLowerCase(Locale.ROOT).startsWith("proxyserver")) {
                String[] parts = trimmed.split("\\s+", 3);
                if (parts.length >= 3) {
                    return normalizeWindowsProxyServer(parts[2]);
                }
            }
        }
        return null;
    }

    private static String normalizeWindowsProxyServer(String proxyServer) {
        String first = proxyServer.split(";", 2)[0].trim();
        int equalsIndex = first.indexOf('=');
        if (equalsIndex >= 0) {
            first = first.substring(equalsIndex + 1).trim();
        }
        return StringUtils.hasText(first) ? first : null;
    }

    private static String firstText(String... values) {
        if (values == null) {
            return null;
        }
        for (String value : values) {
            if (StringUtils.hasText(value)) {
                return value;
            }
        }
        return null;
    }

    private record WeatherLocation(String name, String latitude, String longitude) {
    }
}
