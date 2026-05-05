package edu.cqupt.devbrain.framework.exception;

import edu.cqupt.devbrain.framework.errorcode.BaseErrorCode;
import edu.cqupt.devbrain.framework.errorcode.IErrorCode;

/**
 * 客户端异常。
 * <p>用户发起调用请求后，因客户端提交的参数不合法、认证失败、权限不足等客户端侧问题导致的异常。
 * 默认 HTTP 状态码为 400，根据错误码可自动映射为 401、403、423、429 等。</p>
 *
 * @see ServiceException
 * @see BaseErrorCode#CLIENT_ERROR
 */
public class ClientException extends ServiceException {

    /**
     * 基于消息构造客户端异常（默认使用 CLIENT_ERROR 错误码，HTTP 400）
     *
     * @param message 错误消息
     */
    public ClientException(String message) {
        super(BaseErrorCode.CLIENT_ERROR.code(), message, 400);
    }

    /**
     * 基于 IErrorCode 构造客户端异常
     *
     * @param errorCode 错误码枚举
     */
    public ClientException(IErrorCode errorCode) {
        super(errorCode.code(), errorCode.message(), resolveHttpStatus(errorCode));
    }

    /**
     * 基于消息和 IErrorCode 构造客户端异常
     *
     * @param message   错误消息
     * @param errorCode 错误码枚举
     */
    public ClientException(String message, IErrorCode errorCode) {
        super(errorCode.code(), message, resolveHttpStatus(errorCode));
    }

    /**
     * 向后兼容构造函数：基于错误码、消息和 HTTP 状态码
     *
     * @param code       错误码字符串
     * @param message    错误消息
     * @param httpStatus HTTP 状态码
     */
    public ClientException(String code, String message, int httpStatus) {
        super(code, message, httpStatus);
    }

    /**
     * 根据错误码自动解析对应的 HTTP 状态码
     *
     * @param errorCode 错误码枚举
     * @return 对应的 HTTP 状态码，默认返回 400
     */
    private static int resolveHttpStatus(IErrorCode errorCode) {
        return switch (errorCode.code()) {
            case "A000401" -> 401;
            case "A000403" -> 403;
            case "A000423" -> 423;
            case "A000429" -> 429;
            default -> 400;
        };
    }

    @Override
    public String toString() {
        return "ClientException{" +
                "code='" + errorCode + "'," +
                "message='" + errorMessage + "'" +
                '}';
    }
}
