package edu.cqupt.devbrain.sync.controller.vo;

import java.util.Date;

/**
 * 文档同步历史视图对象。
 *
 * @param id             记录 ID
 * @param docId          文档 ID
 * @param syncStatus     同步状态（success / failed）
 * @param contentHash    本次同步内容的 SHA-256 哈希
 * @param contentChanged 内容是否变更（1-变更，0-未变更）
 * @param errorMessage   错误信息
 * @param durationMs     同步耗时（毫秒）
 * @param createTime     创建时间
 */
public record SyncHistoryVO(
        String id,
        String docId,
        String syncStatus,
        String contentHash,
        Integer contentChanged,
        String errorMessage,
        Long durationMs,
        Date createTime
) {
}
