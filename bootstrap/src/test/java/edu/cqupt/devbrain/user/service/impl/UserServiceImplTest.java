package edu.cqupt.devbrain.user.service.impl;

import edu.cqupt.devbrain.framework.context.LoginUser;
import edu.cqupt.devbrain.framework.context.UserContext;
import edu.cqupt.devbrain.framework.exception.ClientException;
import edu.cqupt.devbrain.user.controller.request.ProfileUpdateRequest;
import edu.cqupt.devbrain.user.controller.request.UserCreateRequest;
import edu.cqupt.devbrain.user.dao.entity.UserDO;
import edu.cqupt.devbrain.user.dao.mapper.UserMapper;
import edu.cqupt.devbrain.user.service.CurrentUserAssembler;
import edu.cqupt.devbrain.user.service.UserAccountSupport;
import edu.cqupt.devbrain.user.service.UserDirectoryService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class UserServiceImplTest {

    private final UserMapper userMapper = mock(UserMapper.class);
    private final UserDirectoryService directoryService = mock(UserDirectoryService.class);
    private final CurrentUserAssembler currentUserAssembler = new CurrentUserAssembler(directoryService);
    private final UserAccountSupport accountSupport = new UserAccountSupport(userMapper);
    private final PasswordEncoder passwordEncoder = mock(PasswordEncoder.class);
    private final UserServiceImpl userService = new UserServiceImpl(
            userMapper,
            directoryService,
            currentUserAssembler,
            accountSupport,
            passwordEncoder
    );

    @AfterEach
    void clearUserContext() {
        UserContext.clear();
    }

    @Test
    void createRejectsDuplicateUsernameOrEmail() {
        when(userMapper.selectCount(any())).thenReturn(1L);

        assertThrows(ClientException.class, () -> userService.create(new UserCreateRequest(
                "alice",
                "alice@example.com",
                "password123",
                "Alice",
                null,
                Set.of("user")
        )));

        verify(userMapper, never()).insert(any(UserDO.class));
    }

    @Test
    void updateProfileAllowsKeepingOwnEmailByExcludingCurrentUser() {
        UserContext.set(loginUser());
        UserDO user = user();
        when(userMapper.selectById("user-1")).thenReturn(user);
        when(userMapper.selectCount(any())).thenReturn(0L);
        when(directoryService.roleCodesByUser("user-1")).thenReturn(Set.of("user"));
        when(directoryService.permissionCodesByRoles(Set.of("user"))).thenReturn(Set.of("profile:write"));

        var current = userService.updateProfile(new ProfileUpdateRequest("alice@example.com", "Alice L", null));

        assertEquals("Alice L", current.displayName());
        verify(userMapper).updateById(user);
    }

    @Test
    void deleteRejectsCurrentUser() {
        UserContext.set(loginUser());

        assertThrows(ClientException.class, () -> userService.delete("user-1"));

        verify(userMapper, never()).deleteById("user-1");
    }

    private LoginUser loginUser() {
        return new LoginUser("user-1", "alice", "alice@example.com", "Alice", null, Set.of("user"), Set.of());
    }

    private UserDO user() {
        UserDO user = new UserDO();
        user.setId("user-1");
        user.setUsername("alice");
        user.setEmail("alice@example.com");
        user.setDisplayName("Alice");
        user.setStatus("enabled");
        return user;
    }
}
