package edu.cqupt.devbrain.commerce.guide.dao.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.FieldStrategy;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import edu.cqupt.devbrain.knowledge.dao.typehandler.JsonbStringTypeHandler;
import lombok.Data;

import java.util.Date;

/**
 * 导购会话实体，对应 t_guide_session 表。
 * <p>
 * 持久化导购对话的会话状态，包括：
 * <ul>
 *   <li><b>会话标识</b>：id / conversationId / userId</li>
 *   <li><b>会话阶段</b>：stage（clarifying-追问中 / recommended-已推荐）</li>
 *   <li><b>意图信息</b>：intent — 当前意图类型</li>
 *   <li><b>槽位状态</b>：slotJson — JSON 格式的槽位快照</li>
 *   <li><b>用户偏好</b>：preferenceJson — JSON 格式的用户偏好</li>
 *   <li><b>工作流状态</b>：graphStateJson — 完整工作流状态快照（用于会话恢复）</li>
 *   <li><b>归档信息</b>：archived / archivedTime / archiveSummary</li>
 * </ul>
 *
 * @author liuqiyang
 * @since 2026-05-15
 */
@Data
@TableName(value = "t_guide_session", autoResultMap = true)
public class GuideSessionDO {

    /** 主键ID */
    @TableId(type = IdType.ASSIGN_ID)
    private String id;

    /** 对话ID */
    private String conversationId;

    /** 用户ID */
    private String userId;

    /** 会话阶段：clarifying-追问中，recommended-已推荐 */
    private String stage;

    /** 当前意图类型 */
    private String intent;

    /** 槽位状态JSON */
    @TableField(typeHandler = JsonbStringTypeHandler.class)
    private String slotJson;

    /** 用户偏好JSON */
    @TableField(typeHandler = JsonbStringTypeHandler.class)
    private String preferenceJson;

    /** 完整工作流状态快照JSON（用于会话恢复） */
    @TableField(typeHandler = JsonbStringTypeHandler.class)
    private String graphStateJson;

    /** 是否已归档：0-当前会话，1-历史归档 */
    private Integer archived;

    /** 归档时间 */
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private Date archivedTime;

    /** 归档摘要 */
    private String archiveSummary;

    @TableField(fill = FieldFill.INSERT)
    private Date createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private Date updateTime;

    @TableLogic
    @TableField(fill = FieldFill.INSERT)
    private Integer deleted;
}
