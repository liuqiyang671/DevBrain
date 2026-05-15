package edu.cqupt.devbrain.commerce.evaluation.dao.entity;

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
 * 评测运行实体，对应数据库表 t_eval_run。
 * 记录一次完整评测的执行状态和汇总指标。
 */
@Data
@TableName(value = "t_eval_run", autoResultMap = true)
public class EvaluationRunDO {

    /** 主键ID */
    @TableId(type = IdType.INPUT)
    private String id;

    /** 评测数据集ID */
    private String datasetId;

    /** 评测使用的Prompt版本标识 */
    private String promptVersion;

    /** 运行状态（running / completed / failed） */
    private String status;

    /** 运行开始时间 */
    private Date startedAt;

    /** 运行结束时间 */
    private Date finishedAt;

    /** 进度信息（JSON对象） */
    @TableField(typeHandler = JsonbStringTypeHandler.class)
    private String progressJson;

    /** 用例总数 */
    private Integer caseCount;

    /** 已完成用例数 */
    private Integer completedCaseCount;

    /** 失败用例数 */
    private Integer failedCaseCount;

    /** 汇总评测指标（JSON对象） */
    @TableField(typeHandler = JsonbStringTypeHandler.class)
    private String metricsJson;

    /** 创建人ID */
    private String createdBy;

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
