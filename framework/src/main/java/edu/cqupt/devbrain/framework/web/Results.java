package edu.cqupt.devbrain.framework.web;

import edu.cqupt.devbrain.framework.convention.Result;
import edu.cqupt.devbrain.framework.errorcode.BaseErrorCode;
import edu.cqupt.devbrain.framework.exception.AbstractException;

import java.util.Optional;

/**
 * 全局统一返回结果构造器（工厂类）。
 * <p>提供一组静态方法，用于快速构建 {@link Result} 对象。
 * 所有方法均会自动附带当前请求的 {@code requestId}，确保链路可追踪。
 * 推荐通过此类创建返回值，而非直接 new {@link Result}。</p>
 *
 * @see Result
 * @see RequestIdContext
 */
public final class Results {

    private Results() {
    }

    /**
     * 构造无数据的成功响应。
     *
     * @return 状态码为 {@code 0}、数据为空的成功 {@link Result}
     */
    public static Result<Void> success() {
        return attachRequestId(new Result<Void>()
                .setCode(Result.SUCCESS_CODE));
    }

    /**
     * 构造带返回数据的成功响应。
     *
     * @param data 响应数据，泛型类型由调用方决定
     * @param <T>  响应数据类型
     * @return 状态码为 {@code 0}、携带给定数据的成功 {@link Result}
     */
    public static <T> Result<T> success(T data) {
        return attachRequestId(new Result<T>()
                .setCode(Result.SUCCESS_CODE)
                .setData(data));
    }

    /**
     * 构建通用的服务端失败响应（使用默认错误码和错误消息）。
     *
     * @return 状态码和消息取自 {@link BaseErrorCode#SERVICE_ERROR} 的失败 {@link Result}
     */
    public static Result<Void> failure() {
        return attachRequestId(new Result<Void>()
                .setCode(BaseErrorCode.SERVICE_ERROR.code())
                .setMessage(BaseErrorCode.SERVICE_ERROR.message()));
    }

    /**
     * 通过业务异常对象构建失败响应。
     * <p>从异常中提取错误码和错误消息；若异常未携带则使用默认值。</p>
     *
     * @param abstractException 业务异常对象
     * @return 携带异常错误码和消息的失败 {@link Result}
     */
    public static Result<Void> failure(AbstractException abstractException) {
        String errorCode = Optional.ofNullable(abstractException.errorCode)
                .orElse(BaseErrorCode.SERVICE_ERROR.code());
        String errorMessage = Optional.ofNullable(abstractException.errorMessage)
                .orElse(BaseErrorCode.SERVICE_ERROR.message());
        return attachRequestId(new Result<Void>()
                .setCode(errorCode)
                .setMessage(errorMessage));
    }

    /**
     * 通过指定错误码和错误消息构建失败响应。
     *
     * @param errorCode    业务错误码
     * @param errorMessage 错误描述信息
     * @return 携带指定错误码和消息的失败 {@link Result}
     */
    public static Result<Void> failure(String errorCode, String errorMessage) {
        return attachRequestId(new Result<Void>()
                .setCode(errorCode)
                .setMessage(errorMessage));
    }

    /**
     * 为 {@link Result} 附加当前线程的请求 ID。
     */
    private static <T> Result<T> attachRequestId(Result<T> result) {
        return result.setRequestId(RequestIdContext.currentId());
    }
}
