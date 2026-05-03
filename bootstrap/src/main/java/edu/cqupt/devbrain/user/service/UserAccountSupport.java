package edu.cqupt.devbrain.user.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import edu.cqupt.devbrain.framework.errorcode.BaseErrorCode;
import edu.cqupt.devbrain.framework.exception.ClientException;
import edu.cqupt.devbrain.user.dao.entity.UserDO;
import edu.cqupt.devbrain.user.dao.mapper.UserMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

/**
 * 用户账号辅助服务 —— 封装用户查询、唯一性校验和状态判断等基础操作。
 * <p>
 * 提供用户相关的通用查询方法，避免在多个业务服务中重复编写用户查询逻辑。
 * 所有查询均对输入进行 trim 处理，防止前后空白导致查询失败。
 */
@Service
@RequiredArgsConstructor
public class UserAccountSupport {

    /**
     * 用户启用状态标识，对应 t_user.status 字段。
     */
    public static final String STATUS_ENABLED = "enabled";

    private final UserMapper userMapper;

    /**
     * 根据用户ID查询用户，不存在则抛出异常。
     *
     * @param userId 用户ID
     * @return 用户实体
     * @throws ClientException 用户不存在
     */
    public UserDO requireUser(String userId) {
        UserDO user = userMapper.selectById(userId);
        if (user == null) {
            throw new ClientException("用户不存在");
        }
        return user;
    }

    /**
     * 根据用户ID查询已启用的用户，用户不存在或已禁用则抛出未授权异常。
     * <p>
     * 用于认证拦截器校验用户状态，禁用用户应视为未认证。
     *
     * @param userId 用户ID
     * @return 已启用的用户实体
     * @throws ClientException 用户不存在或已禁用
     */
    public UserDO requireEnabledUser(String userId) {
        UserDO user = userMapper.selectById(userId);
        if (!isEnabled(user)) {
            throw new ClientException(BaseErrorCode.UNAUTHORIZED);
        }
        return user;
    }

    /**
     * 根据用户名查询用户。
     *
     * @param username 用户名（自动 trim）
     * @return 用户实体，不存在返回 null
     */
    public UserDO findByUsername(String username) {
        return userMapper.selectOne(Wrappers.lambdaQuery(UserDO.class).eq(UserDO::getUsername, clean(username)));
    }

    /**
     * 根据邮箱查询用户。
     *
     * @param email 邮箱地址（自动 trim）
     * @return 用户实体，不存在返回 null
     */
    public UserDO findByEmail(String email) {
        return userMapper.selectOne(Wrappers.lambdaQuery(UserDO.class).eq(UserDO::getEmail, clean(email)));
    }

    /**
     * 校验用户名和邮箱的唯一性。
     * <p>
     * 检查用户名或邮箱是否已被其他用户占用（可排除指定用户ID，用于更新场景）。
     * 用户名和邮箱至少需要提供一个，两者都不提供时直接返回（不做校验）。
     *
     * @param username  用户名（可选）
     * @param email     邮箱地址（可选）
     * @param excludeId 需要排除的用户ID（更新场景排除自身），可为 null
     * @throws ClientException 用户名或邮箱已被占用
     */
    public void ensureUsernameOrEmailAvailable(String username, String email, String excludeId) {
        String cleanUsername = clean(username);
        String cleanEmail = clean(email);
        boolean hasUsername = StringUtils.hasText(cleanUsername);
        boolean hasEmail = StringUtils.hasText(cleanEmail);
        if (!hasUsername && !hasEmail) {
            return;
        }
        Long count = userMapper.selectCount(Wrappers.lambdaQuery(UserDO.class)
                .and(wrapper -> {
                    if (hasUsername) {
                        wrapper.eq(UserDO::getUsername, cleanUsername);
                    }
                    if (hasEmail) {
                        if (hasUsername) {
                            wrapper.or();
                        }
                        wrapper.eq(UserDO::getEmail, cleanEmail);
                    }
                })
                .ne(StringUtils.hasText(excludeId), UserDO::getId, excludeId));
        if (count != null && count > 0) {
            throw new ClientException("用户名或邮箱已存在");
        }
    }

    /**
     * 判断用户是否处于启用状态。
     *
     * @param user 用户实体，可为 null
     * @return true 表示用户存在且状态为 enabled
     */
    public boolean isEnabled(UserDO user) {
        return user != null && STATUS_ENABLED.equals(user.getStatus());
    }

    /**
     * 清理字符串：去除首尾空白。null 值保持不变。
     */
    public String clean(String value) {
        return value == null ? null : value.trim();
    }
}
