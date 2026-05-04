package edu.cqupt.devbrain.sync.controller.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * 定时同步配置请求体。
 *
 * @param sourceType      文档来源类型（feishu / url / file）
 * @param sourceLocation  来源地址
 * @param scheduleEnabled 是否启用定时同步（1-启用，0-禁用）
 * @param scheduleCron    Cron 表达式
 */
public record ScheduleConfigRequest(
        @NotBlank @Size(max = 32) String sourceType,
        @Size(max = 512) String sourceLocation,
        @NotNull Integer scheduleEnabled,
        @Size(max = 64) String scheduleCron
) {
}
