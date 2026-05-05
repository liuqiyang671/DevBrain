package edu.cqupt.devbrain.ingestion.controller.vo;

import java.util.Map;

/**
 * 摄入流水线节点视图对象。
 *
 * @param nodeId     流水线内节点 ID
 * @param nodeType   节点类型
 * @param settings   节点私有配置
 * @param condition  条件配置 JSON 或布尔文本
 * @param nextNodeId 默认下一个节点 ID
 * @param sortOrder  排序号
 */
public record IngestionPipelineNodeVO(
        String nodeId,
        String nodeType,
        Map<String, Object> settings,
        String condition,
        String nextNodeId,
        Integer sortOrder
) {
}
