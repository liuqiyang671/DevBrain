package edu.cqupt.devbrain.framework.context;

import com.alibaba.ttl.TransmittableThreadLocal;
import edu.cqupt.devbrain.framework.errorcode.BaseErrorCode;
import edu.cqupt.devbrain.framework.exception.ClientException;


/**
 * 线程级用户上下文持有器。
 * <p>基于 {@link com.alibaba.ttl.TransmittableThreadLocal} 实现，支持跨线程传播当前登录用户信息。
 * 提供用户信息的存取、清除以及便捷访问方法，在整个请求链路中传递认证上下文。</p>
 *
 * @see LoginUser
 */
public final class UserContext {

    private static final TransmittableThreadLocal<LoginUser> CONTEXT = new TransmittableThreadLocal<>();

    /**
     * 设置当前线程的用户上下文
     */
    public static void set(LoginUser user) {
        CONTEXT.set(user);
    }

    /**
     * 获取当前线程的用户上下文
     */
    public static LoginUser get() {
        return CONTEXT.get();
    }

    /**
     * 获取当前线程用户，若不存在则抛异常
     */
    public static LoginUser requireUser() {
        LoginUser user = CONTEXT.get();
        if (user == null) {
            throw new ClientException(BaseErrorCode.UNAUTHORIZED);
        }
        return user;
    }

    /**
     * 获取当前用户 ID（未登录返回 null）
     */
    public static String getUserId() {
        LoginUser user = CONTEXT.get();
        return user == null ? null : user.getUserId();
    }

    /**
     * 获取当前用户名（未登录返回 null）
     */
    public static String getUsername() {
        LoginUser user = CONTEXT.get();
        return user == null ? null : user.getUsername();
    }

    /**
     * 获取当前角色（未登录返回 null）
     */
    public static String getRole() {
        LoginUser user = CONTEXT.get();
        return user == null ? null : user.role();
    }

    /**
     * 获取当前头像（未登录返回 null）
     */
    public static String getAvatar() {
        LoginUser user = CONTEXT.get();
        return user == null ? null : user.getAvatar();
    }

    /**
     * 清理当前线程的用户上下文
     */
    public static void clear() {
        CONTEXT.remove();
    }

    /**
     * 判断是否已存在用户上下文
     */
    public static boolean hasUser() {
        return CONTEXT.get() != null;
    }
}
