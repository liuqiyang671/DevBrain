package edu.cqupt.devbrain.ingestion.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import edu.cqupt.devbrain.ingestion.controller.request.ExecuteTaskRequest;
import edu.cqupt.devbrain.ingestion.controller.request.IngestionTaskPageRequest;
import edu.cqupt.devbrain.ingestion.controller.vo.IngestionTaskNodeVO;
import edu.cqupt.devbrain.ingestion.controller.vo.IngestionTaskVO;
import edu.cqupt.devbrain.ingestion.domain.result.IngestionResult;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

/**
 * 摄入任务执行服务接口。
 */
public interface IngestionTaskService {

    /**
     * 创建并同步执行摄入任务。
     *
     * @param request 执行请求
     * @return 执行结果
     */
    IngestionResult execute(ExecuteTaskRequest request);

    /**
     * 上传文件并同步执行摄入任务。
     *
     * @param pipelineId 流水线 ID
     * @param file       上传文件
     * @return 执行结果
     */
    IngestionResult upload(String pipelineId, MultipartFile file);

    /**
     * 查询任务详情。
     *
     * @param taskId 任务 ID
     * @return 任务视图对象
     */
    IngestionTaskVO getTask(String taskId);

    /**
     * 查询任务节点日志。
     *
     * @param taskId 任务 ID
     * @return 节点日志列表
     */
    List<IngestionTaskNodeVO> getTaskNodes(String taskId);

    /**
     * 分页查询摄入任务。
     *
     * @param request 分页请求
     * @return 任务分页结果
     */
    IPage<IngestionTaskVO> page(IngestionTaskPageRequest request);
}
