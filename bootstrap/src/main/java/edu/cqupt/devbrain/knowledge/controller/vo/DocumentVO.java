package edu.cqupt.devbrain.knowledge.controller.vo;

import java.util.Date;

/**
 * 知识库文档视图对象 -- 返回给前端，不直接暴露 DO。
 *
 * @param id             文档 ID
 * @param kbId           所属知识库 ID
 * @param docName        文档名称
 * @param enabled        是否启用
 * @param chunkCount     切片数量
 * @param fileUrl        文件存储 URL
 * @param fileType       文件类型
 * @param fileSize       文件大小（字节）
 * @param processMode    处理模式
 * @param status         处理状态
 * @param sourceType     来源类型
 * @param sourceLocation 来源地址
 * @param chunkStrategy  切片策略
 * @param chunkConfig    切片配置（JSON 字符串）
 * @param pipelineId     处理流水线 ID
 * @param createTime     创建时间
 * @param updateTime     更新时间
 */
public record DocumentVO(
        String id,
        String kbId,
        String docName,
        Integer enabled,
        Long chunkCount,
        String fileUrl,
        String fileType,
        Long fileSize,
        String processMode,
        String status,
        String sourceType,
        String sourceLocation,
        String chunkStrategy,
        String chunkConfig,
        String pipelineId,
        Date createTime,
        Date updateTime
) {
}
