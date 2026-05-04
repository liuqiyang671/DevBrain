package edu.cqupt.devbrain.sync.controller.vo;

import java.util.Date;

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
