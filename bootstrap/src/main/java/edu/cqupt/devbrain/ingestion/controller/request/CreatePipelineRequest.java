package edu.cqupt.devbrain.ingestion.controller.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * 创建摄入流水线请求。
 *
 * @param name        流水线名称
 * @param description 流水线说明
 * @param nodes       节点配置列表
 */
public record CreatePipelineRequest(
        @NotBlank(message = "流水线名称不能为空")
        @Size(max = 100, message = "流水线名称不能超过 100 个字符")
        String name,

        String description,

        List<@Valid NodeConfigRequest> nodes
) {
}
