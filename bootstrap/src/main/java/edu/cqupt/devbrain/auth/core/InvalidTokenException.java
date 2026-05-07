package edu.cqupt.devbrain.auth.core;

import edu.cqupt.devbrain.framework.errorcode.BaseErrorCode;
import edu.cqupt.devbrain.framework.exception.ClientException;

/**
 * 无效令牌异常 —— 当 JWT 令牌或 CSRF 令牌验证失败时抛出。
 * <p>
 * 常见触发场景：
 * <ul>
 *   <li>JWT 令牌格式不正确、签名无效或已过期</li>
 *   <li>CSRF 令牌校验失败（请求头与 Cookie 不匹配或令牌已过期）</li>
 * </ul>
 */
public class InvalidTokenException extends ClientException {

    /**
     * 创建无效令牌异常。
     *
     * @param message 具体的错误描述
     */
    public InvalidTokenException(String message) {
        super(message, BaseErrorCode.UNAUTHORIZED);
    }
}
