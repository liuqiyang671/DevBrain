package edu.cqupt.devbrain.framework.context;

import java.util.Set;

/**
 * 当前登录用户的上下文快照。
 *
 * <p>保留 JavaBean 风格 getter，兼容旧代码；同时提供 record 风格访问器，
 * 方便业务层以不可变快照方式读取认证信息。</p>
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

    public boolean isAdmin() {
        return roles.contains("admin");
    }

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
