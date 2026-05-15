package edu.cqupt.devbrain.commerce.evaluation.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import edu.cqupt.devbrain.commerce.evaluation.dto.req.EvaluationDatasetReq;
import edu.cqupt.devbrain.commerce.evaluation.dto.resp.EvaluationDatasetResp;

/**
 * 评测数据集服务接口。
 * 提供评测数据集的增删改查能力。
 */
public interface EvaluationDatasetService {

    /**
     * 创建评测数据集。
     *
     * @param request 数据集创建请求
     * @return 创建后的数据集信息
     */
    EvaluationDatasetResp create(EvaluationDatasetReq request);

    /**
     * 更新评测数据集。
     *
     * @param datasetId 数据集ID
     * @param request   数据集更新请求
     * @return 更新后的数据集信息
     */
    EvaluationDatasetResp update(String datasetId, EvaluationDatasetReq request);

    /**
     * 查询单个评测数据集详情。
     *
     * @param datasetId 数据集ID
     * @return 数据集信息
     */
    EvaluationDatasetResp get(String datasetId);

    /**
     * 分页查询评测数据集列表。
     *
     * @param pageNo   页码
     * @param pageSize 每页条数
     * @param keyword  搜索关键词（匹配名称或描述）
     * @return 分页结果
     */
    IPage<EvaluationDatasetResp> page(long pageNo, long pageSize, String keyword);

    /**
     * 删除评测数据集（逻辑删除）。
     *
     * @param datasetId 数据集ID
     */
    void delete(String datasetId);
}
