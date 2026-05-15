package edu.cqupt.devbrain.commerce.evaluation.dao.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.util.Date;

/**
 * 评测数据集实体，对应数据库表 t_eval_dataset。
 * 用于管理导购评测用例的逻辑分组。
 */
@Data
@TableName("t_eval_dataset")
public class EvaluationDatasetDO {

    /** 主键ID */
    @TableId(type = IdType.INPUT)
    private String id;

    /** 数据集名称 */
    private String name;

    /** 数据集描述 */
    private String description;

    /** 数据集状态（enabled / disabled） */
    private String status;

    /** 创建人ID */
    private String createdBy;

    /** 最近更新人ID */
    private String updatedBy;

    /** 创建时间 */
    @TableField(fill = FieldFill.INSERT)
    private Date createTime;

    /** 更新时间 */
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private Date updateTime;

    /** 逻辑删除标志（0-未删除，1-已删除） */
    @TableLogic
    @TableField(fill = FieldFill.INSERT)
    private Integer deleted;
}
