package edu.cqupt.devbrain.sync.adapter;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import edu.cqupt.devbrain.framework.exception.ClientException;
import edu.cqupt.devbrain.sync.config.FeishuProperties;
import lombok.extern.slf4j.Slf4j;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicReference;

/**
 * 飞书文档来源适配器，支持 docx、wiki、sheet 三种类型的文档内容拉取。
 */
@Slf4j
@Component
public class FeishuDocumentAdapter implements DocumentSourceAdapter {

    private static final MediaType JSON_MEDIA = MediaType.parse("application/json; charset=utf-8");

    private final FeishuProperties properties;
    private final OkHttpClient httpClient;

    private final AtomicReference<CachedToken> cachedToken = new AtomicReference<>();

    /**
     * 构造方法，注入飞书配置和 HTTP 客户端。
     */
    public FeishuDocumentAdapter(FeishuProperties properties, OkHttpClient httpClient) {
        this.properties = properties;
        this.httpClient = httpClient;
    }

    /**
     * 返回来源类型标识 {@code feishu}。
     */
    @Override
    public String sourceType() {
        return "feishu";
    }

    /**
     * 根据飞书文档地址拉取内容，支持 docx、wiki、sheet 三种格式。
     */
    @Override
    public FetchedContent fetchContent(String sourceLocation) throws Exception {
        String token = getTenantAccessToken();
        String[] parts = sourceLocation.split(":", 2);
        if (parts.length != 2) {
            throw new ClientException("飞书文档地址格式错误，应为 docx:{id} 或 wiki:{token} 或 sheet:{token}");
        }
        String type = parts[0];
        String id = parts[1];

        return switch (type) {
            case "docx" -> fetchDocx(token, id);
            case "wiki" -> fetchWiki(token, id);
            case "sheet" -> fetchSheet(token, id);
            default -> throw new ClientException("不支持的飞书文档类型: " + type);
        };
    }

    /**
     * 拉取飞书 docx 文档的纯文本内容。
     */
    private FetchedContent fetchDocx(String token, String documentId) throws IOException {
        String url = properties.getDocxContentUrl() + "/" + documentId + "/raw_content";
        String body = doGet(url, token);
        JsonObject json = JsonParser.parseString(body).getAsJsonObject();
        checkFeishuError(json);
        JsonObject data = json.getAsJsonObject("data");
        String content = data.get("content").getAsString();
        return new FetchedContent(content, "text/plain", null);
    }

    /**
     * 拉取飞书 wiki 节点，根据节点实际类型（docx/sheet）委托到对应方法。
     */
    private FetchedContent fetchWiki(String token, String nodeToken) throws Exception {
        String url = properties.getWikiNodeUrl() + "?token=" + nodeToken;
        String body = doGet(url, token);
        JsonObject json = JsonParser.parseString(body).getAsJsonObject();
        checkFeishuError(json);
        JsonObject node = json.getAsJsonObject("data").getAsJsonObject("node");
        String objType = node.get("obj_type").getAsString();
        String objToken = node.get("obj_token").getAsString();
        String title = node.has("title") ? node.get("title").getAsString() : null;

        FetchedContent content = switch (objType) {
            case "docx" -> fetchDocx(token, objToken);
            case "sheet" -> fetchSheet(token, objToken);
            default -> throw new ClientException("不支持的 wiki 节点类型: " + objType);
        };
        return new FetchedContent(content.text(), content.contentType(), title);
    }

    /**
     * 拉取飞书电子表格内容并转换为文本格式。
     */
    private FetchedContent fetchSheet(String token, String spreadsheetToken) throws IOException {
        String url = properties.getSheetValuesUrl() + "/" + spreadsheetToken + "/values";
        String body = doGet(url, token);
        JsonObject json = JsonParser.parseString(body).getAsJsonObject();
        checkFeishuError(json);
        JsonObject data = json.getAsJsonObject("data");
        JsonArray sheets = data.getAsJsonArray("sheets");

        StringBuilder sb = new StringBuilder();
        for (JsonElement sheetElem : sheets) {
            JsonObject sheet = sheetElem.getAsJsonObject();
            String sheetName = sheet.get("title").getAsString();
            sb.append("## ").append(sheetName).append("\n\n");
            if (sheet.has("cells")) {
                JsonArray rows = sheet.getAsJsonArray("cells");
                for (JsonElement rowElem : rows) {
                    JsonArray row = rowElem.getAsJsonArray();
                    for (int i = 0; i < row.size(); i++) {
                        if (i > 0) sb.append("\t");
                        sb.append(row.get(i).getAsString());
                    }
                    sb.append("\n");
                }
            }
            sb.append("\n");
        }
        return new FetchedContent(sb.toString().trim(), "text/plain", null);
    }

    /**
     * 获取飞书 tenant_access_token，带本地缓存和自动续期。
     */
    private synchronized String getTenantAccessToken() throws IOException {
        CachedToken ct = cachedToken.get();
        if (ct != null && Instant.now().isBefore(ct.expiresAt)) {
            return ct.token;
        }
        JsonObject requestBody = new JsonObject();
        requestBody.addProperty("app_id", properties.getAppId());
        requestBody.addProperty("app_secret", properties.getAppSecret());

        Request request = new Request.Builder()
                .url(properties.getTokenUrl())
                .post(RequestBody.create(requestBody.toString(), JSON_MEDIA))
                .build();

        try (Response response = httpClient.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                throw new ClientException("获取飞书 tenant_access_token 失败: HTTP " + response.code());
            }
            String body = response.body().string();
            JsonObject json = JsonParser.parseString(body).getAsJsonObject();
            int code = json.get("code").getAsInt();
            if (code != 0) {
                throw new ClientException("获取飞书 tenant_access_token 失败: " + json.get("msg").getAsString());
            }
            String token = json.get("tenant_access_token").getAsString();
            int expire = json.get("expire").getAsInt();
            cachedToken.set(new CachedToken(token, Instant.now().plusSeconds(expire - 300)));
            return token;
        }
    }

    /**
     * 发送带 Bearer Token 的 GET 请求。
     */
    private String doGet(String url, String token) throws IOException {
        Request request = new Request.Builder()
                .url(url)
                .header("Authorization", "Bearer " + token)
                .get()
                .build();
        try (Response response = httpClient.newCall(request).execute()) {
            if (response.code() == 401) {
                cachedToken.set(null);
                throw new ClientException("飞书 API 认证失败，令牌可能已过期");
            }
            if (!response.isSuccessful()) {
                throw new ClientException("飞书 API 请求失败: HTTP " + response.code());
            }
            return response.body().string();
        }
    }

    /**
     * 检查飞书 API 响应中的错误码，有错误时抛出异常。
     */
    private void checkFeishuError(JsonObject json) {
        if (json.has("code") && json.get("code").getAsInt() != 0) {
            String msg = json.has("msg") ? json.get("msg").getAsString() : "未知错误";
            throw new ClientException("飞书 API 返回错误: " + msg);
        }
    }

    /**
     * 缓存的飞书访问令牌。
     *
     * @param token     令牌值
     * @param expiresAt 过期时间
     */
    private record CachedToken(String token, Instant expiresAt) {
    }
}
