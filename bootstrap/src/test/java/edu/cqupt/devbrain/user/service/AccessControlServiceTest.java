package edu.cqupt.devbrain.user.service;

import edu.cqupt.devbrain.framework.context.LoginUser;
import edu.cqupt.devbrain.framework.errorcode.BaseErrorCode;
import edu.cqupt.devbrain.framework.exception.ClientException;
import edu.cqupt.devbrain.user.dao.entity.ResourceDO;
import edu.cqupt.devbrain.user.dao.mapper.ResourceMapper;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AccessControlServiceTest {

    private final ResourceMapper resourceMapper = mock(ResourceMapper.class);
    private final AccessControlService accessControlService = new AccessControlService(resourceMapper);

    @Test
    void allowsPublicResource() {
        when(resourceMapper.selectList(any())).thenReturn(List.of(resource("GET", "/api/public/**", "admin:only", 1)));

        assertDoesNotThrow(() -> accessControlService.checkAccess("GET", "/api/public/ping", user(Set.of(), Set.of())));
    }

    @Test
    void allowsAdminUserForProtectedResource() {
        when(resourceMapper.selectList(any())).thenReturn(List.of(resource("GET", "/api/admin/**", "admin:read", 0)));

        assertDoesNotThrow(() -> accessControlService.checkAccess("GET", "/api/admin/users", user(Set.of("admin"), Set.of())));
    }

    @Test
    void allowsUserWhenPermissionMatchesResource() {
        when(resourceMapper.selectList(any())).thenReturn(List.of(resource("GET", "/api/users/**", "user:read", 0)));

        assertDoesNotThrow(() -> accessControlService.checkAccess("GET", "/api/users/me", user(Set.of("user"), Set.of("user:read"))));
    }

    @Test
    void rejectsUserWhenPermissionIsMissingWithFrameworkForbiddenCode() {
        when(resourceMapper.selectList(any())).thenReturn(List.of(resource("GET", "/api/admin/**", "admin:read", 0)));

        ClientException exception = assertThrows(ClientException.class,
                () -> accessControlService.checkAccess("GET", "/api/admin/users", user(Set.of("user"), Set.of("user:read"))));

        assertEquals(BaseErrorCode.FORBIDDEN.code(), exception.errorCode);
        assertEquals(403, exception.getHttpStatus());
    }

    @Test
    void clearResourceCacheForcesReloadedResourceRules() {
        when(resourceMapper.selectList(any())).thenReturn(
                List.of(resource("GET", "/api/reports/**", "report:read", 0)),
                List.of(resource("GET", "/api/reports/**", "report:admin", 0))
        );
        LoginUser user = user(Set.of("user"), Set.of("report:read"));

        assertDoesNotThrow(() -> accessControlService.checkAccess("GET", "/api/reports/monthly", user));
        accessControlService.clearResourceCache();
        assertThrows(ClientException.class, () -> accessControlService.checkAccess("GET", "/api/reports/monthly", user));

        verify(resourceMapper, times(2)).selectList(any());
    }

    private LoginUser user(Set<String> roles, Set<String> permissions) {
        return new LoginUser("user-1", "alice", "alice@example.com", "Alice", null, roles, permissions);
    }

    private ResourceDO resource(String method, String path, String permissionCode, int publicAccess) {
        ResourceDO resource = new ResourceDO();
        resource.setHttpMethod(method);
        resource.setPathPattern(path);
        resource.setPermissionCode(permissionCode);
        resource.setPublicAccess(publicAccess);
        return resource;
    }
}
