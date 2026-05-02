package edu.cqupt.devbrain.user.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import edu.cqupt.devbrain.auth.core.DigestSupport;
import edu.cqupt.devbrain.auth.core.JwtClaims;
import edu.cqupt.devbrain.auth.core.JwtTokenService;
import edu.cqupt.devbrain.auth.core.TokenSessionService;
import edu.cqupt.devbrain.framework.errorcode.BaseErrorCode;
import edu.cqupt.devbrain.framework.exception.ClientException;
import edu.cqupt.devbrain.user.controller.request.ForgotPasswordRequest;
import edu.cqupt.devbrain.user.controller.request.LoginRequest;
import edu.cqupt.devbrain.user.controller.request.RegisterRequest;
import edu.cqupt.devbrain.user.controller.request.ResetPasswordRequest;
import edu.cqupt.devbrain.user.controller.vo.CurrentUserVO;
import edu.cqupt.devbrain.user.dao.entity.PasswordResetTokenDO;
import edu.cqupt.devbrain.user.dao.entity.UserDO;
import edu.cqupt.devbrain.user.dao.mapper.PasswordResetTokenMapper;
import edu.cqupt.devbrain.user.dao.mapper.UserMapper;
import edu.cqupt.devbrain.user.service.AuthService;
import edu.cqupt.devbrain.user.service.CurrentUserAssembler;
import edu.cqupt.devbrain.user.service.LoginOutcome;
import edu.cqupt.devbrain.user.service.UserAccountSupport;
import edu.cqupt.devbrain.user.service.UserDirectoryService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.Duration;
import java.util.Date;
import java.util.Set;

/**
 * 认证服务实现 —— 处理用户注册、登录、密码找回与重置的核心业务逻辑。
 * <p>
 * <b>安全设计要点</b>：
 * <ul>
 *   <li>密码使用 BCrypt 哈希存储，永不明文入库</li>
 *   <li>密码重置令牌仅存储 SHA-256 哈希值，原始令牌不落库</li>
 *   <li>登录失败不区分"用户不存在"和"密码错误"，防止用户名枚举攻击</li>
 *   <li>忘记密码接口无论邮箱是否存在都返回成功，不暴露用户注册信息</li>
 * </ul>
 */
@Service
public class AuthServiceImpl implements AuthService {

    private static final Logger log = LoggerFactory.getLogger(AuthServiceImpl.class);

    private final UserMapper userMapper;
    private final PasswordResetTokenMapper passwordResetTokenMapper;
    private final UserDirectoryService directoryService;
    private final CurrentUserAssembler currentUserAssembler;
    private final UserAccountSupport accountSupport;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenService jwtTokenService;
    private final TokenSessionService tokenSessionService;

    public AuthServiceImpl(
            UserMapper userMapper,
            PasswordResetTokenMapper passwordResetTokenMapper,
            UserDirectoryService directoryService,
            CurrentUserAssembler currentUserAssembler,
            UserAccountSupport accountSupport,
            PasswordEncoder passwordEncoder,
            JwtTokenService jwtTokenService,
            TokenSessionService tokenSessionService) {
        this.userMapper = userMapper;
        this.passwordResetTokenMapper = passwordResetTokenMapper;
        this.directoryService = directoryService;
        this.currentUserAssembler = currentUserAssembler;
        this.accountSupport = accountSupport;
        this.passwordEncoder = passwordEncoder;
        this.jwtTokenService = jwtTokenService;
        this.tokenSessionService = tokenSessionService;
    }

    /**
     * 用户注册。
     * <p>
     * 流程：校验唯一性 → 创建用户（密码 BCrypt 加密）→ 分配默认角色 "user" → 返回用户信息。
     * 显示名称为空时默认使用用户名。
     */
    @Override
    @Transactional
    public CurrentUserVO register(RegisterRequest request) {
        accountSupport.ensureUsernameOrEmailAvailable(request.username(), request.email(), null);
        UserDO user = new UserDO();
        user.setUsername(accountSupport.clean(request.username()));
        user.setEmail(accountSupport.clean(request.email()));
        user.setPasswordHash(passwordEncoder.encode(request.password()));
        user.setDisplayName(StringUtils.hasText(request.displayName())
                ? accountSupport.clean(request.displayName())
                : accountSupport.clean(request.username()));
        user.setStatus(UserAccountSupport.STATUS_ENABLED);
        userMapper.insert(user);
        directoryService.assignRoles(user.getId(), Set.of(UserDirectoryService.DEFAULT_ROLE));
        return currentUserAssembler.toCurrentUser(user);
    }

    /**
     * 用户登录。
     * <p>
     * 流程：校验用户名密码 → 校验账号状态 → 加载角色权限 → 签发 JWT → 创建会话 → 更新登录时间 → 返回结果。
     * <p>
     * <b>安全策略</b>：用户不存在和密码错误返回相同提示，防止用户名枚举。
     */
    @Override
    @Transactional
    public LoginOutcome login(LoginRequest request, String ipAddress, String userAgent) {
        String username = accountSupport.clean(request.username());
        UserDO user = accountSupport.findByUsername(username);
        if (user == null || !passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            throw new ClientException("用户名或密码错误");
        }
        if (!accountSupport.isEnabled(user)) {
            throw new ClientException("账号已禁用", BaseErrorCode.FORBIDDEN);
        }
        Set<String> roles = directoryService.roleCodesByUser(user.getId());
        Set<String> permissions = directoryService.permissionCodesByRoles(roles);
        String token = jwtTokenService.createToken(user.getId(), user.getUsername(), roles, permissions);
        JwtClaims claims = jwtTokenService.parseToken(token);
        tokenSessionService.store(claims);
        user.setLastLoginTime(new Date());
        userMapper.updateById(user);
        return new LoginOutcome(token, currentUserAssembler.toCurrentUser(user, roles, permissions));
    }

    /**
     * 忘记密码 —— 生成密码重置令牌。
     * <p>
     * 流程：根据邮箱查找用户 → 生成随机令牌 → 存储 SHA-256 哈希到数据库 → 输出原始令牌到日志。
     * <p>
     * <b>注意</b>：当前令牌通过日志输出，<b>生产环境应替换为邮件发送</b>。
     * 令牌有效期为 30 分钟，仅可使用一次。
     * 邮箱不存在时静默返回，不暴露用户注册信息。
     */
    @Override
    @Transactional
    public void forgotPassword(ForgotPasswordRequest request) {
        UserDO user = accountSupport.findByEmail(request.email());
        if (user == null) {
            return;
        }
        String token = DigestSupport.randomToken();
        PasswordResetTokenDO record = new PasswordResetTokenDO();
        record.setUserId(user.getId());
        record.setTokenHash(DigestSupport.sha256(token));
        record.setExpireTime(new Date(System.currentTimeMillis() + Duration.ofMinutes(30).toMillis()));
        record.setUsed(0);
        passwordResetTokenMapper.insert(record);
        log.info("DevBrain password reset token for {}: {}", user.getEmail(), token);
    }

    /**
     * 重置密码 —— 使用重置令牌设置新密码。
     * <p>
     * 流程：根据令牌哈希查找记录 → 校验令牌状态（未使用、未过期）→ 更新密码 → 标记令牌已使用。
     * <p>
     * <b>安全策略</b>：令牌使用 SHA-256 哈希存储，校验时对传入令牌计算哈希后比对。
     * 令牌仅可使用一次，防止重复利用攻击。
     */
    @Override
    @Transactional
    public void resetPassword(ResetPasswordRequest request) {
        PasswordResetTokenDO token = passwordResetTokenMapper.selectOne(
                Wrappers.lambdaQuery(PasswordResetTokenDO.class)
                        .eq(PasswordResetTokenDO::getTokenHash, DigestSupport.sha256(request.token()))
                        .eq(PasswordResetTokenDO::getUsed, 0));
        if (token == null || token.getExpireTime().before(new Date())) {
            throw new ClientException("重置链接无效或已过期");
        }
        UserDO user = accountSupport.requireUser(token.getUserId());
        user.setPasswordHash(passwordEncoder.encode(request.newPassword()));
        userMapper.updateById(user);
        token.setUsed(1);
        passwordResetTokenMapper.updateById(token);
    }

}
