package edu.cqupt.devbrain.auth.core;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.HexFormat;

/**
 * 摘要与随机令牌工具类 —— 提供密码学安全的哈希和随机数生成能力。
 * <p>
 * 所有方法均为静态方法，工具类不可实例化。
 * <p>
 * 使用场景：
 * <ul>
 *   <li>{@link #randomToken()} — 生成密码重置令牌等一次性随机凭证</li>
 *   <li>{@link #sha256(String)} — 对敏感数据（如重置令牌）进行哈希后存储，避免明文入库</li>
 * </ul>
 */
public final class DigestSupport {

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private DigestSupport() {
    }

    /**
     * 生成 64 字符的随机十六进制令牌（32 字节随机数）。
     * <p>
     * 使用 {@link SecureRandom} 保证密码学安全性，适用于密码重置令牌等场景。
     *
     * @return 64 字符的十六进制随机令牌
     */
    public static String randomToken() {
        byte[] bytes = new byte[32];
        SECURE_RANDOM.nextBytes(bytes);
        return HexFormat.of().formatHex(bytes);
    }

    /**
     * 计算字符串的 SHA-256 哈希值，返回 64 字符的十六进制摘要。
     * <p>
     * 用于对敏感令牌进行哈希后存储，避免明文入库。
     * 例如密码重置令牌仅存储哈希值，原始令牌只通过邮件等渠道发送给用户。
     *
     * @param value 待哈希的原始字符串
     * @return SHA-256 哈希的十六进制表示
     * @throws IllegalStateException 当 SHA-256 算法不可用时（理论上不应发生）
     */
    public static String sha256(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception ex) {
            throw new IllegalStateException("SHA-256 digest unavailable", ex);
        }
    }
}
