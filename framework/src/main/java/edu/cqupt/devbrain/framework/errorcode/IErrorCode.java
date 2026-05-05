package edu.cqupt.devbrain.framework.errorcode;

/**
 * 平台错误码统一接口。
 * <p>定义错误码和错误消息的抽象契约，所有错误码枚举或实现类均需实现此接口，
 * 以保证错误码体系的一致性和可扩展性。</p>
 *
 * @see edu.cqupt.devbrain.framework.errorcode.BaseErrorCode
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
