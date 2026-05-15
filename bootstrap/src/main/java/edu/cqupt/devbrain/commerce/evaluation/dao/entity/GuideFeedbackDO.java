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
 * 导购反馈实体，对应数据库表 t_guide_feedback。
 * 记录用户对导购会话结果的反馈意见及审核处理状态。
 */
@Data
@TableName("t_guide_feedback")
public class GuideFeedbackDO {

    /** 主键ID */
    @TableId(type = IdType.INPUT)
    private String id;

    /** 关联的会话ID */
    private String conversationId;

    /** 关联的消息ID */
    private String messageId;

    /** 涉及的商品ID */
    private String productId;

    /** 反馈类型（如：positive / negative / suggestion） */
    private String feedbackType;

    /** 用户反馈评论内容 */
    private String comment;

    /** 反馈目标类型：answer/product/reason/evidence/tool_step/session */
    private String targetType;

    /** 反馈目标ID */
    private String targetId;

    /** 关联的 Agent 运行ID */
    private String agentRunId;

    /** 关联的 Agent 步骤ID */
    private String stepId;

    /** 关联的证据ID或 docId#chunkId */
    private String evidenceId;

    /** 推荐理由序号 */
    private Integer reasonIndex;

    /** 审核状态（pending / reviewing / resolved / ignored） */
    private String reviewStatus;

    /** 审核结果说明 */
    private String reviewResult;

    /** 审核后生成的改进建议 */
    private String improvementSuggestion;

    /** 创建人ID（反馈提交者） */
    private String createdBy;

    /** 审核人ID */
    private String reviewedBy;

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
