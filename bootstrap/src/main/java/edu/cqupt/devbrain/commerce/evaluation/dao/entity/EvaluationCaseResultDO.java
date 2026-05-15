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
 * 评测用例结果实体，对应数据库表 t_eval_result。
 * 记录单条评测用例在某次运行中的执行结果与评分详情。
 */
@Data
@TableName(value = "t_eval_result", autoResultMap = true)
public class EvaluationCaseResultDO {

    /** 主键ID */
    @TableId(type = IdType.INPUT)
    private String id;

    /** 所属评测运行ID */
    private String runId;

    /** 关联的评测用例ID */
    private String caseId;

    /** 实际生成的回答文本 */
    private String answer;

    /** 检索到的知识证据列表（JSON数组） */
    @TableField(typeHandler = JsonbStringTypeHandler.class)
    private String retrievedJson;

    /** 推荐的商品列表（JSON数组） */
    @TableField(typeHandler = JsonbStringTypeHandler.class)
    private String recommendationJson;

    /** 评分详情（JSON对象） */
    @TableField(typeHandler = JsonbStringTypeHandler.class)
    private String scoreJson;

    /** 决策追踪信息（JSON对象） */
    @TableField(typeHandler = JsonbStringTypeHandler.class)
    private String traceJson;

    /** 关联的 Agent Run ID */
    private String agentRunId;

    /** 失败归因类型 */
    private String failureType;

    /** 单用例延迟（毫秒） */
    private Long latencyMs;

    /** 期望输出快照（JSON对象） */
    @TableField(typeHandler = JsonbStringTypeHandler.class)
    private String expectedJson;

    /** 实际输出快照（JSON对象） */
    @TableField(typeHandler = JsonbStringTypeHandler.class)
    private String actualJson;

    /** 调试提示（JSON数组） */
    @TableField(typeHandler = JsonbStringTypeHandler.class)
    private String debugHints;

    /** 执行异常信息（非空表示用例执行失败） */
    private String errorMessage;

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
