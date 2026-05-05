package edu.cqupt.devbrain.framework.exception;

import edu.cqupt.devbrain.framework.errorcode.IErrorCode;
import org.springframework.util.StringUtils;

import java.util.Optional;

/**
 * 业务异常体系的抽象基类。
 * <p>项目中三类异常（{@link ClientException}、{@link ServiceException}、{@link RemoteException}）
 * 均继承自本类。统一持有错误码和错误信息，通过 {@link IErrorCode} 接口实现错误码的标准化管理，
 * 确保异常体系结构清晰、易于统一拦截和处理。</p>
 *
 * @see IErrorCode
 * @see ClientException
 * @see ServiceException
 * @see RemoteException
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
