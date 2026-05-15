package edu.cqupt.devbrain.commerce.guide.dao.entity;

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
 * 导购消息实体，对应 t_guide_message 表。
 * <p>
 * 记录导购对话中的每条消息，包括用户消息和助手回复：
 * <ul>
 *   <li><b>消息标识</b>：id / conversationId / sessionId / userId</li>
 *   <li><b>消息内容</b>：role（user/assistant）/ content / imageRefsJson</li>
 *   <li><b>幂等控制</b>：clientMessageId — 客户端消息 ID</li>
 *   <li><b>Agent 关联</b>：agentRunId — Agent 运行 ID</li>
 * </ul>
 *
 * @author liuqiyang
 * @since 2026-05-15
 */
@Data
@TableName(value = "t_guide_message", autoResultMap = true)
public class GuideMessageDO {

    @TableId(type = IdType.ASSIGN_ID)
    private String id;

    private String conversationId;

    private String sessionId;

    private String userId;

    private String role;

    private String content;

    @TableField(typeHandler = JsonbStringTypeHandler.class)
    private String imageRefsJson;

    private String clientMessageId;

    private String agentRunId;

    @TableField(fill = FieldFill.INSERT)
    private Date createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private Date updateTime;

    @TableLogic
    @TableField(fill = FieldFill.INSERT)
    private Integer deleted;
}
