package edu.cqupt.devbrain.user.service;

import edu.cqupt.devbrain.framework.context.LoginUser;
import edu.cqupt.devbrain.user.controller.vo.CurrentUserVO;
import edu.cqupt.devbrain.user.dao.entity.UserDO;
import org.springframework.stereotype.Service;

import java.util.Set;

/**
 * 当前用户组装器 —— 将用户实体转换为上下文对象和视图对象。
 * <p>
 * 负责在转换过程中加载用户的角色和权限信息，确保组装后的对象包含完整的身份与权限数据。
 * 用于认证拦截器设置用户上下文、登录/注册返回用户信息等场景。
 */
@Service
public class CurrentUserAssembler {

    private final UserDirectoryService directoryService;

    public CurrentUserAssembler(UserDirectoryService directoryService) {
        this.directoryService = directoryService;
    }

    /**
     * 将用户实体转换为登录用户上下文对象。
     * <p>
     * 自动加载用户角色和权限信息。
     *
     * @param user 用户实体
     * @return 包含角色和权限的登录用户上下文
     */
    public LoginUser toLoginUser(UserDO user) {
        Set<String> roles = directoryService.roleCodesByUser(user.getId());
        return toLoginUser(user, roles, directoryService.permissionCodesByRoles(roles));
    }

    /**
     * 将用户实体转换为登录用户上下文对象（指定角色和权限）。
     * <p>
     * 适用于已预加载角色和权限信息的场景，避免重复查询。
     *
     * @param user        用户实体
     * @param roles       角色编码集合
     * @param permissions 权限编码集合
     * @return 登录用户上下文
     */
    public LoginUser toLoginUser(UserDO user, Set<String> roles, Set<String> permissions) {
        return new LoginUser(
                user.getId(),
                user.getUsername(),
                user.getEmail(),
                user.getDisplayName(),
                user.getAvatar(),
                roles,
                permissions
        );
    }

    /**
     * 将用户实体转换为当前用户视图对象。
     * <p>
     * 自动加载用户角色和权限信息。
     *
     * @param user 用户实体
     * @return 当前用户视图对象
     */
    public CurrentUserVO toCurrentUser(UserDO user) {
        Set<String> roles = directoryService.roleCodesByUser(user.getId());
        return toCurrentUser(user, roles, directoryService.permissionCodesByRoles(roles));
    }

    /**
     * 将用户实体转换为当前用户视图对象（指定角色和权限）。
     *
     * @param user        用户实体
     * @param roles       角色编码集合
     * @param permissions 权限编码集合
     * @return 当前用户视图对象
     */
    public CurrentUserVO toCurrentUser(UserDO user, Set<String> roles, Set<String> permissions) {
        return new CurrentUserVO(
                user.getId(),
                user.getUsername(),
                user.getEmail(),
                user.getDisplayName(),
                user.getAvatar(),
                roles,
                permissions
        );
    }
}
