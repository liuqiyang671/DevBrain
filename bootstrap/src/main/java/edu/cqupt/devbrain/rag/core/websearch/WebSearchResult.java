package edu.cqupt.devbrain.rag.core.websearch;

/**
 * 联网搜索结果条目。
 */
public record WebSearchResult(
        String title,
        String url,
        String snippet
) {
}
