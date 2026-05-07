package edu.cqupt.devbrain.rag.core.rewrite.dao.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.util.Date;

/**
 * 查询术语映射实体，对应 t_query_term_mapping。
 */
@Data
@TableName("t_query_term_mapping")
public class QueryTermMappingDO {

    /** 主键，使用 MyBatis-Plus 雪花算法生成。 */
    @TableId(type = IdType.ASSIGN_ID)
    private String id;

    /** 所属领域，如 HR、IT、finance。 */
    private String domain;

    /** 源词，如 OA、年假。 */
    private String sourceTerm;

    /** 目标词，如 OA系统、年休假。 */
    private String targetTerm;

    /** 匹配方式：1 精确匹配，2 前缀匹配，3 正则匹配，4 全词匹配。 */
    private Integer matchType;

    /** 优先级，数值越大越先匹配。 */
    private Integer priority;

    /** 是否启用：1 启用，0 禁用。 */
    private Integer enabled;

    /** 备注。 */
    private String remark;

    /** 创建人用户 ID。 */
    private String createBy;

    /** 最近更新人用户 ID。 */
    private String updateBy;

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
}
