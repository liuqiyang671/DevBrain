package edu.cqupt.devbrain.auth.core;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

class JwtTokenServiceTest {

    @Test
    void createsAndParsesSignedToken() {
        AuthSecurityProperties properties = new AuthSecurityProperties();
        properties.setJwtSecret("devbrain-test-secret-devbrain-test-secret");
        properties.setTokenTtl(Duration.ofHours(2));
        JwtTokenService tokenService = new JwtTokenService(properties);

        String token = tokenService.createToken("u-1", "alice", Set.of("user"), Set.of("dashboard:read"));
        JwtClaims claims = tokenService.parseToken(token);

        assertEquals("u-1", claims.userId());
        assertEquals("alice", claims.username());
        assertEquals(Set.of("user"), claims.roles());
        assertEquals(Set.of("dashboard:read"), claims.permissions());
        assertFalse(claims.sessionId().isBlank());
    }

    @Test
    void rejectsTamperedToken() {
        AuthSecurityProperties properties = new AuthSecurityProperties();
        properties.setJwtSecret("devbrain-test-secret-devbrain-test-secret");
        JwtTokenService tokenService = new JwtTokenService(properties);
        String token = tokenService.createToken("u-1", "alice", Set.of("user"), Set.of());

        String tampered = token.substring(0, token.length() - 2) + "xx";

        assertThrows(InvalidTokenException.class, () -> tokenService.parseToken(tampered));
    }
}
