package edu.cqupt.devbrain.ingestion.domain.context;

import lombok.Builder;
import lombok.Data;

/**
 * 节点执行日志，用于记录每个节点的结果和耗时。
 */
@Data
@Builder
public class NodeLog {

    /**
     * 节点类型标识。
     */
    private String nodeType;

    /**
     * 节点实例 ID。
     */
    private String nodeId;

    /**
     * 当前节点是否执行成功。
     */
    private boolean success;

    /**
     * 节点执行消息。
     */
    private String message;

    /**
     * 节点耗时，单位毫秒。
     */
    private long durationMs;
}
