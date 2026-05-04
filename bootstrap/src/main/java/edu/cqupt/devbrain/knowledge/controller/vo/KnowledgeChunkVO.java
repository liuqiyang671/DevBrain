package edu.cqupt.devbrain.knowledge.controller.vo;

import java.time.LocalDateTime;

/**
 * 知识库分块详情视图对象。
 *
 * @param id           分块 ID
 * @param kbId         所属知识库 ID
 * @param docId        所属文档 ID
 * @param chunkIndex   块在文档中的序号
 * @param content      块文本内容
 * @param contentHash  内容 SHA-256 哈希
 * @param charCount    字符数
 * @param tokenCount   token 数
 * @param enabled      是否启用
 * @param createTime   创建时间
 * @param updateTime   更新时间
 */
public record KnowledgeChunkVO(
        String id,
        String kbId,
        String docId,
        Integer chunkIndex,
        String content,
        String contentHash,
        Integer charCount,
        Integer tokenCount,
        Integer enabled,
        LocalDateTime createTime,
        LocalDateTime updateTime
) {
}
