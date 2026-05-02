package edu.cqupt.devbrain.user.dao.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;

import java.util.Date;

/**
 * 权限码实体 —— 对应数据库表 t_permission，RBAC 模型中的权限定义。
 * <p>
 * 权限码是细粒度的操作权限标识（如 user:create、role:delete），
 * 通过 t_role_permission 与角色关联，再通过角色间接授权给用户。
 */
@TableName("t_permission")
public class PermissionDO {

    @TableId(type = IdType.ASSIGN_ID)
    private String id;
    /** 权限编码，全局唯一，如 user:create、role:delete */
    private String permissionCode;
    /** 权限显示名称 */
    private String permissionName;
    private String description;
    @TableField(fill = FieldFill.INSERT)
    private Date createTime;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private Date updateTime;
    @TableLogic
    @TableField(fill = FieldFill.INSERT)
    private Integer deleted;

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getPermissionCode() { return permissionCode; }
    public void setPermissionCode(String permissionCode) { this.permissionCode = permissionCode; }
    public String getPermissionName() { return permissionName; }
    public void setPermissionName(String permissionName) { this.permissionName = permissionName; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public Date getCreateTime() { return createTime; }
    public void setCreateTime(Date createTime) { this.createTime = createTime; }
    public Date getUpdateTime() { return updateTime; }
    public void setUpdateTime(Date updateTime) { this.updateTime = updateTime; }
    public Integer getDeleted() { return deleted; }
    public void setDeleted(Integer deleted) { this.deleted = deleted; }
}
