package edu.cqupt.devbrain.framework.exception;

import edu.cqupt.devbrain.framework.errorcode.BaseErrorCode;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ClientExceptionTest {

    @Test
    void mapsCommonClientErrorCodesToHttpStatus() {
        assertEquals(401, new ClientException(BaseErrorCode.UNAUTHORIZED).getHttpStatus());
        assertEquals(403, new ClientException(BaseErrorCode.FORBIDDEN).getHttpStatus());
        assertEquals(423, new ClientException(BaseErrorCode.ACCOUNT_LOCKED).getHttpStatus());
        assertEquals(429, new ClientException(BaseErrorCode.LOGIN_RATE_LIMIT).getHttpStatus());
    }

    @Test
    void defaultsPlainClientMessageToBadRequest() {
        ClientException ex = new ClientException("参数错误");

        assertEquals("A000001", ex.errorCode);
        assertEquals("参数错误", ex.errorMessage);
        assertEquals(400, ex.getHttpStatus());
    }
}
