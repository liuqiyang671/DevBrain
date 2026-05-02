package edu.cqupt.devbrain.knowledge.controller.vo;

import java.util.Date;

/**
 * 知识库视图对象 —— 返回给前端，不直接暴露 DO。
 *
 * @param id             知识库 ID
 * @param name           知识库名称
 * @param description    知识库描述
 * @param embeddingModel Embedding 模型标识
 * @param collectionName 向量集合名称
 * @param status         知识库状态
 * @param documentCount  当前知识库下未删除文档数量
 * @param createdBy      创建人用户 ID
 * @param updatedBy      最近更新人用户 ID
 * @param createTime     创建时间
 * @param updateTime     更新时间
 */
public record KnowledgeBaseVO(
        String id,
        String name,
        String description,
        String embeddingModel,
        String collectionName,
        String status,
        Long documentCount,
        String createdBy,
        String updatedBy,
        Date createTime,
        Date updateTime
) {
}
