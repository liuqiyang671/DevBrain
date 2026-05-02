package edu.cqupt.devbrain.user.controller;

import edu.cqupt.devbrain.auth.core.CookieSupport;
import edu.cqupt.devbrain.auth.core.CsrfTokenService;
import edu.cqupt.devbrain.auth.core.InvalidTokenException;
import edu.cqupt.devbrain.auth.core.JwtClaims;
import edu.cqupt.devbrain.auth.core.JwtTokenService;
import edu.cqupt.devbrain.auth.core.TokenSessionService;
import edu.cqupt.devbrain.user.service.AuthService;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.time.Instant;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AuthControllerTest {

    private final AuthService authService = mock(AuthService.class);
    private final CsrfTokenService csrfTokenService = mock(CsrfTokenService.class);
    private final CookieSupport cookieSupport = mock(CookieSupport.class);
    private final JwtTokenService jwtTokenService = mock(JwtTokenService.class);
    private final TokenSessionService tokenSessionService = mock(TokenSessionService.class);
    private final AuthController controller = new AuthController(
            authService,
            csrfTokenService,
            cookieSupport,
            jwtTokenService,
            tokenSessionService
    );

    @Test
    void logoutClearsCookiesWhenTokenMissing() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();
        when(cookieSupport.readToken(request)).thenReturn(null);

        assertDoesNotThrow(() -> controller.logout(request, response));

        verify(jwtTokenService, never()).parseToken(null);
        verify(cookieSupport).clearTokenCookie(response);
        verify(cookieSupport).clearCsrfCookie(response);
    }

    @Test
    void logoutClearsCookiesWhenTokenInvalid() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();
        when(cookieSupport.readToken(request)).thenReturn("bad-token");
        when(jwtTokenService.parseToken("bad-token")).thenThrow(new InvalidTokenException("invalid token"));

        assertDoesNotThrow(() -> controller.logout(request, response));

        verify(tokenSessionService, never()).remove("bad-token");
        verify(cookieSupport).clearTokenCookie(response);
        verify(cookieSupport).clearCsrfCookie(response);
    }

    @Test
    void logoutRemovesSessionAndClearsCookiesWhenTokenValid() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();
        JwtClaims claims = new JwtClaims(
                "session-1",
                "user-1",
                "alice",
                Set.of("user"),
                Set.of("dashboard:read"),
                Instant.now().plusSeconds(60)
        );
        when(cookieSupport.readToken(request)).thenReturn("valid-token");
        when(jwtTokenService.parseToken("valid-token")).thenReturn(claims);

        controller.logout(request, response);

        verify(tokenSessionService).remove("session-1");
        verify(cookieSupport).clearTokenCookie(response);
        verify(cookieSupport).clearCsrfCookie(response);
    }
}
