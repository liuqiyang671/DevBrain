package edu.cqupt.devbrain.framework.exception;

import edu.cqupt.devbrain.framework.errorcode.BaseErrorCode;
import edu.cqupt.devbrain.framework.errorcode.IErrorCode;

/**
 * 远程服务调用异常。
 * <p>当系统调用第三方远程服务（如支付服务、消息队列、外部 API 等）失败时抛出的异常。
 * 属于 C 类第三方服务错误，用于区分本地业务异常和远程调用异常。</p>
 *
 * @see AbstractException
 * @see BaseErrorCode#REMOTE_ERROR
 */
public class RemoteException extends AbstractException {

    /**
     * 基于消息构造远程服务异常（默认使用 REMOTE_ERROR 错误码）
     *
     * @param message 错误消息
     */
    public RemoteException(String message) {
        this(message, null, BaseErrorCode.REMOTE_ERROR);
    }

    /**
     * 基于消息和错误码构造远程服务异常
     *
     * @param message   错误消息
     * @param errorCode 错误码枚举
     */
    public RemoteException(String message, IErrorCode errorCode) {
        this(message, null, errorCode);
    }

    /**
     * 基于消息、原始异常和错误码构造远程服务异常
     *
     * @param message   错误消息
     * @param throwable 原始异常
     * @param errorCode 错误码枚举
     */
    public RemoteException(String message, Throwable throwable, IErrorCode errorCode) {
        super(message, throwable, errorCode);
    }

    @Override
    public String toString() {
        return "RemoteException{" +
                "code='" + errorCode + "'," +
                "message='" + errorMessage + "'" +
                '}';
    }
}
