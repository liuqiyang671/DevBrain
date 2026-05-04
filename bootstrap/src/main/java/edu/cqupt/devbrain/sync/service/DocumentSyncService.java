package edu.cqupt.devbrain.sync.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import edu.cqupt.devbrain.knowledge.controller.vo.DocumentVO;
import edu.cqupt.devbrain.sync.controller.request.ScheduleConfigRequest;
import edu.cqupt.devbrain.sync.controller.vo.SyncHistoryVO;

/**
 * 文档同步服务接口，提供手动同步、历史查询、定时配置等能力。
 */
public interface DocumentSyncService {

    /**
     * 执行一次文档同步。
     *
     * @param docId 文档 ID
     * @return 同步结果描述
     */
    SyncResult sync(String docId);

    /**
     * 分页查询文档同步历史。
     *
     * @param docId   文档 ID
     * @param pageNo  页码
     * @param pageSize 每页条数
     * @return 同步历史分页结果
     */
    IPage<SyncHistoryVO> getSyncHistory(String docId, long pageNo, long pageSize);

    /**
     * 更新文档的定时同步配置。
     *
     * @param kbId    知识库 ID
     * @param docId   文档 ID
     * @param request 同步配置请求
     * @return 更新后的文档信息
     */
    DocumentVO updateSchedule(String kbId, String docId, ScheduleConfigRequest request);

    /**
     * 同步结果。
     *
     * @param contentChanged 内容是否发生变更
     * @param message        结果描述
     */
    record SyncResult(boolean contentChanged, String message) {
    }
}
