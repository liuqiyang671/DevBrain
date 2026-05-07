package edu.cqupt.devbrain.auth.core;

import edu.cqupt.devbrain.framework.errorcode.BaseErrorCode;
import edu.cqupt.devbrain.framework.exception.ClientException;

/**
 * 登录频率超限异常 —— 当同一 IP 在指定时间窗口内的登录尝试次数超过上限时抛出。
 * <p>
 * IP 限流机制由 {@link LoginAttemptGuard} 管理，阈值和窗口由
 * {@link AuthSecurityProperties#getIpLoginMaxAttempts()} 和 {@link AuthSecurityProperties#getIpLoginWindow()} 配置。
 */
public class RateLimitExceededException extends ClientException {

    /**
     * 创建登录频率超限异常，使用默认错误消息和错误码。
     */
    public RateLimitExceededException() {
        super("登录尝试过于频繁，请稍后再试", BaseErrorCode.LOGIN_RATE_LIMIT);
    }
}
