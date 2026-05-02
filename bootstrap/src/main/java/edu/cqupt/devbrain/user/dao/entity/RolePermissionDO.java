package edu.cqupt.devbrain.user.dao.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;

import java.util.Date;

/**
 * 角色-权限关联实体 —— 对应数据库表 t_role_permission，维护角色与权限码的多对多关系。
 */
@TableName("t_role_permission")
public class RolePermissionDO {

    @TableId(type = IdType.ASSIGN_ID)
    private String id;
    /** 关联的角色ID */
    private String roleId;
    /** 关联的权限码ID */
    private String permissionId;
    @TableField(fill = FieldFill.INSERT)
    private Date createTime;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private Date updateTime;
    @TableLogic
    @TableField(fill = FieldFill.INSERT)
    private Integer deleted;

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getRoleId() { return roleId; }
    public void setRoleId(String roleId) { this.roleId = roleId; }
    public String getPermissionId() { return permissionId; }
    public void setPermissionId(String permissionId) { this.permissionId = permissionId; }
    public Date getCreateTime() { return createTime; }
    public void setCreateTime(Date createTime) { this.createTime = createTime; }
    public Date getUpdateTime() { return updateTime; }
    public void setUpdateTime(Date updateTime) { this.updateTime = updateTime; }
    public Integer getDeleted() { return deleted; }
    public void setDeleted(Integer deleted) { this.deleted = deleted; }
}
