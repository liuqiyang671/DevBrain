package edu.cqupt.devbrain.ingestion.domain.pipeline;

import lombok.Builder;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * 摄入流水线定义，描述节点拓扑和基础说明信息。
 */
@Data
@Builder
public class PipelineDefinition {

    /**
     * 流水线 ID。
     */
    private String id;

    /**
     * 流水线名称。
     */
    private String name;

    /**
     * 流水线说明。
     */
    private String description;

    /**
     * 节点配置列表，按默认执行顺序排列。
     */
    @Builder.Default
    private List<NodeConfig> nodes = new ArrayList<>();
}
