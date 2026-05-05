package edu.cqupt.devbrain.ingestion.controller.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.Data;

/**
 * 摄入任务分页查询请求。
 */
@Data
public class IngestionTaskPageRequest {

    /**
     * 页码，从 1 开始。
     */
    @Min(value = 1, message = "pageNo 最小为 1")
    private long pageNo = 1;

    /**
     * 每页大小，最大 100。
     */
    @Min(value = 1, message = "pageSize 最小为 1")
    @Max(value = 100, message = "pageSize 最大为 100")
    private long pageSize = 10;

    /**
     * 流水线 ID 过滤。
     */
    private String pipelineId;

    /**
     * 任务状态过滤。
     */
    private String status;
}
