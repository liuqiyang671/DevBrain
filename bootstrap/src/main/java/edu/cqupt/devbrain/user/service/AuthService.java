package edu.cqupt.devbrain.user.service;

import edu.cqupt.devbrain.user.controller.request.ForgotPasswordRequest;
import edu.cqupt.devbrain.user.controller.request.LoginRequest;
import edu.cqupt.devbrain.user.controller.request.RegisterRequest;
import edu.cqupt.devbrain.user.controller.request.ResetPasswordRequest;
import edu.cqupt.devbrain.user.controller.vo.CurrentUserVO;

/**
 * 认证服务接口 —— 定义用户注册、登录、密码找回与重置等核心认证操作。
 */
public interface AuthService {

    /**
     * 用户注册。
     * <p>
     * 校验用户名和邮箱唯一性，创建用户并分配默认角色，返回用户信息。
     *
     * @param request 注册请求
     * @return 注册成功的用户信息
     */
    CurrentUserVO register(RegisterRequest request);

    /**
     * 用户登录。
     * <p>
     * 校验用户名密码，签发 JWT 令牌，创建服务端会话，返回令牌和用户信息。
     *
     * @param request   登录请求
     * @param ipAddress 客户端 IP 地址，用于登录审计
     * @param userAgent 客户端 User-Agent，用于登录审计
     * @return 登录结果，包含 JWT 令牌和用户信息
     */
    LoginOutcome login(LoginRequest request, String ipAddress, String userAgent);

    /**
     * 忘记密码 —— 生成密码重置令牌。
     * <p>
     * 根据邮箱查找用户，生成重置令牌并将哈希存入数据库。
     * 若邮箱不存在则静默返回，不暴露用户是否存在的信息。
     *
     * @param request 忘记密码请求
     */
    void forgotPassword(ForgotPasswordRequest request);

    /**
     * 重置密码 —— 使用重置令牌设置新密码。
     * <p>
     * 校验令牌有效性（存在、未使用、未过期），通过后更新密码并标记令牌已使用。
     *
     * @param request 重置密码请求
     */
    void resetPassword(ResetPasswordRequest request);
}
