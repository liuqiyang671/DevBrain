package edu.cqupt.devbrain.user.dao.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.util.Date;

/**
 * 登录审计实体 —— 对应数据库表 t_login_audit，记录用户登录行为用于安全审计。
 * <p>
 * 记录每次登录尝试的用户名、IP 地址、User-Agent、成功/失败状态及失败原因，
 * 可用于异常登录检测和安全事件追溯。
 */
@Data
@TableName("t_login_audit")
public class LoginAuditDO {

    @TableId(type = IdType.ASSIGN_ID)
    private String id;
    /** 登录时使用的用户名 */
    private String username;
    /** 登录用户ID（登录成功时有值） */
    private String userId;
    /** 请求来源 IP 地址 */
    private String ipAddress;
    /** 客户端 User-Agent */
    private String userAgent;
    /** 登录是否成功：1 成功，0 失败 */
    private Integer success;
    /** 登录失败原因 */
    private String failureReason;
    @TableField(fill = FieldFill.INSERT)
    private Date createTime;
}
