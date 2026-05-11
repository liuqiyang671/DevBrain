package edu.cqupt.devbrain.rag.core.websearch;

import java.util.List;

/**
 * 联网搜索服务端口。
 */
public interface WebSearchService {

    /**
     * 执行搜索并返回简洁结果列表。
     */
    List<WebSearchResult> search(String query, int limit);
}
