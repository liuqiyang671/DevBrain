package edu.cqupt.devbrain.user.dao.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;

import java.util.Date;

/**
 * 密码重置令牌实体 —— 对应数据库表 t_password_reset_token，用于"忘记密码"流程。
 * <p>
 * 安全设计：原始重置令牌只发给用户（当前通过日志输出，生产环境应改为邮件发送），
 * 数据库仅保存 SHA-256 哈希值，避免令牌泄露风险。
 * <p>
 * 令牌状态管理：
 * <ul>
 *   <li>used=0 — 未使用，可用于重置密码</li>
 *   <li>used=1 — 已使用，不可再次使用</li>
 *   <li>expireTime — 过期时间，过期后不可使用</li>
 * </ul>
 */
@TableName("t_password_reset_token")
public class PasswordResetTokenDO {

    @TableId(type = IdType.ASSIGN_ID)
    private String id;
    /** 关联的用户ID */
    private String userId;
    /** 原始重置令牌只发给用户，数据库仅保存 SHA-256 哈希。 */
    private String tokenHash;
    /** 令牌过期时间 */
    private Date expireTime;
    /** 是否已使用：0 未使用，1 已使用 */
    private Integer used;
    @TableField(fill = FieldFill.INSERT)
    private Date createTime;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private Date updateTime;
    @TableLogic
    @TableField(fill = FieldFill.INSERT)
    private Integer deleted;

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }
    public String getTokenHash() { return tokenHash; }
    public void setTokenHash(String tokenHash) { this.tokenHash = tokenHash; }
    public Date getExpireTime() { return expireTime; }
    public void setExpireTime(Date expireTime) { this.expireTime = expireTime; }
    public Integer getUsed() { return used; }
    public void setUsed(Integer used) { this.used = used; }
    public Date getCreateTime() { return createTime; }
    public void setCreateTime(Date createTime) { this.createTime = createTime; }
    public Date getUpdateTime() { return updateTime; }
    public void setUpdateTime(Date updateTime) { this.updateTime = updateTime; }
    public Integer getDeleted() { return deleted; }
    public void setDeleted(Integer deleted) { this.deleted = deleted; }
}
