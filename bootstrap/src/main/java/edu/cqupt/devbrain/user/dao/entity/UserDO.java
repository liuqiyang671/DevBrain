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
@Data
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
}
