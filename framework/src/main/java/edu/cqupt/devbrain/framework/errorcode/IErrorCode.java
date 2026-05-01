package edu.cqupt.devbrain.framework.errorcode;

/**
 * 平台错误码接口
 * 定义错误码抽象接口，由各错误码类实现接口方法
 */
public interface IErrorCode {

    /**
     * 获取错误码
     *
     * @return 错误码字符串
     */
    String code();

    /**
     * 获取错误信息
     *
     * @return 错误信息字符串
     */
    String message();
}
