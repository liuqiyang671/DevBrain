package edu.cqupt.devbrain.ingestion.controller.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * 更新摄入流水线请求。
 *
 * @param name        流水线名称，传入时覆盖原值
 * @param description 流水线说明，传入时覆盖原值
 * @param nodes       节点配置列表，传入时替换原节点列表
 */
public record UpdatePipelineRequest(
        @Size(max = 100, message = "流水线名称不能超过 100 个字符")
        String name,

        String description,

        List<@Valid NodeConfigRequest> nodes
) {
}
