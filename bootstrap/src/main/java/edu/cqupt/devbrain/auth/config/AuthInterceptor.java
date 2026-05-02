package edu.cqupt.devbrain.auth.config;

import edu.cqupt.devbrain.auth.core.AuthSecurityProperties;
import edu.cqupt.devbrain.auth.core.CookieSupport;
import edu.cqupt.devbrain.auth.core.CsrfTokenService;
import edu.cqupt.devbrain.auth.core.JwtClaims;
import edu.cqupt.devbrain.auth.core.JwtTokenService;
import edu.cqupt.devbrain.auth.core.TokenSessionService;
import edu.cqupt.devbrain.framework.context.LoginUser;
import edu.cqupt.devbrain.framework.context.UserContext;
import edu.cqupt.devbrain.framework.errorcode.BaseErrorCode;
import edu.cqupt.devbrain.framework.exception.ClientException;
import edu.cqupt.devbrain.user.dao.entity.UserDO;
import edu.cqupt.devbrain.user.service.AccessControlService;
import edu.cqupt.devbrain.user.service.CurrentUserAssembler;
import edu.cqupt.devbrain.user.service.UserAccountSupport;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * 认证拦截器 —— 核心请求鉴权与权限校验入口。
 * <p>
 * 拦截所有 HTTP 请求，按以下顺序执行安全检查：
 * <ol>
 *   <li><b>OPTIONS 放行</b>：预检请求直接通过，支持 CORS</li>
 *   <li><b>CSRF 校验</b>：对非安全方法（POST/PUT/PATCH/DELETE）验证 CSRF 令牌</li>
 *   <li><b>公开路径放行</b>：匹配 publicPaths 配置的路径跳过认证</li>
 *   <li><b>JWT 令牌解析</b>：从 Cookie 读取令牌并验证签名与有效期</li>
 *   <li><b>会话有效性校验</b>：确认令牌在服务端会话中存在（未被注销）</li>
 *   <li><b>用户状态校验</b>：确认用户账号处于启用状态</li>
 *   <li><b>权限校验</b>：基于资源访问控制规则检查用户是否具备访问权限</li>
 * </ol>
 * <p>
 * 认证通过后，将当前用户信息封装为 {@link LoginUser} 并存入 {@link UserContext}，
 * 供后续业务逻辑使用。请求完成后自动清理上下文，防止内存泄漏。
 */
@Component
public class AuthInterceptor implements HandlerInterceptor {

    private final AuthSecurityProperties properties;
    private final CookieSupport cookieSupport;
    private final CsrfTokenService csrfTokenService;
    private final JwtTokenService jwtTokenService;
    private final TokenSessionService tokenSessionService;
    private final UserAccountSupport accountSupport;
    private final CurrentUserAssembler currentUserAssembler;
    private final AccessControlService accessControlService;
    private final AntPathMatcher pathMatcher = new AntPathMatcher();

    public AuthInterceptor(
            AuthSecurityProperties properties,
            CookieSupport cookieSupport,
            CsrfTokenService csrfTokenService,
            JwtTokenService jwtTokenService,
            TokenSessionService tokenSessionService,
            UserAccountSupport accountSupport,
            CurrentUserAssembler currentUserAssembler,
            AccessControlService accessControlService) {
        this.properties = properties;
        this.cookieSupport = cookieSupport;
        this.csrfTokenService = csrfTokenService;
        this.jwtTokenService = jwtTokenService;
        this.tokenSessionService = tokenSessionService;
        this.accountSupport = accountSupport;
        this.currentUserAssembler = currentUserAssembler;
        this.accessControlService = accessControlService;
    }

    /**
     * 请求预处理 —— 执行完整的认证与权限校验链路。
     * <p>
     * 校验通过后设置用户上下文，校验失败抛出对应异常由全局异常处理器统一处理。
     */
    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
            return true;
        }
        String path = request.getRequestURI();
        String contextPath = request.getContextPath();
        if (contextPath != null && !contextPath.isBlank() && path.startsWith(contextPath)) {
            path = path.substring(contextPath.length());
        }
        if (isPublicPath(path)) {
            return true;
        }
        if (isUnsafeMethod(request.getMethod())) {
            csrfTokenService.validate(request);
        }
        String token = cookieSupport.readToken(request);
        if (token == null || token.isBlank()) {
            throw new ClientException(BaseErrorCode.UNAUTHORIZED);
        }
        JwtClaims claims = jwtTokenService.parseToken(token);
        if (!tokenSessionService.exists(claims)) {
            throw new ClientException(BaseErrorCode.UNAUTHORIZED);
        }
        UserDO user = accountSupport.requireEnabledUser(claims.userId());
        LoginUser loginUser = currentUserAssembler.toLoginUser(user);
        UserContext.set(loginUser);
        accessControlService.checkAccess(request.getMethod(), path, loginUser);
        return true;
    }

    /**
     * 请求完成回调 —— 清理用户上下文，防止线程复用导致的信息泄漏。
     */
    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) {
        UserContext.clear();
    }

    /**
     * 判断请求路径是否为公开路径（免认证）。
     * <p>
     * 使用 Ant 风格路径匹配，支持通配符。
     */
    private boolean isPublicPath(String path) {
        return properties.getPublicPaths().stream().anyMatch(pattern -> pathMatcher.match(pattern, path));
    }

    /**
     * 判断 HTTP 方法是否为非安全方法（需要 CSRF 校验）。
     * <p>
     * POST、PUT、PATCH、DELETE 属于写操作，需要 CSRF 保护；
     * GET、HEAD、OPTIONS 属于安全方法，无需 CSRF 校验。
     */
    private boolean isUnsafeMethod(String method) {
        return "POST".equalsIgnoreCase(method) || "PUT".equalsIgnoreCase(method)
                || "PATCH".equalsIgnoreCase(method) || "DELETE".equalsIgnoreCase(method);
    }
}
