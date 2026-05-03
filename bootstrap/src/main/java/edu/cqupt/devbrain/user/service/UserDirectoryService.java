package edu.cqupt.devbrain.user.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import edu.cqupt.devbrain.framework.errorcode.BaseErrorCode;
import edu.cqupt.devbrain.framework.exception.ClientException;
import edu.cqupt.devbrain.user.dao.entity.PermissionDO;
import edu.cqupt.devbrain.user.dao.entity.RoleDO;
import edu.cqupt.devbrain.user.dao.entity.RolePermissionDO;
import edu.cqupt.devbrain.user.dao.entity.UserRoleDO;
import edu.cqupt.devbrain.user.dao.mapper.PermissionMapper;
import edu.cqupt.devbrain.user.dao.mapper.RoleMapper;
import edu.cqupt.devbrain.user.dao.mapper.RolePermissionMapper;
import edu.cqupt.devbrain.user.dao.mapper.UserRoleMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;

import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 用户目录服务 —— 管理 RBAC 模型中的用户-角色、角色-权限关系。
 * <p>
 * 提供角色和权限的查询、分配与清理功能，是权限体系的核心支撑服务。
 * <p>
 * <b>数据模型</b>：
 * <ul>
 *   <li>用户 ←→ 角色：多对多关系，通过 t_user_role 关联表维护</li>
 *   <li>角色 ←→ 权限码：多对多关系，通过 t_role_permission 关联表维护</li>
 * </ul>
 * <p>
 * <b>默认角色</b>：新注册用户自动分配 "user" 角色（{@link #DEFAULT_ROLE}）。
 */
@Service
@RequiredArgsConstructor
public class UserDirectoryService {

    /**
     * 默认角色编码，新注册用户自动分配此角色。
     */
    public static final String DEFAULT_ROLE = "user";

    private final RoleMapper roleMapper;
    private final PermissionMapper permissionMapper;
    private final UserRoleMapper userRoleMapper;
    private final RolePermissionMapper rolePermissionMapper;

    /**
     * 根据用户ID查询其角色编码集合。
     * <p>
     * 查询路径：用户ID → t_user_role → 角色ID → t_role → 角色编码
     *
     * @param userId 用户ID
     * @return 角色编码集合，无角色时返回空集合
     */
    public Set<String> roleCodesByUser(String userId) {
        Set<String> roleIds = userRoleMapper.selectList(Wrappers.lambdaQuery(UserRoleDO.class).eq(UserRoleDO::getUserId, userId))
                .stream()
                .map(UserRoleDO::getRoleId)
                .collect(Collectors.toSet());
        if (roleIds.isEmpty()) {
            return Set.of();
        }
        return roleMapper.selectBatchIds(roleIds).stream().map(RoleDO::getRoleCode).collect(Collectors.toUnmodifiableSet());
    }

    /**
     * 根据角色编码集合查询其权限编码集合。
     * <p>
     * 查询路径：角色编码 → t_role → 角色ID → t_role_permission → 权限ID → t_permission → 权限编码
     *
     * @param roleCodes 角色编码集合
     * @return 权限编码集合，无权限时返回空集合
     */
    public Set<String> permissionCodesByRoles(Set<String> roleCodes) {
        if (CollectionUtils.isEmpty(roleCodes)) {
            return Set.of();
        }
        List<RoleDO> roles = roleMapper.selectList(Wrappers.lambdaQuery(RoleDO.class).in(RoleDO::getRoleCode, roleCodes));
        Set<String> roleIds = roles.stream().map(RoleDO::getId).collect(Collectors.toSet());
        if (roleIds.isEmpty()) {
            return Set.of();
        }
        Set<String> permissionIds = rolePermissionMapper.selectList(
                        Wrappers.lambdaQuery(RolePermissionDO.class).in(RolePermissionDO::getRoleId, roleIds))
                .stream()
                .map(RolePermissionDO::getPermissionId)
                .collect(Collectors.toSet());
        if (permissionIds.isEmpty()) {
            return Set.of();
        }
        return permissionMapper.selectBatchIds(permissionIds).stream()
                .map(PermissionDO::getPermissionCode)
                .collect(Collectors.toUnmodifiableSet());
    }

    /**
     * 为用户分配角色（全量替换模式）。
     * <p>
     * 先删除用户的所有角色关联，再根据传入的角色编码列表重新创建关联。
     * 若角色编码列表为空，则分配默认角色 "user"。
     * <p>
     * <b>风险点</b>：该方法先删后插，在并发场景下可能出现短暂的权限真空，
     * 建议在事务中调用。
     *
     * @param userId    用户ID
     * @param roleCodes 角色编码集合，为空时分配默认角色
     * @throws ClientException 角色编码不存在
     */
    @Transactional
    public void assignRoles(String userId, Collection<String> roleCodes) {
        Set<String> nextCodes = CollectionUtils.isEmpty(roleCodes) ? Set.of(DEFAULT_ROLE) : Set.copyOf(roleCodes);
        Map<String, RoleDO> roles = roleMapper.selectList(Wrappers.lambdaQuery(RoleDO.class).in(RoleDO::getRoleCode, nextCodes))
                .stream()
                .collect(Collectors.toMap(RoleDO::getRoleCode, Function.identity(), (left, right) -> left));
        rejectMissingCodes(nextCodes, roles.keySet(), "角色码不存在");
        userRoleMapper.delete(Wrappers.lambdaQuery(UserRoleDO.class).eq(UserRoleDO::getUserId, userId));
        nextCodes.stream().map(roles::get).forEach(role -> {
            UserRoleDO relation = new UserRoleDO();
            relation.setUserId(userId);
            relation.setRoleId(role.getId());
            userRoleMapper.insert(relation);
        });
    }

    /**
     * 删除角色关联的所有关系数据（用户-角色关系 + 角色-权限关系）。
     * <p>
     * 在删除角色时调用，确保删除角色后不会留下孤立的关系数据。
     *
     * @param roleId 角色ID
     */
    @Transactional
    public void removeRoleRelations(String roleId) {
        userRoleMapper.delete(Wrappers.lambdaQuery(UserRoleDO.class).eq(UserRoleDO::getRoleId, roleId));
        rolePermissionMapper.delete(Wrappers.lambdaQuery(RolePermissionDO.class).eq(RolePermissionDO::getRoleId, roleId));
    }

    /**
     * 删除权限码关联的所有角色-权限关系数据。
     * <p>
     * 在删除权限码时调用，确保删除权限码后不会留下孤立的关系数据。
     *
     * @param permissionId 权限码ID
     */
    @Transactional
    public void removePermissionRelations(String permissionId) {
        rolePermissionMapper.delete(Wrappers.lambdaQuery(RolePermissionDO.class).eq(RolePermissionDO::getPermissionId, permissionId));
    }

    /**
     * 为角色分配权限码（全量替换模式）。
     * <p>
     * 先删除角色的所有权限关联，再根据传入的权限编码列表重新创建关联。
     * 若权限编码列表为空，则清除该角色的所有权限关联。
     *
     * @param roleId           角色ID
     * @param permissionCodes  权限编码集合，为空则清除所有权限
     * @throws ClientException 权限编码不存在
     */
    @Transactional
    public void assignPermissions(String roleId, Collection<String> permissionCodes) {
        if (CollectionUtils.isEmpty(permissionCodes)) {
            rolePermissionMapper.delete(Wrappers.lambdaQuery(RolePermissionDO.class).eq(RolePermissionDO::getRoleId, roleId));
            return;
        }
        Set<String> nextCodes = Set.copyOf(permissionCodes);
        Map<String, PermissionDO> permissions = permissionMapper.selectList(
                        Wrappers.lambdaQuery(PermissionDO.class).in(PermissionDO::getPermissionCode, nextCodes))
                .stream()
                .collect(Collectors.toMap(PermissionDO::getPermissionCode, Function.identity(), (left, right) -> left));
        rejectMissingCodes(nextCodes, permissions.keySet(), "权限码不存在");
        rolePermissionMapper.delete(Wrappers.lambdaQuery(RolePermissionDO.class).eq(RolePermissionDO::getRoleId, roleId));
        nextCodes.stream().map(permissions::get).forEach(permission -> {
            RolePermissionDO relation = new RolePermissionDO();
            relation.setRoleId(roleId);
            relation.setPermissionId(permission.getId());
            rolePermissionMapper.insert(relation);
        });
    }

    /**
     * 校验请求的编码集合是否全部存在于数据库中，不存在的编码抛出异常。
     *
     * @param requestedCodes 请求的编码集合
     * @param foundCodes     数据库中实际找到的编码集合
     * @param message        错误提示信息前缀
     * @throws ClientException 存在不存在的编码时抛出
     */
    private void rejectMissingCodes(Set<String> requestedCodes, Set<String> foundCodes, String message) {
        Set<String> missingCodes = new HashSet<>(requestedCodes);
        missingCodes.removeAll(foundCodes);
        if (!missingCodes.isEmpty()) {
            throw new ClientException(message + ": " + String.join(", ", missingCodes), BaseErrorCode.CLIENT_ERROR);
        }
    }
}
