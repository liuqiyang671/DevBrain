package edu.cqupt.devbrain.user.controller.vo;

import java.util.Set;

/**
 * 当前用户视图 —— 包含用户基本信息及角色、权限集合，用于登录/注册/个人资料等接口的响应。
 *
 * @param userId      用户ID
 * @param username    用户名
 * @param email       邮箱地址
 * @param displayName 显示名称
 * @param avatar      头像地址
 * @param roles       角色编码集合
 * @param permissions 权限编码集合
 */
public record CurrentUserVO(
        String userId,
        String username,
        String email,
        String displayName,
        String avatar,
        Set<String> roles,
        Set<String> permissions
) {
}
