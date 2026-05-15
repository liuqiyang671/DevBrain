package edu.cqupt.devbrain.commerce.catalog.dao.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import edu.cqupt.devbrain.knowledge.dao.typehandler.JsonbStringTypeHandler;
import lombok.Data;

import java.util.Date;

/**
 * 商品-文档关联实体，对应 t_product_doc_link 表。
 * 记录商品与知识库文档的绑定关系，支持按文档类型分类管理。
 */
@Data
@TableName(value = "t_product_doc_link", autoResultMap = true)
public class ProductDocumentLinkDO {

    /** 主键ID（雪花算法生成） */
    @TableId(type = IdType.ASSIGN_ID)
    private String id;

    /** 关联的商品SPU ID */
    private String productId;

    /** 关联的知识库文档ID */
    private String docId;

    /** 关联的文档分块ID（可选，精确到某一分块） */
    private String chunkId;

    /** 文档绑定类型：detail-详情，marketing-营销，faq-FAQ，policy-政策，review-评测 */
    private String docType;

    /** 扩展元数据，JSON格式存储 */
    @TableField(typeHandler = JsonbStringTypeHandler.class)
    private String metadata;

    /** 创建人 */
    private String createdBy;

    /** 更新人 */
    private String updatedBy;

    @TableField(fill = FieldFill.INSERT)
    private Date createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private Date updateTime;

    @TableLogic
    @TableField(fill = FieldFill.INSERT)
    private Integer deleted;
}
