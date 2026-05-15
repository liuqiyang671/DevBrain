package edu.cqupt.devbrain.commerce.evaluation.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import edu.cqupt.devbrain.commerce.evaluation.dto.req.EvaluationCaseReq;
import edu.cqupt.devbrain.commerce.evaluation.dto.resp.EvaluationCaseResp;

/**
 * 评测用例服务接口。
 * 提供评测用例的增删改查能力。
 */
public interface EvaluationCaseService {

    /**
     * 在指定数据集下创建评测用例。
     *
     * @param datasetId 所属数据集ID
     * @param request   用例创建请求
     * @return 创建后的用例信息
     */
    EvaluationCaseResp create(String datasetId, EvaluationCaseReq request);

    /**
     * 更新评测用例。
     *
     * @param caseId  用例ID
     * @param request 用例更新请求
     * @return 更新后的用例信息
     */
    EvaluationCaseResp update(String caseId, EvaluationCaseReq request);

    /**
     * 分页查询指定数据集下的评测用例列表。
     *
     * @param datasetId 所属数据集ID
     * @param pageNo    页码
     * @param pageSize  每页条数
     * @return 分页结果
     */
    IPage<EvaluationCaseResp> page(String datasetId, long pageNo, long pageSize);

    /**
     * 删除评测用例（逻辑删除）。
     *
     * @param caseId 用例ID
     */
    void delete(String caseId);
}
