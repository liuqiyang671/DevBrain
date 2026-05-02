package edu.cqupt.devbrain.user.service;

import edu.cqupt.devbrain.framework.exception.ClientException;
import edu.cqupt.devbrain.user.dao.entity.PermissionDO;
import edu.cqupt.devbrain.user.dao.entity.RoleDO;
import edu.cqupt.devbrain.user.dao.entity.RolePermissionDO;
import edu.cqupt.devbrain.user.dao.entity.UserRoleDO;
import edu.cqupt.devbrain.user.dao.mapper.PermissionMapper;
import edu.cqupt.devbrain.user.dao.mapper.RoleMapper;
import edu.cqupt.devbrain.user.dao.mapper.RolePermissionMapper;
import edu.cqupt.devbrain.user.dao.mapper.UserRoleMapper;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class UserDirectoryServiceTest {

    private final RoleMapper roleMapper = mock(RoleMapper.class);
    private final PermissionMapper permissionMapper = mock(PermissionMapper.class);
    private final UserRoleMapper userRoleMapper = mock(UserRoleMapper.class);
    private final RolePermissionMapper rolePermissionMapper = mock(RolePermissionMapper.class);
    private final UserDirectoryService directoryService = new UserDirectoryService(
            roleMapper,
            permissionMapper,
            userRoleMapper,
            rolePermissionMapper
    );

    @Test
    void assignRolesUsesDefaultUserRoleWhenInputEmpty() {
        when(roleMapper.selectList(any())).thenReturn(List.of(role("role-user", UserDirectoryService.DEFAULT_ROLE)));

        directoryService.assignRoles("user-1", Set.of());

        verify(userRoleMapper).delete(any());
        verify(userRoleMapper).insert(any(UserRoleDO.class));
    }

    @Test
    void assignRolesRejectsUnknownRoleCodesBeforeChangingRelations() {
        when(roleMapper.selectList(any())).thenReturn(List.of(role("role-admin", "admin")));

        assertThrows(ClientException.class, () -> directoryService.assignRoles("user-1", Set.of("admin", "ghost")));

        verify(userRoleMapper, never()).delete(any());
        verify(userRoleMapper, never()).insert(any(UserRoleDO.class));
    }

    @Test
    void assignPermissionsDeletesRelationsWhenPermissionSetIsEmpty() {
        directoryService.assignPermissions("role-1", Set.of());

        verify(rolePermissionMapper).delete(any());
        verify(rolePermissionMapper, never()).insert(any(RolePermissionDO.class));
    }

    @Test
    void assignPermissionsRejectsUnknownPermissionCodesBeforeChangingRelations() {
        when(permissionMapper.selectList(any())).thenReturn(List.of(permission("perm-read", "dashboard:read")));

        assertThrows(ClientException.class,
                () -> directoryService.assignPermissions("role-1", Set.of("dashboard:read", "dashboard:write")));

        verify(rolePermissionMapper, never()).delete(any());
        verify(rolePermissionMapper, never()).insert(any(RolePermissionDO.class));
    }

    private RoleDO role(String id, String roleCode) {
        RoleDO role = new RoleDO();
        role.setId(id);
        role.setRoleCode(roleCode);
        return role;
    }

    private PermissionDO permission(String id, String permissionCode) {
        PermissionDO permission = new PermissionDO();
        permission.setId(id);
        permission.setPermissionCode(permissionCode);
        return permission;
    }
}
