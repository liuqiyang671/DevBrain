package edu.cqupt.devbrain.ingestion.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import edu.cqupt.devbrain.ingestion.controller.request.CreatePipelineRequest;
import edu.cqupt.devbrain.ingestion.controller.request.IngestionPipelinePageRequest;
import edu.cqupt.devbrain.ingestion.controller.request.UpdatePipelineRequest;
import edu.cqupt.devbrain.ingestion.controller.vo.IngestionPipelineVO;
import edu.cqupt.devbrain.ingestion.domain.pipeline.PipelineDefinition;

/**
 * 摄入流水线定义服务接口。
 */
public interface IngestionPipelineService {

    /**
     * 根据流水线 ID 加载可执行的 PipelineDefinition。
     *
     * @param pipelineId 流水线 ID
     * @return 可执行流水线定义
     */
    PipelineDefinition getDefinition(String pipelineId);

    /**
     * 创建流水线定义。
     *
     * @param request 创建请求
     * @return 创建后的流水线视图对象
     */
    IngestionPipelineVO create(CreatePipelineRequest request);

    /**
     * 更新流水线定义。
     *
     * @param id      流水线 ID
     * @param request 更新请求
     * @return 更新后的流水线视图对象
     */
    IngestionPipelineVO update(String id, UpdatePipelineRequest request);

    /**
     * 删除流水线定义及其节点。
     *
     * @param id 流水线 ID
     */
    void delete(String id);

    /**
     * 分页查询流水线定义。
     *
     * @param request 分页请求
     * @return 流水线分页结果
     */
    IPage<IngestionPipelineVO> page(IngestionPipelinePageRequest request);
}
