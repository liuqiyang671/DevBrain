package edu.cqupt.devbrain.sync.adapter;

import edu.cqupt.devbrain.framework.exception.ClientException;
import lombok.extern.slf4j.Slf4j;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * URL 网页抓取适配器，通过 HTTP 请求获取网页内容并提取正文文本。
 */
@Slf4j
@Component
public class UrlScrapingAdapter implements DocumentSourceAdapter {

    private final OkHttpClient httpClient;

    /**
     * 构造方法，注入 HTTP 客户端。
     */
    public UrlScrapingAdapter(OkHttpClient httpClient) {
        this.httpClient = httpClient;
    }

    /**
     * 返回来源类型标识 {@code url}。
     */
    @Override
    public String sourceType() {
        return "url";
    }

    /**
     * 抓取指定 URL 的网页内容，提取正文文本并返回。
     */
    @Override
    public FetchedContent fetchContent(String sourceLocation) throws Exception {
        if (!sourceLocation.startsWith("http://") && !sourceLocation.startsWith("https://")) {
            throw new ClientException("URL 必须以 http:// 或 https:// 开头");
        }

        Request request = new Request.Builder()
                .url(sourceLocation)
                .header("User-Agent", "Mozilla/5.0 (compatible; ai-shopping-agent/1.0; +https://devbrain.cqupt.edu.cn)")
                .get()
                .build();

        String html;
        try (Response response = httpClient.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                throw new ClientException("网页抓取失败: HTTP " + response.code());
            }
            html = response.body().string();
        } catch (IOException e) {
            throw new ClientException("网页抓取网络错误: " + e.getMessage());
        }

        Document doc = Jsoup.parse(html, sourceLocation);

        // Remove non-content elements
        doc.select("script, style, nav, footer, header, aside, noscript, iframe").remove();

        String title = doc.title();
        if (title != null && title.isBlank()) {
            title = null;
        }

        // Try to extract main content area first
        Element main = doc.selectFirst("article, main, [role=main], .content, .post-content, .article-content");
        String text;
        if (main != null) {
            text = extractText(main);
        } else {
            Element body = doc.body();
            text = body != null ? extractText(body) : "";
        }

        text = cleanText(text);
        if (text.isBlank()) {
            throw new ClientException("网页内容为空，无法提取有效文本");
        }

        return new FetchedContent(text, "text/html", title);
    }

    /**
     * 从 HTML 元素中提取结构化文本。
     */
    private String extractText(Element element) {
        StringBuilder sb = new StringBuilder();
        element.children().forEach(child -> appendBlockText(child, sb));
        return sb.toString();
    }

    /**
     * 递归解析块级元素并追加 Markdown 风格的文本。
     */
    private void appendBlockText(Element element, StringBuilder sb) {
        String tag = element.tagName();

        if (tag.equals("p") || tag.equals("div") || tag.equals("section") || tag.equals("article")) {
            String text = element.ownText().trim();
            if (!text.isEmpty()) {
                sb.append(text).append("\n\n");
            }
            element.children().forEach(child -> appendBlockText(child, sb));
        } else if (tag.matches("h[1-6]")) {
            int level = Integer.parseInt(tag.substring(1));
            sb.append("#".repeat(level)).append(" ").append(element.ownText().trim()).append("\n\n");
        } else if (tag.equals("li")) {
            sb.append("- ").append(element.ownText().trim()).append("\n");
        } else if (tag.equals("pre") || tag.equals("code")) {
            sb.append("```\n").append(element.text()).append("\n```\n\n");
        } else if (tag.equals("br")) {
            sb.append("\n");
        } else if (tag.equals("table")) {
            element.select("tr").forEach(row -> {
                row.select("td, th").forEach(cell -> sb.append(cell.text().trim()).append("\t"));
                sb.append("\n");
            });
            sb.append("\n");
        } else {
            String text = element.ownText().trim();
            if (!text.isEmpty()) {
                sb.append(text).append("\n");
            }
            element.children().forEach(child -> appendBlockText(child, sb));
        }
    }

    /**
     * 清理文本中的多余空白和连续换行。
     */
    private String cleanText(String text) {
        return text
                .replaceAll("[ \t]+", " ")
                .replaceAll("\n{3,}", "\n\n")
                .trim();
    }
}
