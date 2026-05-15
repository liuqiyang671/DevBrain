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
 * 评测用例实体，对应数据库表 t_eval_case。
 * 定义单条导购场景的测试输入与预期输出，用于自动化评测。
 */
@Data
@TableName(value = "t_eval_case", autoResultMap = true)
public class EvaluationCaseDO {

    /** 主键ID */
    @TableId(type = IdType.INPUT)
    private String id;

    /** 所属数据集ID */
    private String datasetId;

    /** 用例编号 */
    private String caseNo;

    /** 测试场景描述 */
    private String scenario;

    /** 用户提问文本 */
    private String question;

    /** 多轮对话历史（JSON数组） */
    @TableField(typeHandler = JsonbStringTypeHandler.class)
    private String turnsJson;

    /** 额外上下文信息（JSON对象） */
    @TableField(typeHandler = JsonbStringTypeHandler.class)
    private String contextJson;

    /** 期望的标准回答 */
    private String expectedAnswer;

    /** 期望的意图分类 */
    private String expectedIntent;

    /** 期望的槽位信息（JSON对象） */
    @TableField(typeHandler = JsonbStringTypeHandler.class)
    private String expectedSlots;

    /** 期望命中的商品ID列表（JSON数组） */
    @TableField(typeHandler = JsonbStringTypeHandler.class)
    private String expectedProductIds;

    /** 期望命中的知识分块ID列表（JSON数组） */
    @TableField(typeHandler = JsonbStringTypeHandler.class)
    private String expectedChunkIds;

    /** 必须命中的关键词列表（JSON数组） */
    @TableField(typeHandler = JsonbStringTypeHandler.class)
    private String mustHitKeywords;

    /** 禁止出现的声明内容列表（JSON数组） */
    @TableField(typeHandler = JsonbStringTypeHandler.class)
    private String forbiddenClaims;

    /** 用例标签列表（JSON数组） */
    @TableField(typeHandler = JsonbStringTypeHandler.class)
    private String tags;

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
