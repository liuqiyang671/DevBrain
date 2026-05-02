package edu.cqupt.devbrain.user.service.impl;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import edu.cqupt.devbrain.framework.context.LoginUser;
import edu.cqupt.devbrain.framework.context.UserContext;
import edu.cqupt.devbrain.framework.exception.ClientException;
import edu.cqupt.devbrain.user.controller.request.ChangePasswordRequest;
import edu.cqupt.devbrain.user.controller.request.ProfileUpdateRequest;
import edu.cqupt.devbrain.user.controller.request.UserCreateRequest;
import edu.cqupt.devbrain.user.controller.request.UserUpdateRequest;
import edu.cqupt.devbrain.user.controller.vo.CurrentUserVO;
import edu.cqupt.devbrain.user.controller.vo.UserVO;
import edu.cqupt.devbrain.user.dao.entity.UserDO;
import edu.cqupt.devbrain.user.dao.mapper.UserMapper;
import edu.cqupt.devbrain.user.service.CurrentUserAssembler;
import edu.cqupt.devbrain.user.service.UserAccountSupport;
import edu.cqupt.devbrain.user.service.UserDirectoryService;
import edu.cqupt.devbrain.user.service.UserService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/**
 * 用户服务实现 —— 处理用户信息查询、个人资料管理、密码修改及用户 CRUD 的核心业务逻辑。
 * <p>
 * <b>业务规则</b>：
 * <ul>
 *   <li>不允许删除当前登录用户自身</li>
 *   <li>邮箱更新时需校验唯一性</li>
 *   <li>分页查询每页最大 100 条，防止大量数据查询</li>
 *   <li>创建用户时若未指定显示名称，默认使用用户名</li>
 * </ul>
 */
@Service
public class UserServiceImpl implements UserService {

    private static final Logger log = LoggerFactory.getLogger(UserServiceImpl.class);

    private final UserMapper userMapper;
    private final UserDirectoryService directoryService;
    private final CurrentUserAssembler currentUserAssembler;
    private final UserAccountSupport accountSupport;
    private final PasswordEncoder passwordEncoder;

    public UserServiceImpl(
            UserMapper userMapper,
            UserDirectoryService directoryService,
            CurrentUserAssembler currentUserAssembler,
            UserAccountSupport accountSupport,
            PasswordEncoder passwordEncoder) {
        this.userMapper = userMapper;
        this.directoryService = directoryService;
        this.currentUserAssembler = currentUserAssembler;
        this.accountSupport = accountSupport;
        this.passwordEncoder = passwordEncoder;
    }

    /**
     * 获取当前登录用户信息。
     * <p>
     * 直接从线程上下文 {@link UserContext} 中读取，无需查询数据库。
     */
    @Override
    public CurrentUserVO current() {
        LoginUser user = UserContext.requireUser();
        return new CurrentUserVO(
                user.userId(),
                user.username(),
                user.email(),
                user.displayName(),
                user.avatar(),
                user.roles(),
                user.permissions()
        );
    }

    /**
     * 更新当前用户个人资料。
     * <p>
     * 支持部分更新：仅更新请求中非空的字段。邮箱更新时校验唯一性。
     */
    @Override
    @Transactional
    public CurrentUserVO updateProfile(ProfileUpdateRequest request) {
        LoginUser loginUser = UserContext.requireUser();
        UserDO user = accountSupport.requireUser(loginUser.userId());
        if (StringUtils.hasText(request.email())) {
            accountSupport.ensureUsernameOrEmailAvailable(null, request.email(), user.getId());
            user.setEmail(accountSupport.clean(request.email()));
        }
        if (request.displayName() != null) {
            user.setDisplayName(accountSupport.clean(request.displayName()));
        }
        if (request.avatar() != null) {
            user.setAvatar(accountSupport.clean(request.avatar()));
        }
        userMapper.updateById(user);
        return currentUserAssembler.toCurrentUser(user);
    }

    /**
     * 修改当前用户密码。
     * <p>
     * 需先验证当前密码正确，再设置新密码（BCrypt 加密后存储）。
     */
    @Override
    @Transactional
    public void changePassword(ChangePasswordRequest request) {
        UserDO user = accountSupport.requireUser(UserContext.requireUser().userId());
        if (!passwordEncoder.matches(request.currentPassword(), user.getPasswordHash())) {
            throw new ClientException("当前密码不正确");
        }
        user.setPasswordHash(passwordEncoder.encode(request.newPassword()));
        userMapper.updateById(user);
    }

    /**
     * 分页查询用户列表。
     * <p>
     * 支持按用户名、邮箱、显示名称模糊搜索，按更新时间倒序排列。
     * 每页大小上限 100 条，防止大量数据查询影响性能。
     */
    @Override
    public IPage<UserVO> page(long current, long size, String keyword) {
        Page<UserDO> page = new Page<>(Math.max(1, current), Math.min(Math.max(1, size), 100));
        IPage<UserDO> result = userMapper.selectPage(page, Wrappers.lambdaQuery(UserDO.class)
                .and(StringUtils.hasText(keyword), wrapper -> wrapper
                        .like(UserDO::getUsername, keyword)
                        .or()
                        .like(UserDO::getEmail, keyword)
                        .or()
                        .like(UserDO::getDisplayName, keyword))
                .orderByDesc(UserDO::getUpdateTime));
        return result.convert(this::toUserVO);
    }

    /**
     * 创建新用户。
     * <p>
     * 流程：校验唯一性 → 创建用户 → 分配角色 → 返回用户信息。
     */
    @Override
    @Transactional
    public UserVO create(UserCreateRequest request) {
        log.info("Creating user: username={}, email={}", request.username(), request.email());
        accountSupport.ensureUsernameOrEmailAvailable(request.username(), request.email(), null);
        UserDO user = new UserDO();
        user.setUsername(accountSupport.clean(request.username()));
        user.setEmail(accountSupport.clean(request.email()));
        user.setPasswordHash(passwordEncoder.encode(request.password()));
        user.setDisplayName(StringUtils.hasText(request.displayName())
                ? accountSupport.clean(request.displayName())
                : accountSupport.clean(request.username()));
        user.setAvatar(accountSupport.clean(request.avatar()));
        user.setStatus(UserAccountSupport.STATUS_ENABLED);
        userMapper.insert(user);
        directoryService.assignRoles(user.getId(), request.roleCodes());
        log.info("User created successfully: id={}, username={}", user.getId(), user.getUsername());
        return toUserVO(user);
    }

    /**
     * 更新用户信息。
     * <p>
     * 支持部分更新：仅更新请求中非空的字段。角色分配为全量替换模式。
     */
    @Override
    @Transactional
    public UserVO update(String id, UserUpdateRequest request) {
        log.info("Updating user: id={}", id);
        UserDO user = accountSupport.requireUser(id);
        if (StringUtils.hasText(request.email())) {
            accountSupport.ensureUsernameOrEmailAvailable(null, request.email(), user.getId());
            user.setEmail(accountSupport.clean(request.email()));
        }
        if (request.displayName() != null) {
            user.setDisplayName(accountSupport.clean(request.displayName()));
        }
        if (request.avatar() != null) {
            user.setAvatar(accountSupport.clean(request.avatar()));
        }
        if (StringUtils.hasText(request.status())) {
            user.setStatus(accountSupport.clean(request.status()));
        }
        if (StringUtils.hasText(request.password())) {
            user.setPasswordHash(passwordEncoder.encode(request.password()));
        }
        userMapper.updateById(user);
        if (request.roleCodes() != null) {
            directoryService.assignRoles(user.getId(), request.roleCodes());
        }
        return toUserVO(user);
    }

    /**
     * 删除用户（逻辑删除）。
     * <p>
     * <b>安全校验</b>：不允许删除当前登录用户自身，防止管理员误操作导致自身无法登录。
     */
    @Override
    public void delete(String id) {
        log.info("Deleting user: id={}", id);
        if (UserContext.requireUser().userId().equals(id)) {
            throw new ClientException("不能删除当前登录用户");
        }
        accountSupport.requireUser(id);
        userMapper.deleteById(id);
        log.info("User deleted successfully: id={}", id);
    }

    /**
     * 将用户实体转换为视图对象，包含角色信息。
     */
    private UserVO toUserVO(UserDO user) {
        return new UserVO(
                user.getId(),
                user.getUsername(),
                user.getEmail(),
                user.getDisplayName(),
                user.getAvatar(),
                user.getStatus(),
                directoryService.roleCodesByUser(user.getId()),
                user.getLastLoginTime(),
                user.getCreateTime(),
                user.getUpdateTime()
        );
    }
}
