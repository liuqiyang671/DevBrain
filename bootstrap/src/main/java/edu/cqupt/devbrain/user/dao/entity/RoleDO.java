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
 * 角色实体 —— 对应数据库表 t_role，RBAC 模型中的角色定义。
 * <p>
 * 角色通过 roleCode 进行业务标识（如 admin、user），
 * 通过 t_user_role 关联用户，通过 t_role_permission 关联权限码。
 */
@Data
@TableName("t_role")
public class RoleDO {

    @TableId(type = IdType.ASSIGN_ID)
    private String id;
    /** 角色编码，全局唯一，如 admin、user */
    private String roleCode;
    /** 角色显示名称，如 管理员、普通用户 */
    private String roleName;
    private String description;
    @TableField(fill = FieldFill.INSERT)
    private Date createTime;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private Date updateTime;
    @TableLogic
    @TableField(fill = FieldFill.INSERT)
    private Integer deleted;
}
