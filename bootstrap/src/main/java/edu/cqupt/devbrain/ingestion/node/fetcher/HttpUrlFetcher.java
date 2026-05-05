package edu.cqupt.devbrain.ingestion.node.fetcher;

import edu.cqupt.devbrain.ingestion.domain.SourceType;
import edu.cqupt.devbrain.ingestion.domain.context.DocumentSource;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.util.Map;

/**
 * HTTP URL 文档获取策略，通过 GET 请求拉取远程文档字节。
 */
@Component
public class HttpUrlFetcher implements DocumentFetcher {

    /**
     * HTTP 客户端可复用连接池，组件级单例即可。
     */
    private final OkHttpClient okHttpClient;

    /**
     * Spring 使用的默认构造函数。
     */
    public HttpUrlFetcher() {
        this(new OkHttpClient());
    }

    /**
     * 测试或定制场景可注入自定义 OkHttpClient。
     */
    public HttpUrlFetcher(OkHttpClient okHttpClient) {
        this.okHttpClient = okHttpClient;
    }

    /**
     * 发起 GET 请求并读取响应体字节，非 2xx 响应会作为获取失败处理。
     */
    @Override
    public byte[] fetch(DocumentSource source) throws Exception {
        if (source == null || !StringUtils.hasText(source.getLocation())) {
            throw new IllegalArgumentException("URL 来源地址不能为空");
        }

        Request.Builder requestBuilder = new Request.Builder()
                .url(source.getLocation())
                .get();
        applyCredentialHeaders(requestBuilder, source.getCredentials());

        try (Response response = okHttpClient.newCall(requestBuilder.build()).execute()) {
            if (!response.isSuccessful()) {
                throw new IOException("HTTP 获取失败，状态码: " + response.code());
            }
            ResponseBody body = response.body();
            if (body == null) {
                throw new IOException("HTTP 响应体为空");
            }
            return body.bytes();
        }
    }

    /**
     * 当前策略处理 URL 来源。
     */
    @Override
    public SourceType getSupportedType() {
        return SourceType.URL;
    }

    /**
     * 将 credentials 中的认证信息转换为请求头。
     *
     * <p>支持两类写法：header:Name=Value 用于任意头；Authorization、Cookie、X-* 用于常见认证头。</p>
     */
    private void applyCredentialHeaders(Request.Builder requestBuilder, Map<String, String> credentials) {
        if (credentials == null || credentials.isEmpty()) {
            return;
        }
        for (Map.Entry<String, String> entry : credentials.entrySet()) {
            String key = entry.getKey();
            String value = entry.getValue();
            if (!StringUtils.hasText(key) || !StringUtils.hasText(value)) {
                continue;
            }
            if (key.startsWith("header:")) {
                String headerName = key.substring("header:".length()).trim();
                if (StringUtils.hasText(headerName)) {
                    requestBuilder.header(headerName, value);
                }
                continue;
            }
            if ("authorization".equalsIgnoreCase(key)) {
                requestBuilder.header("Authorization", value);
                continue;
            }
            if ("cookie".equalsIgnoreCase(key)) {
                requestBuilder.header("Cookie", value);
                continue;
            }
            if (key.regionMatches(true, 0, "x-", 0, 2)) {
                requestBuilder.header(key, value);
            }
        }
    }
}
