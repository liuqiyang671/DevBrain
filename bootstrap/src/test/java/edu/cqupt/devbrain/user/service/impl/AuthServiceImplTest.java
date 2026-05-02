package edu.cqupt.devbrain.user.service.impl;

import edu.cqupt.devbrain.auth.core.JwtClaims;
import edu.cqupt.devbrain.auth.core.JwtTokenService;
import edu.cqupt.devbrain.auth.core.TokenSessionService;
import edu.cqupt.devbrain.framework.errorcode.BaseErrorCode;
import edu.cqupt.devbrain.framework.exception.ClientException;
import edu.cqupt.devbrain.user.controller.request.LoginRequest;
import edu.cqupt.devbrain.user.dao.entity.UserDO;
import edu.cqupt.devbrain.user.dao.mapper.PasswordResetTokenMapper;
import edu.cqupt.devbrain.user.dao.mapper.UserMapper;
import edu.cqupt.devbrain.user.service.CurrentUserAssembler;
import edu.cqupt.devbrain.user.service.UserAccountSupport;
import edu.cqupt.devbrain.user.service.UserDirectoryService;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Instant;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AuthServiceImplTest {

    private final UserMapper userMapper = mock(UserMapper.class);
    private final PasswordResetTokenMapper passwordResetTokenMapper = mock(PasswordResetTokenMapper.class);
    private final UserDirectoryService directoryService = mock(UserDirectoryService.class);
    private final CurrentUserAssembler currentUserAssembler = new CurrentUserAssembler(directoryService);
    private final UserAccountSupport accountSupport = new UserAccountSupport(userMapper);
    private final PasswordEncoder passwordEncoder = mock(PasswordEncoder.class);
    private final JwtTokenService jwtTokenService = mock(JwtTokenService.class);
    private final TokenSessionService tokenSessionService = mock(TokenSessionService.class);
    private final AuthServiceImpl authService = new AuthServiceImpl(
            userMapper,
            passwordResetTokenMapper,
            directoryService,
            currentUserAssembler,
            accountSupport,
            passwordEncoder,
            jwtTokenService,
            tokenSessionService
    );

    @Test
    void loginUsesSimpleDemoFlowAndCreatesSession() {
        UserDO user = user("enabled");
        Set<String> roles = Set.of("user");
        Set<String> permissions = Set.of("profile:read");
        JwtClaims claims = new JwtClaims("session-1", "user-1", "alice", roles, permissions, Instant.now().plusSeconds(60));
        when(userMapper.selectOne(any())).thenReturn(user);
        when(passwordEncoder.matches("password123", "hash")).thenReturn(true);
        when(directoryService.roleCodesByUser("user-1")).thenReturn(roles);
        when(directoryService.permissionCodesByRoles(roles)).thenReturn(permissions);
        when(jwtTokenService.createToken("user-1", "alice", roles, permissions)).thenReturn("token");
        when(jwtTokenService.parseToken("token")).thenReturn(claims);

        var outcome = authService.login(new LoginRequest("alice", "password123"), "127.0.0.1", "JUnit");

        assertEquals("token", outcome.token());
        assertEquals("user-1", outcome.user().userId());
        assertEquals(roles, outcome.user().roles());
        verify(tokenSessionService).store(claims);
        verify(userMapper).updateById(user);
    }

    @Test
    void loginRejectsDisabledUserWithForbiddenErrorCode() {
        UserDO user = user("disabled");
        when(userMapper.selectOne(any())).thenReturn(user);
        when(passwordEncoder.matches("password123", "hash")).thenReturn(true);

        ClientException exception = assertThrows(ClientException.class,
                () -> authService.login(new LoginRequest("alice", "password123"), "127.0.0.1", "JUnit"));

        assertEquals(BaseErrorCode.FORBIDDEN.code(), exception.errorCode);
        assertEquals(403, exception.getHttpStatus());
    }

    private UserDO user(String status) {
        UserDO user = new UserDO();
        user.setId("user-1");
        user.setUsername("alice");
        user.setEmail("alice@example.com");
        user.setDisplayName("Alice");
        user.setPasswordHash("hash");
        user.setStatus(status);
        return user;
    }
}
