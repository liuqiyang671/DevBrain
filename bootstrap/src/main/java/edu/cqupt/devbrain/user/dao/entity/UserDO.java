package edu.cqupt.devbrain.user.dao.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;

import java.util.Date;

/**
 * 用户实体 —— 对应数据库表 t_user，存储用户基本信息。
 * <p>
 * 核心字段说明：
 * <ul>
 *   <li>id — 用户唯一标识，使用雪花算法自动生成</li>
 *   <li>username — 用户名，全局唯一</li>
 *   <li>email — 邮箱地址，全局唯一</li>
 *   <li>passwordHash — BCrypt 哈希后的密码，禁止保存明文</li>
 *   <li>status — 账号状态：enabled（启用）/ disabled（禁用），禁用用户不能登录</li>
 *   <li>deleted — 逻辑删除标志，0 表示未删除，1 表示已删除</li>
 * </ul>
 */
@TableName("t_user")
public class UserDO {

    @TableId(type = IdType.ASSIGN_ID)
    private String id;

    private String username;

    private String email;

    /** BCrypt 哈希后的密码，禁止保存明文。 */
    private String passwordHash;

    private String displayName;

    private String avatar;

    /** enabled / disabled，禁用用户不能登录。 */
    private String status;

    private Date lastLoginTime;

    @TableField(fill = FieldFill.INSERT)
    private Date createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private Date updateTime;

    @TableLogic
    @TableField(fill = FieldFill.INSERT)
    private Integer deleted;

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public void setPasswordHash(String passwordHash) {
        this.passwordHash = passwordHash;
    }

    public String getDisplayName() {
        return displayName;
    }

    public void setDisplayName(String displayName) {
        this.displayName = displayName;
    }

    public String getAvatar() {
        return avatar;
    }

    public void setAvatar(String avatar) {
        this.avatar = avatar;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public Date getLastLoginTime() {
        return lastLoginTime;
    }

    public void setLastLoginTime(Date lastLoginTime) {
        this.lastLoginTime = lastLoginTime;
    }

    public Date getCreateTime() {
        return createTime;
    }

    public void setCreateTime(Date createTime) {
        this.createTime = createTime;
    }

    public Date getUpdateTime() {
        return updateTime;
    }

    public void setUpdateTime(Date updateTime) {
        this.updateTime = updateTime;
    }

    public Integer getDeleted() {
        return deleted;
    }

    public void setDeleted(Integer deleted) {
        this.deleted = deleted;
    }
}
