package edu.cqupt.devbrain.framework.context;

import edu.cqupt.devbrain.framework.exception.ClientException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LoginUserTest {

    @AfterEach
    void tearDown() {
        UserContext.clear();
    }

    @Test
    void exposesRecordAndBeanStyleAccessors() {
        LoginUser user = new LoginUser(
                "u-1",
                "alice",
                "alice@example.com",
                "Alice",
                "avatar.png",
                Set.of("admin", "user"),
                Set.of("dashboard:read")
        );

        assertEquals("u-1", user.userId());
        assertEquals("u-1", user.getUserId());
        assertEquals("alice@example.com", user.email());
        assertEquals("Alice", user.getDisplayName());
        assertTrue(user.isAdmin());
        assertTrue(user.permissions().contains("dashboard:read"));
    }

    @Test
    void normalizesMissingRolesAndPermissions() {
        LoginUser user = new LoginUser("u-2", "bob", null, null, null, null, null);

        assertEquals(Set.of(), user.roles());
        assertEquals(Set.of(), user.permissions());
        assertFalse(user.isAdmin());
        assertEquals("bob", user.getUsername());
    }

    @Test
    void requireUserThrowsUnauthorizedWhenContextIsMissing() {
        ClientException ex = assertThrows(ClientException.class, UserContext::requireUser);

        assertEquals("A000401", ex.errorCode);
        assertEquals(401, ex.getHttpStatus());
    }
}
