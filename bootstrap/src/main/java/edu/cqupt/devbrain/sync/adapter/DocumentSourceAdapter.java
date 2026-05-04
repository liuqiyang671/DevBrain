package edu.cqupt.devbrain.sync.adapter;

/**
 * 文档来源适配器接口，定义从不同来源拉取文档内容的统一契约。
 */
public interface DocumentSourceAdapter {

    /**
     * 返回该适配器支持的来源类型标识（如 feishu、url）。
     */
    String sourceType();

    /**
     * 根据来源地址拉取文档内容。
     *
     * @param sourceLocation 来源地址
     * @return 拉取到的文档内容
     * @throws Exception 拉取过程中可能发生的异常
     */
    FetchedContent fetchContent(String sourceLocation) throws Exception;
}
