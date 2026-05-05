package edu.cqupt.devbrain.ingestion.controller.vo;

import java.util.Date;
import java.util.List;
import java.util.Map;

/**
 * 摄入任务视图对象。
 *
 * @param id             任务 ID
 * @param pipelineId     流水线 ID
 * @param sourceType     来源类型
 * @param sourceLocation 来源地址
 * @param status         任务状态
 * @param chunkCount     chunk 数量
 * @param logs           节点日志列表
 * @param metadata       任务元数据
 * @param createdBy      创建人
 * @param createTime     创建时间
 * @param updateTime     更新时间
 */
public record IngestionTaskVO(
        String id,
        String pipelineId,
        String sourceType,
        String sourceLocation,
        String status,
        Integer chunkCount,
        List<Map<String, Object>> logs,
        Map<String, Object> metadata,
        String createdBy,
        Date createTime,
        Date updateTime
) {
}
