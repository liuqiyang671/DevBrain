package edu.cqupt.devbrain.sync.controller.vo;

import java.util.Date;

/**
 * 同步任务概览视图对象。
 *
 * @param docId           文档 ID
 * @param docName         文档名称
 * @param kbId            知识库 ID
 * @param sourceType      来源类型
 * @param sourceLocation  来源地址
 * @param scheduleEnabled 是否启用定时同步
 * @param scheduleCron    Cron 表达式
 * @param lastSyncTime    最近一次同步时间
 * @param lastContentHash 最近一次同步的内容哈希
 */
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
