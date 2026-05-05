package edu.cqupt.devbrain.ingestion.domain.pipeline;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.Builder;
import lombok.Data;

/**
 * 流水线节点配置，描述节点 ID、节点类型、执行条件和下一个节点。
 */
@Data
@Builder
public class NodeConfig {

    /**
     * 节点实例 ID，在同一条流水线中唯一。
     */
    private String nodeId;

    /**
     * 节点类型标识，如 fetcher、parser、chunker。
     */
    private String nodeType;

    /**
     * 节点私有配置，具体结构由节点实现自行解析。
     */
    private JsonNode settings;

    /**
     * 执行条件配置，后续 Pipeline 引擎可据此决定是否执行或跳转。
     */
    private JsonNode condition;

    /**
     * 默认下一个节点 ID。
     */
    private String nextNodeId;
}
