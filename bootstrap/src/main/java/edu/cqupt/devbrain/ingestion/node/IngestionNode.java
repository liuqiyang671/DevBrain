package edu.cqupt.devbrain.ingestion.node;

import edu.cqupt.devbrain.ingestion.domain.context.IngestionContext;
import edu.cqupt.devbrain.ingestion.domain.pipeline.NodeConfig;
import edu.cqupt.devbrain.ingestion.domain.result.NodeResult;

/**
 * 摄入流水线节点接口。每个节点负责处理 IngestionContext 的一个阶段。
 */
public interface IngestionNode {

    /**
     * 返回节点类型标识，如 fetcher、parser、chunker。
     *
     * @return 节点类型
     */
    String getNodeType();

    /**
     * 执行节点逻辑，并通过 NodeResult 告知 Pipeline 引擎是否继续。
     *
     * @param context 摄入上下文
     * @param config  当前节点配置
     * @return 节点执行结果
     */
    NodeResult execute(IngestionContext context, NodeConfig config);
}
