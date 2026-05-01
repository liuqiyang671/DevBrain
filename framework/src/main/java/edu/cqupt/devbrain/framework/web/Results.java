package edu.cqupt.devbrain.framework.web;

import edu.cqupt.devbrain.framework.convention.Result;
import edu.cqupt.devbrain.framework.errorcode.BaseErrorCode;
import edu.cqupt.devbrain.framework.exception.AbstractException;

import java.util.Optional;

/**
 * 构建全局返回对象构造器，方便开发者构建全局返回对象
 */
public final class Results {

    private Results() {
    }

    /**
     * 构造成功响应
     */
    public static Result<Void> success() {
        return attachRequestId(new Result<Void>()
                .setCode(Result.SUCCESS_CODE));
    }

    /**
     * 构造带返回数据的成功响应
     */
    public static <T> Result<T> success(T data) {
        return attachRequestId(new Result<T>()
                .setCode(Result.SUCCESS_CODE)
                .setData(data));
    }

    /**
     * 构建服务端失败响应
     */
    public static Result<Void> failure() {
        return attachRequestId(new Result<Void>()
                .setCode(BaseErrorCode.SERVICE_ERROR.code())
                .setMessage(BaseErrorCode.SERVICE_ERROR.message()));
    }

    /**
     * 通过 {@link AbstractException} 构建失败响应
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
     * 通过 errorCode、errorMessage 构建失败响应
     */
    public static Result<Void> failure(String errorCode, String errorMessage) {
        return attachRequestId(new Result<Void>()
                .setCode(errorCode)
                .setMessage(errorMessage));
    }

    private static <T> Result<T> attachRequestId(Result<T> result) {
        return result.setRequestId(RequestIdContext.currentId());
    }
}
