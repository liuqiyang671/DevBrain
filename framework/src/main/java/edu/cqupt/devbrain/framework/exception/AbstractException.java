package edu.cqupt.devbrain.framework.exception;

import edu.cqupt.devbrain.framework.errorcode.IErrorCode;
import org.springframework.util.StringUtils;

import java.util.Optional;

/**
 * 抽象项目中三类异常体系，客户端异常、服务端异常以及远程服务调用异常
 *
 * <p>
 * 所有业务异常的基类，统一定义错误码和错误信息。
 * 通过 {@link IErrorCode} 接口实现错误码的标准化管理。
 * </p>
 */
public abstract class AbstractException extends RuntimeException {

    /**
     * 错误码
     */
    public final String errorCode;

    /**
     * 错误信息
     */
    public final String errorMessage;

    /**
     * 构造函数
     *
     * @param message   错误消息，如果为空则使用 errorCode 的默认消息
     * @param throwable 原始异常
     * @param errorCode 错误码枚举
     */
    public AbstractException(String message, Throwable throwable, IErrorCode errorCode) {
        super(message, throwable);
        this.errorCode = errorCode.code();
        this.errorMessage = Optional.ofNullable(StringUtils.hasLength(message) ? message : null).orElse(errorCode.message());
    }
}
