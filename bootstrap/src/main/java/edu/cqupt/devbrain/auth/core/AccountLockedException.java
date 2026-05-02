package edu.cqupt.devbrain.auth.core;

import edu.cqupt.devbrain.framework.errorcode.BaseErrorCode;
import edu.cqupt.devbrain.framework.exception.ClientException;

/**
 * 账号锁定异常 —— 当账号因连续登录失败次数达到阈值而被锁定时抛出。
 * <p>
 * 锁定机制由 {@link LoginAttemptGuard} 管理，锁定时长由 {@link AuthSecurityProperties#getAccountLockDuration()} 配置。
 */
public class AccountLockedException extends ClientException {

    public AccountLockedException() {
        super("账号暂时锁定，请稍后再试", BaseErrorCode.ACCOUNT_LOCKED);
    }
}
