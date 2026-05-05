package edu.cqupt.devbrain.ingestion.controller.vo;

import java.util.Date;
import java.util.List;

/**
 * 摄入流水线视图对象。
 *
 * @param id          流水线 ID
 * @param name        流水线名称
 * @param description 流水线说明
 * @param nodeCount   节点数量
 * @param nodes       节点配置列表，分页列表可为空
 * @param createdBy   创建人用户 ID
 * @param createTime  创建时间
 * @param updateTime  更新时间
 */
public record IngestionPipelineVO(
        String id,
        String name,
        String description,
        int nodeCount,
        List<IngestionPipelineNodeVO> nodes,
        String createdBy,
        Date createTime,
        Date updateTime
) {
}
