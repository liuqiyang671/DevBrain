package edu.cqupt.devbrain.ingestion.node.fetcher;

import edu.cqupt.devbrain.ingestion.domain.SourceType;
import edu.cqupt.devbrain.ingestion.domain.context.DocumentSource;
import org.springframework.stereotype.Component;

/**
 * 飞书文档获取策略占位实现，后续接入飞书开放平台时替换 fetch 逻辑即可。
 */
@Component
public class FeishuDocumentFetcher implements DocumentFetcher {

    /**
     * 第一版 Pipeline 先保留明确失败语义，避免调用方误以为飞书来源已完整支持。
     */
    @Override
    public byte[] fetch(DocumentSource source) {
        throw new UnsupportedOperationException("飞书文档获取暂未实现");
    }

    /**
     * 当前策略处理 FEISHU 来源。
     */
    @Override
    public SourceType getSupportedType() {
        return SourceType.FEISHU;
    }
}
