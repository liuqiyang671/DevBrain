package edu.cqupt.devbrain.knowledge.controller.vo;

/**
 * 文档分块列表视图对象。
 *
 * @param chunkId 分块 ID
 * @param index 分块顺序索引
 * @param content 分块内容摘要，最长保留前 200 个字符
 * @param charCount 原始分块字符数
 */
public record ChunkVO(
        String chunkId,
        Integer index,
        String content,
        int charCount
) {
}
