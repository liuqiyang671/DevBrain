package edu.cqupt.devbrain.ingestion.controller.vo;

import java.util.Date;
import java.util.Map;

/**
 * 摄入任务节点日志视图对象。
 *
 * @param id         记录 ID
 * @param taskId     任务 ID
 * @param pipelineId 流水线 ID
 * @param nodeId     节点 ID
 * @param nodeType   节点类型
 * @param nodeOrder  节点顺序
 * @param status     节点状态
 * @param durationMs 节点耗时
 * @param output     节点输出
 * @param createTime 创建时间
 */
public record IngestionTaskNodeVO(
        String id,
        String taskId,
        String pipelineId,
        String nodeId,
        String nodeType,
        Integer nodeOrder,
        String status,
        Long durationMs,
        Map<String, Object> output,
        Date createTime
) {
}
