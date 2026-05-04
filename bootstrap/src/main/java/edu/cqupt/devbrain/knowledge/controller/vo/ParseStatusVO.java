package edu.cqupt.devbrain.knowledge.controller.vo;

import java.util.Date;

/**
 * 文档解析状态视图对象。
 *
 * @param status 解析状态
 * @param chunkCount 分块数量
 * @param extractDuration 文本提取耗时
 * @param chunkDuration 分块耗时
 * @param totalDuration 总耗时
 * @param errorMessage 失败错误信息
 * @param startTime 解析开始时间
 * @param endTime 解析结束时间
 */
public record ParseStatusVO(
        String status,
        Integer chunkCount,
        Long extractDuration,
        Long chunkDuration,
        Long totalDuration,
        String errorMessage,
        Date startTime,
        Date endTime
) {
}
