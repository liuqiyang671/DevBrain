package edu.cqupt.devbrain.commerce.evaluation.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import edu.cqupt.devbrain.commerce.evaluation.dto.req.EvaluationRunReq;
import edu.cqupt.devbrain.commerce.evaluation.dto.resp.EvaluationReportResp;
import edu.cqupt.devbrain.commerce.evaluation.dto.resp.EvaluationRunResp;

/**
 * 评测运行服务接口。
 * 提供评测运行的发起、查询和报告查看能力。
 */
public interface EvaluationRunService {

    /**
     * 发起一次评测运行，遍历数据集中的所有用例执行评测。
     *
     * @param request 评测运行请求
     * @return 评测运行信息
     */
    EvaluationRunResp run(EvaluationRunReq request);

    /**
     * 分页查询评测运行记录。
     *
     * @param pageNo    页码
     * @param pageSize  每页条数
     * @param datasetId 按数据集ID筛选（可选）
     * @return 分页结果
     */
    IPage<EvaluationRunResp> page(long pageNo, long pageSize, String datasetId);

    /**
     * 获取评测运行的详细报告，包含用例结果、失败分析和改进建议。
     *
     * @param runId 评测运行ID
     * @return 评测报告
     */
    EvaluationReportResp report(String runId);

    /**
     * 取消正在执行的评测运行。
     *
     * @param runId 评测运行ID
     * @return 更新后的评测运行信息
     */
    EvaluationRunResp cancel(String runId);
}
