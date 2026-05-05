package edu.cqupt.devbrain.framework.exception;

import edu.cqupt.devbrain.framework.errorcode.BaseErrorCode;
import edu.cqupt.devbrain.framework.errorcode.IErrorCode;

import java.util.Optional;

/**
 * 服务端运行时异常。
 * <p>请求处理过程中因业务逻辑不符合预期（如数据状态冲突、业务规则校验失败等）而抛出的异常。
 * 默认 HTTP 状态码为 500，属于 B 类系统执行错误。</p>
 *
 * @see AbstractException
 * @see BaseErrorCode#SERVICE_ERROR
 */
public class ServiceException extends AbstractException {

    /**
     * HTTP 状态码
     */
    private final int httpStatus;

    /**
     * 基于 IErrorCode 构造服务异常
     *
     * @param errorCode 错误码枚举
     */
    public ServiceException(IErrorCode errorCode) {
        this(null, errorCode);
    }

    /**
     * 基于消息和 IErrorCode 构造服务异常
     *
     * @param message   错误消息
     * @param errorCode 错误码枚举
     */
    public ServiceException(String message, IErrorCode errorCode) {
        this(message, null, errorCode);
    }

    /**
     * 基于消息、原始异常和 IErrorCode 构造服务异常
     *
     * @param message   错误消息
     * @param throwable 原始异常
     * @param errorCode 错误码枚举
     */
    public ServiceException(String message, Throwable throwable, IErrorCode errorCode) {
        super(Optional.ofNullable(message).orElse(errorCode.message()), throwable, errorCode);
        this.httpStatus = 500;
    }

    /**
     * 向后兼容构造函数：基于错误码、消息和 HTTP 状态码
     *
     * @param code       错误码字符串
     * @param message    错误消息
     * @param httpStatus HTTP 状态码
     */
    public ServiceException(String code, String message, int httpStatus) {
        super(message, null, new IErrorCode() {
            @Override
            public String code() {
                return code;
            }

            @Override
            public String message() {
                return message;
            }
        });
        this.httpStatus = httpStatus;
    }

    /**
     * 获取 HTTP 状态码
     *
     * @return HTTP 状态码
     */
    public int getHttpStatus() {
        return httpStatus;
    }

    @Override
    public String toString() {
        return "ServiceException{" +
                "code='" + errorCode + "'," +
                "message='" + errorMessage + "'" +
                '}';
    }
}
