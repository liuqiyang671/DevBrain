package edu.cqupt.devbrain.user.dao.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.util.Date;

/**
 * 角色-权限关联实体 —— 对应数据库表 t_role_permission，维护角色与权限码的多对多关系。
 */
@Data
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
}
