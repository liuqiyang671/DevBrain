package edu.cqupt.devbrain.sync.controller.vo;

import java.util.Date;

public record SyncTaskOverviewVO(
        String docId,
        String docName,
        String kbId,
        String sourceType,
        String sourceLocation,
        Integer scheduleEnabled,
        String scheduleCron,
        Date lastSyncTime,
        String lastContentHash
) {
}
