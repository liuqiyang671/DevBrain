package edu.cqupt.devbrain.ingestion.controller.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.Map;

/**
 * 流水线节点配置请求。
 *
 * @param nodeId     流水线内节点 ID
 * @param nodeType   节点类型，如 fetcher、parser、chunker
 * @param settings   节点私有配置
 * @param condition  条件配置，支持 true/false 或 JSON 字符串
 * @param nextNodeId 默认下一个节点 ID
 */
public record NodeConfigRequest(
        @NotBlank(message = "nodeId 不能为空")
        @Size(max = 50, message = "nodeId 不能超过 50 个字符")
        String nodeId,

        @NotBlank(message = "nodeType 不能为空")
        @Size(max = 30, message = "nodeType 不能超过 30 个字符")
        String nodeType,

        Map<String, Object> settings,

        String condition,

        @Size(max = 50, message = "nextNodeId 不能超过 50 个字符")
        String nextNodeId
) {
}
