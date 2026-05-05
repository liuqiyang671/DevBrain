package edu.cqupt.devbrain.ingestion.domain;

/**
 * 摄入任务生命周期状态。
 */
public enum IngestionStatus {

    /**
     * 等待执行。
     */
    PENDING,

    /**
     * 正在执行。
     */
    RUNNING,

    /**
     * 已完成。
     */
    COMPLETED,

    /**
     * 执行失败。
     */
    FAILED
}
