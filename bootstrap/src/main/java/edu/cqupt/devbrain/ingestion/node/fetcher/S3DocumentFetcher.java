package edu.cqupt.devbrain.ingestion.node.fetcher;

import edu.cqupt.devbrain.ingestion.domain.SourceType;
import edu.cqupt.devbrain.ingestion.domain.context.DocumentSource;
import org.springframework.stereotype.Component;

/**
 * S3 直连获取策略占位实现；当前 FILE 来源已复用 FileStorageService 覆盖项目内对象存储读取。
 */
@Component
public class S3DocumentFetcher implements DocumentFetcher {

    /**
     * 第一版 Pipeline 先保留明确失败语义，后续可接入独立 S3 客户端。
     */
    @Override
    public byte[] fetch(DocumentSource source) {
        throw new UnsupportedOperationException("S3 文档获取暂未实现");
    }

    /**
     * 当前策略处理 S3 来源。
     */
    @Override
    public SourceType getSupportedType() {
        return SourceType.S3;
    }
}
