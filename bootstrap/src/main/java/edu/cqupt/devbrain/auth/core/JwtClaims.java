package edu.cqupt.devbrain.auth.core;

import java.time.Instant;
import java.util.Set;

/**
 * JWT 令牌声明信息 —— 解析 JWT 后封装的载体对象。
 * <p>
 * 包含令牌中的核心业务字段，用于在请求拦截、权限校验等环节传递用户身份与权限信息。
 *
 * @param sessionId   会话ID，对应 Payload 中的 sid，用于服务端会话管理
 * @param userId      用户ID，对应 Payload 中的 sub
 * @param username    用户名
 * @param roles       用户角色编码集合
 * @param permissions 用户权限编码集合
 * @param expiresAt   令牌过期时间
 */
public record JwtClaims(
        String sessionId,
        String userId,
        String username,
        Set<String> roles,
        Set<String> permissions,
        Instant expiresAt
) {
}
