package edu.cqupt.devbrain.framework.context;

import java.util.Set;

/**
 * 当前登录用户的不可变上下文快照。
 * <p>使用 Java record 实现，所有字段在构造时进行防御性拷贝，确保实例创建后不可修改。
 * 保留 JavaBean 风格 getter 以兼容旧代码，同时提供 record 风格访问器，
 * 方便业务层以不可变快照方式读取认证信息。</p>
 *
 * @param userId       用户唯一标识
 * @param username     用户名
 * @param email        邮箱地址
 * @param displayName  显示名称
 * @param avatar       头像 URL
 * @param roles        角色集合（不可变）
 * @param permissions  权限集合（不可变）
 * @see UserContext
 */
public record LoginUser(
        String userId,
        String username,
        String email,
        String displayName,
        String avatar,
        Set<String> roles,
        Set<String> permissions
) {

    public LoginUser {
        roles = roles == null ? Set.of() : Set.copyOf(roles);
        permissions = permissions == null ? Set.of() : Set.copyOf(permissions);
    }

    /**
     * 判断当前用户是否为管理员角色
     *
     * @return 是管理员返回 true，否则返回 false
     */
    public boolean isAdmin() {
        return roles.contains("admin");
    }

    /**
     * 获取用户的主要角色（按字典序排列后的第一个）
     *
     * @return 角色名称，无角色时返回 null
     */
    public String role() {
        return roles.stream().sorted().findFirst().orElse(null);
    }

    public String getUserId() {
        return userId;
    }

    public String getUsername() {
        return username;
    }

    public String getEmail() {
        return email;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getAvatar() {
        return avatar;
    }

    public String getRole() {
        return role();
    }

    public Set<String> getRoles() {
        return roles;
    }

    public Set<String> getPermissions() {
        return permissions;
    }
}
