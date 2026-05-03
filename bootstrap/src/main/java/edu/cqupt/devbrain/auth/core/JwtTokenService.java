package edu.cqupt.devbrain.auth.core;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * JWT 令牌服务 —— 负责令牌的签发与解析验证。
 * <p>
 * 采用 HMAC-SHA256 对称签名算法，不依赖外部 JWT 库，自行实现编解码与签名逻辑。
 * 令牌结构遵循标准 JWT 三段式：Header.Payload.Signature。
 * <p>
 * Payload 中包含的字段：
 * <ul>
 *   <li>sid — 会话ID，用于服务端会话管理</li>
 *   <li>sub — 用户ID</li>
 *   <li>username — 用户名</li>
 *   <li>roles — 角色编码集合（排序后保证一致性）</li>
 *   <li>permissions — 权限编码集合（排序后保证一致性）</li>
 *   <li>iat — 签发时间（Unix 时间戳）</li>
 *   <li>exp — 过期时间（Unix 时间戳）</li>
 * </ul>
 * <p>
 * <b>安全注意</b>：签名密钥通过 {@link AuthSecurityProperties#getJwtSecret()} 配置，
 * 生产环境务必替换默认值并妥善保管。
 */
@Component
@RequiredArgsConstructor
public class JwtTokenService {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final Base64.Encoder BASE64_URL_ENCODER = Base64.getUrlEncoder().withoutPadding();
    private static final Base64.Decoder BASE64_URL_DECODER = Base64.getUrlDecoder();

    private final AuthSecurityProperties properties;

    /**
     * 签发 JWT 令牌。
     * <p>
     * 生成流程：构建 Header 和 Payload → Base64URL 编码 → HMAC-SHA256 签名 → 拼接三段。
     * 角色和权限列表排序后写入，确保相同输入产生相同令牌（便于缓存和对比）。
     *
     * @param userId      用户ID，写入 sub 声明
     * @param username    用户名，写入 username 声明
     * @param roles       用户角色编码集合
     * @param permissions 用户权限编码集合
     * @return 完整的 JWT 字符串（Header.Payload.Signature）
     * @throws InvalidTokenException 令牌生成过程中发生异常
     */
    public String createToken(String userId, String username, Set<String> roles, Set<String> permissions) {
        try {
            Instant now = Instant.now();
            Map<String, Object> header = Map.of("alg", "HS256", "typ", "JWT");
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("sid", UUID.randomUUID().toString().replace("-", ""));
            payload.put("sub", userId);
            payload.put("username", username);
            payload.put("roles", roles == null ? List.of() : roles.stream().sorted().toList());
            payload.put("permissions", permissions == null ? List.of() : permissions.stream().sorted().toList());
            payload.put("iat", now.getEpochSecond());
            payload.put("exp", now.plus(properties.getTokenTtl()).getEpochSecond());
            String unsigned = encodeJson(header) + "." + encodeJson(payload);
            return unsigned + "." + sign(unsigned);
        } catch (Exception ex) {
            throw new InvalidTokenException("令牌生成失败");
        }
    }

    /**
     * 解析并验证 JWT 令牌。
     * <p>
     * 验证流程：
     * <ol>
     *   <li>拆分三段，格式校验</li>
     *   <li>重新计算签名并与令牌中的签名进行常量时间比较（防时序攻击）</li>
     *   <li>解码 Payload，检查过期时间</li>
     *   <li>封装为 {@link JwtClaims} 返回</li>
     * </ol>
     *
     * @param token 待解析的 JWT 字符串
     * @return 解析后的令牌声明信息
     * @throws InvalidTokenException 令牌格式无效、签名不匹配或已过期
     */
    public JwtClaims parseToken(String token) {
        try {
            String[] parts = token == null ? new String[0] : token.split("\\.");
            if (parts.length != 3) {
                throw new InvalidTokenException("登录令牌格式无效");
            }
            String unsigned = parts[0] + "." + parts[1];
            if (!MessageDigest.isEqual(sign(unsigned).getBytes(StandardCharsets.UTF_8), parts[2].getBytes(StandardCharsets.UTF_8))) {
                throw new InvalidTokenException("登录令牌签名无效");
            }
            Map<String, Object> payload = OBJECT_MAPPER.readValue(
                    BASE64_URL_DECODER.decode(parts[1]),
                    new TypeReference<>() {
                    }
            );
            Instant expiresAt = Instant.ofEpochSecond(asLong(payload.get("exp")));
            if (expiresAt.isBefore(Instant.now())) {
                throw new InvalidTokenException("登录已过期");
            }
            return new JwtClaims(
                    asString(payload.get("sid")),
                    asString(payload.get("sub")),
                    asString(payload.get("username")),
                    asStringSet(payload.get("roles")),
                    asStringSet(payload.get("permissions")),
                    expiresAt
            );
        } catch (InvalidTokenException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new InvalidTokenException("登录令牌无效");
        }
    }

    /**
     * 将 Map 序列化为 JSON 并进行 Base64URL 编码（无填充）。
     */
    private String encodeJson(Map<String, Object> value) throws Exception {
        return BASE64_URL_ENCODER.encodeToString(OBJECT_MAPPER.writeValueAsBytes(value));
    }

    /**
     * 使用 HMAC-SHA256 对无签名部分进行签名，返回 Base64URL 编码的签名值。
     */
    private String sign(String unsigned) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(properties.getJwtSecret().getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        return BASE64_URL_ENCODER.encodeToString(mac.doFinal(unsigned.getBytes(StandardCharsets.UTF_8)));
    }

    /**
     * 安全地将对象转换为字符串，null 返回空串。
     */
    private static String asString(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    /**
     * 安全地将对象转换为 long 值，支持 Number 类型和字符串类型。
     */
    private static long asLong(Object value) {
        if (value instanceof Number number) {
            return number.longValue();
        }
        return Long.parseLong(String.valueOf(value));
    }

    /**
     * 将 Payload 中的列表字段转换为不可变的字符串集合。
     * 若值不是 List 类型则返回空集合。
     */
    private static Set<String> asStringSet(Object value) {
        if (value instanceof List<?> list) {
            return list.stream().map(String::valueOf).collect(java.util.stream.Collectors.toUnmodifiableSet());
        }
        return Set.of();
    }
}
