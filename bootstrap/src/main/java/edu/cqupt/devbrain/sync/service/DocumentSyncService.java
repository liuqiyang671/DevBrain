package edu.cqupt.devbrain.sync.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import edu.cqupt.devbrain.knowledge.controller.vo.DocumentVO;
import edu.cqupt.devbrain.sync.controller.request.ScheduleConfigRequest;
import edu.cqupt.devbrain.sync.controller.vo.SyncHistoryVO;

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
     */
    IPage<SyncHistoryVO> getSyncHistory(String docId, long pageNo, long pageSize);

    /**
     * 更新文档的定时同步配置。
     */
    DocumentVO updateSchedule(String kbId, String docId, ScheduleConfigRequest request);

    record SyncResult(boolean contentChanged, String message) {
    }
}
