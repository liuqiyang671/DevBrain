package edu.cqupt.devbrain.auth.core;

import org.springframework.stereotype.Service;

/**
 * 令牌会话服务 —— 管理服务端 JWT 会话状态。
 * <p>
 * 将已签发的 JWT 会话ID与用户ID的映射关系存储在 {@link SecurityCache} 中，
 * 实现服务端会话管理，支持主动注销和会话有效性校验。
 * <p>
 * 缓存键格式：auth:session:{sessionId}，值为 userId，TTL 与令牌有效期一致。
 * <p>
 * <b>设计说明</b>：仅凭 JWT 自身无法实现主动注销（令牌在过期前始终有效），
 * 通过服务端会话记录可以实现"令牌虽未过期但已被注销"的场景。
 */
@Service
public class TokenSessionService {

    private final SecurityCache cache;
    private final AuthSecurityProperties properties;

    public TokenSessionService(SecurityCache cache, AuthSecurityProperties properties) {
        this.cache = cache;
        this.properties = properties;
    }

    /**
     * 存储会话映射：将 sessionId → userId 写入缓存，TTL 为令牌有效期。
     *
     * @param claims JWT 声明信息，从中提取 sessionId 和 userId
     */
    public void store(JwtClaims claims) {
        cache.set(sessionKey(claims.sessionId()), claims.userId(), properties.getTokenTtl());
    }

    /**
     * 检查会话是否仍然有效。
     * <p>
     * 同时满足两个条件才视为有效：缓存中存在该 sessionId，且对应的 userId 与令牌中的 userId 一致。
     *
     * @param claims JWT 声明信息
     * @return true 表示会话有效，false 表示会话已注销或被篡改
     */
    public boolean exists(JwtClaims claims) {
        return cache.get(sessionKey(claims.sessionId())).filter(claims.userId()::equals).isPresent();
    }

    /**
     * 移除会话映射，用于注销登录。
     *
     * @param sessionId 待注销的会话ID
     */
    public void remove(String sessionId) {
        cache.delete(sessionKey(sessionId));
    }

    /**
     * 生成会话缓存键，格式：auth:session:{sessionId}
     */
    private String sessionKey(String sessionId) {
        return "auth:session:" + sessionId;
    }
}
