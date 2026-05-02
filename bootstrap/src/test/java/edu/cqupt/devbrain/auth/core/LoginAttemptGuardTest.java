package edu.cqupt.devbrain.auth.core;

import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

class LoginAttemptGuardTest {

    @Test
    void blocksIpAfterTwentyAttemptsInsideFiveMinutes() {
        AuthSecurityProperties properties = new AuthSecurityProperties();
        properties.setIpLoginWindow(Duration.ofMinutes(5));
        properties.setIpLoginMaxAttempts(20);
        LoginAttemptGuard guard = new LoginAttemptGuard(new InMemorySecurityCache(), properties);

        for (int i = 0; i < 20; i++) {
            assertDoesNotThrow(() -> guard.checkLoginAllowed("alice", "10.0.0.8"));
        }

        assertThrows(RateLimitExceededException.class, () -> guard.checkLoginAllowed("bob", "10.0.0.8"));
    }

    @Test
    void locksAccountForFifteenMinutesAfterFiveFailures() {
        AuthSecurityProperties properties = new AuthSecurityProperties();
        properties.setAccountMaxFailures(5);
        properties.setAccountLockDuration(Duration.ofMinutes(15));
        LoginAttemptGuard guard = new LoginAttemptGuard(new InMemorySecurityCache(), properties);

        for (int i = 0; i < 5; i++) {
            guard.recordFailure("alice");
        }

        assertThrows(AccountLockedException.class, () -> guard.checkLoginAllowed("alice", "10.0.0.9"));
        guard.recordSuccess("alice");
        assertDoesNotThrow(() -> guard.checkLoginAllowed("alice", "10.0.0.10"));
    }
}
