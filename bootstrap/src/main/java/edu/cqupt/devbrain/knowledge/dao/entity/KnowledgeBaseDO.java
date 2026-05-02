package edu.cqupt.devbrain.knowledge.dao.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;

import java.util.Date;

/**
 * 知识库实体 —— 对应 t_knowledge_base，作为文档、Chunk 和向量集合的上层容器。
 * <p>
 * 该实体只在 DAO/Service 层流转，接口层统一转换为 KnowledgeBaseVO。
 */
@TableName("t_knowledge_base")
public class KnowledgeBaseDO {

    /** 主键，使用 MyBatis-Plus 雪花算法生成。 */
    @TableId(type = IdType.ASSIGN_ID)
    private String id;

    /** 知识库展示名称，面向用户可读。 */
    private String name;

    /** 知识库描述，用于说明资料范围和用途。 */
    private String description;

    /** Embedding 模型标识，后续文档入库和向量检索需要保持一致。 */
    private String embeddingModel;

    /** 向量集合名称，创建后禁止修改。 */
    private String collectionName;

    /** enabled / disabled，用于控制知识库是否可被后续业务使用。 */
    private String status;

    /** 创建人用户 ID。 */
    private String createdBy;

    /** 最近更新人用户 ID。 */
    private String updatedBy;

    /** 创建时间，由 MyMetaObjectHandler 自动填充。 */
    @TableField(fill = FieldFill.INSERT)
    private Date createTime;

    /** 更新时间，由 MyMetaObjectHandler 自动填充。 */
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private Date updateTime;

    /** 逻辑删除标记：0 表示未删除，1 表示已删除。 */
    @TableLogic
    @TableField(fill = FieldFill.INSERT)
    private Integer deleted;

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getEmbeddingModel() {
        return embeddingModel;
    }

    public void setEmbeddingModel(String embeddingModel) {
        this.embeddingModel = embeddingModel;
    }

    public String getCollectionName() {
        return collectionName;
    }

    public void setCollectionName(String collectionName) {
        this.collectionName = collectionName;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getCreatedBy() {
        return createdBy;
    }

    public void setCreatedBy(String createdBy) {
        this.createdBy = createdBy;
    }

    public String getUpdatedBy() {
        return updatedBy;
    }

    public void setUpdatedBy(String updatedBy) {
        this.updatedBy = updatedBy;
    }

    public Date getCreateTime() {
        return createTime;
    }

    public void setCreateTime(Date createTime) {
        this.createTime = createTime;
    }

    public Date getUpdateTime() {
        return updateTime;
    }

    public void setUpdateTime(Date updateTime) {
        this.updateTime = updateTime;
    }

    public Integer getDeleted() {
        return deleted;
    }

    public void setDeleted(Integer deleted) {
        this.deleted = deleted;
    }
}
