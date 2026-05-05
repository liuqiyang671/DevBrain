package edu.cqupt.devbrain.ingestion.node.fetcher;

import edu.cqupt.devbrain.ingestion.domain.SourceType;
import edu.cqupt.devbrain.ingestion.domain.context.DocumentSource;

/**
 * 文档获取策略接口，按来源类型封装不同存储或第三方系统的读取逻辑。
 */
public interface DocumentFetcher {

    /**
     * 获取原始文档字节。
     *
     * @param source 文档来源信息
     * @return 原始文档字节
     * @throws Exception 获取失败时抛出，由 FetcherNode 统一转为 NodeResult.fail
     */
    byte[] fetch(DocumentSource source) throws Exception;

    /**
     * 返回当前策略支持的来源类型。
     *
     * @return 来源类型
     */
    SourceType getSupportedType();
}
