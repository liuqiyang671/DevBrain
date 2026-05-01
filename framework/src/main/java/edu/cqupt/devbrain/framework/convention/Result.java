package edu.cqupt.devbrain.framework.convention;

import java.io.Serial;
import java.io.Serializable;

/**
 * 全局统一返回结果对象
 *
 * <p>
 * 用于规范化所有 API 接口的返回格式，确保前后端交互的一致性
 * 所有接口返回都应使用此对象包装，避免不同开发人员定义不一致的返回结构
 * </p>
 *
 * @param <T> 响应数据的类型
 */
public class Result<T> implements Serializable {

    @Serial
    private static final long serialVersionUID = 7471710030971385443L;

    /**
     * 成功状态码
     */
    public static final String SUCCESS_CODE = "0";

    /**
     * 状态码
     */
    private String code;

    /**
     * 响应消息
     */
    private String message;

    /**
     * 响应数据
     */
    private T data;

    /**
     * 请求追踪 ID，用于链路追踪和问题排查
     */
    private String requestId;

    public String getCode() {
        return code;
    }

    public Result<T> setCode(String code) {
        this.code = code;
        return this;
    }

    public String getMessage() {
        return message;
    }

    public Result<T> setMessage(String message) {
        this.message = message;
        return this;
    }

    public T getData() {
        return data;
    }

    public Result<T> setData(T data) {
        this.data = data;
        return this;
    }

    public String getRequestId() {
        return requestId;
    }

    public Result<T> setRequestId(String requestId) {
        this.requestId = requestId;
        return this;
    }

    public boolean isSuccess() {
        return SUCCESS_CODE.equals(code);
    }
}
