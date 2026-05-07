package edu.cqupt.devbrain.framework.web;

import edu.cqupt.devbrain.framework.convention.Result;
import edu.cqupt.devbrain.framework.exception.ClientException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ResultsTest {

    @Test
    void successCarriesCurrentRequestId() {
        try (RequestIdContext.Scope ignored = RequestIdContext.open("req-1")) {
            Result<Void> result = Results.success();

            assertEquals("req-1", result.getRequestId());
        }
    }

    @Test
    void failureCarriesCurrentRequestId() {
        try (RequestIdContext.Scope ignored = RequestIdContext.open("req-2")) {
            Result<Void> result = Results.failure("A000001", "参数错误");

            assertEquals("req-2", result.getRequestId());
        }
    }

    @Test
    void clientExceptionHandlerUsesExceptionHttpStatus() {
        HttpServletRequest request = new MockHttpServletRequest("GET", "/private");
        HttpServletResponse servletResponse = new MockHttpServletResponse();
        ClientException ex = new ClientException("A000401", "未登录", 401);

        var response = new GlobalExceptionHandler().clientException(request, servletResponse, ex);

        assertEquals(401, response.getStatusCode().value());
        assertEquals("A000401", response.getBody().getCode());
    }
}
