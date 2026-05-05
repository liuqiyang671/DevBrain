package edu.cqupt.devbrain.ingestion.domain.result;

import edu.cqupt.devbrain.ingestion.domain.IngestionStatus;
import lombok.Builder;
import lombok.Data;

/**
 * 摄入流水线最终执行结果。
 */
@Data
@Builder
public class IngestionResult {

    /**
     * 摄入任务 ID。
     */
    private String taskId;

    /**
     * 流水线定义 ID。
     */
    private String pipelineId;

    /**
     * 最终状态。
     */
    private IngestionStatus status;

    /**
     * 最终生成的 chunk 数量。
     */
    private int chunkCount;

    /**
     * 执行结果说明。
     */
    private String message;
}
