package edu.cqupt.devbrain.user.controller.vo;

import java.util.Date;
import java.util.Set;

/**
 * 用户视图 —— 用户管理接口返回的用户信息，包含角色和审计时间字段。
 *
 * @param id            用户ID
 * @param username      用户名
 * @param email         邮箱地址
 * @param displayName   显示名称
 * @param avatar        头像地址
 * @param status        账号状态（enabled/disabled）
 * @param roles         角色编码集合
 * @param lastLoginTime 最后登录时间
 * @param createTime    创建时间
 * @param updateTime    更新时间
 */
public record UserVO(
        String id,
        String username,
        String email,
        String displayName,
        String avatar,
        String status,
        Set<String> roles,
        Date lastLoginTime,
        Date createTime,
        Date updateTime
) {
}
