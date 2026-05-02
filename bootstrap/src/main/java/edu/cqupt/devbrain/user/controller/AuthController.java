package edu.cqupt.devbrain.user.controller;

import edu.cqupt.devbrain.auth.core.CookieSupport;
import edu.cqupt.devbrain.auth.core.CsrfTokenService;
import edu.cqupt.devbrain.auth.core.InvalidTokenException;
import edu.cqupt.devbrain.auth.core.JwtClaims;
import edu.cqupt.devbrain.auth.core.JwtTokenService;
import edu.cqupt.devbrain.auth.core.TokenSessionService;
import edu.cqupt.devbrain.framework.convention.Result;
import edu.cqupt.devbrain.framework.web.Results;
import edu.cqupt.devbrain.user.controller.request.ForgotPasswordRequest;
import edu.cqupt.devbrain.user.controller.request.LoginRequest;
import edu.cqupt.devbrain.user.controller.request.RegisterRequest;
import edu.cqupt.devbrain.user.controller.request.ResetPasswordRequest;
import edu.cqupt.devbrain.user.controller.vo.CurrentUserVO;
import edu.cqupt.devbrain.user.controller.vo.LoginVO;
import edu.cqupt.devbrain.user.service.AuthService;
import edu.cqupt.devbrain.user.service.LoginOutcome;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * 认证控制器 —— 处理用户注册、登录、注销、密码找回与重置等认证相关请求。
 * <p>
 * 所有接口路径均以 /auth 前缀开头，其中注册、登录、CSRF 令牌获取、密码找回与重置为公开接口（免认证），
 * 注销接口需要用户已登录。
 * <p>
 * <b>业务流程</b>：
 * <ul>
 *   <li>注册：校验用户名/邮箱唯一性 → 创建用户 → 分配默认角色 → 返回用户信息</li>
 *   <li>登录：校验凭证 → 签发 JWT → 写入 Cookie → 创建服务端会话 → 返回用户信息</li>
 *   <li>注销：销毁服务端会话 → 清除客户端 Cookie</li>
 *   <li>忘记密码：生成重置令牌 → 记录哈希到数据库 → 输出令牌到日志（后续应改为邮件发送）</li>
 *   <li>重置密码：校验重置令牌 → 更新密码 → 标记令牌已使用</li>
 * </ul>
 */
@RestController
public class AuthController {

    private final AuthService authService;
    private final CsrfTokenService csrfTokenService;
    private final CookieSupport cookieSupport;
    private final JwtTokenService jwtTokenService;
    private final TokenSessionService tokenSessionService;

    public AuthController(AuthService authService, CsrfTokenService csrfTokenService, CookieSupport cookieSupport, JwtTokenService jwtTokenService, TokenSessionService tokenSessionService) {
        this.authService = authService;
        this.csrfTokenService = csrfTokenService;
        this.cookieSupport = cookieSupport;
        this.jwtTokenService = jwtTokenService;
        this.tokenSessionService = tokenSessionService;
    }

    /**
     * 获取 CSRF 令牌。
     * <p>
     * 签发新的 CSRF 令牌，同时写入 Cookie（XSRF-TOKEN）和返回响应体，
     * 前端应在后续写操作请求中通过 X-XSRF-TOKEN 请求头携带该令牌。
     *
     * @param response HTTP 响应对象，用于写入 CSRF Cookie
     * @return CSRF 令牌字符串
     */
    @GetMapping("/auth/csrf")
    public Result<String> csrf(HttpServletResponse response) {
        String token = csrfTokenService.issueToken();
        cookieSupport.writeCsrfCookie(response, token);
        return Results.success(token);
    }

    /**
     * 用户注册。
     * <p>
     * 校验用户名和邮箱的唯一性，创建用户并分配默认角色（user），
     * 返回注册后的用户信息（含角色和权限）。
     *
     * @param request 注册请求，包含用户名、邮箱、密码和可选的显示名称
     * @return 注册成功的用户信息
     */
    @PostMapping("/auth/register")
    public Result<CurrentUserVO> register(@RequestBody @Valid RegisterRequest request) {
        return Results.success(authService.register(request));
    }

    /**
     * 用户登录。
     * <p>
     * 校验用户名和密码，签发 JWT 令牌并写入 Cookie，创建服务端会话，
     * 更新最后登录时间，返回用户信息。
     *
     * @param request       登录请求，包含用户名和密码
     * @param servletRequest HTTP 请求对象，用于获取客户端 IP
     * @param response       HTTP 响应对象，用于写入 JWT Cookie
     * @return 登录结果，包含用户信息
     */
    @PostMapping("/auth/login")
    public Result<LoginVO> login(@RequestBody @Valid LoginRequest request, HttpServletRequest servletRequest, HttpServletResponse response) {
        LoginOutcome outcome = authService.login(request, clientIp(servletRequest), servletRequest.getHeader("User-Agent"));
        cookieSupport.writeTokenCookie(response, outcome.token());
        return Results.success(new LoginVO(outcome.user()));
    }

    /**
     * 用户注销。
     * <p>
     * 销毁服务端会话，清除客户端 JWT 和 CSRF Cookie。
     * 即使令牌已过期或格式异常，也会执行 Cookie 清理，确保客户端状态一致。
     *
     * @param request  HTTP 请求对象，用于读取 JWT Cookie
     * @param response HTTP 响应对象，用于清除 Cookie
     * @return 空结果
     */
    @PostMapping("/auth/logout")
    public Result<Void> logout(HttpServletRequest request, HttpServletResponse response) {
        String token = cookieSupport.readToken(request);
        try {
            if (token != null && !token.isBlank()) {
                JwtClaims claims = jwtTokenService.parseToken(token);
                tokenSessionService.remove(claims.sessionId());
            }
        } catch (InvalidTokenException ignored) {
            // Cookie cleanup must still complete when the client sends an expired or malformed token.
        } finally {
            cookieSupport.clearTokenCookie(response);
            cookieSupport.clearCsrfCookie(response);
        }
        return Results.success();
    }

    /**
     * 忘记密码 —— 申请密码重置。
     * <p>
     * 根据邮箱查找用户，生成重置令牌并记录到数据库（仅存哈希）。
     * 当前令牌通过日志输出，<b>生产环境应替换为邮件发送</b>。
     *
     * @param request 忘记密码请求，包含注册邮箱
     * @return 空结果（不暴露用户是否存在的信息）
     */
    @PostMapping("/auth/password/forgot")
    public Result<Void> forgotPassword(@RequestBody @Valid ForgotPasswordRequest request) {
        authService.forgotPassword(request);
        return Results.success();
    }

    /**
     * 重置密码 —— 使用重置令牌设置新密码。
     * <p>
     * 校验重置令牌的有效性（存在、未使用、未过期），通过后更新用户密码并标记令牌已使用。
     *
     * @param request 重置密码请求，包含重置令牌和新密码
     * @return 空结果
     */
    @PostMapping("/auth/password/reset")
    public Result<Void> resetPassword(@RequestBody @Valid ResetPasswordRequest request) {
        authService.resetPassword(request);
        return Results.success();
    }

    /**
     * 提取客户端真实 IP 地址。
     * <p>
     * 优先从 X-Forwarded-OR 请求头获取（支持反向代理场景），
     * 取第一个地址（最接近客户端的代理地址），否则使用远程地址。
     */
    private String clientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}
