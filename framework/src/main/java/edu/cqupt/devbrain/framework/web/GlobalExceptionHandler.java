package edu.cqupt.devbrain.framework.web;

import edu.cqupt.devbrain.framework.convention.Result;
import edu.cqupt.devbrain.framework.errorcode.BaseErrorCode;
import edu.cqupt.devbrain.framework.exception.AbstractException;
import edu.cqupt.devbrain.framework.exception.ClientException;
import edu.cqupt.devbrain.framework.exception.ServiceException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * 全局异常处理器（{@code @RestControllerAdvice}）。
 * <p>拦截控制器层抛出的各种异常，统一转换为 {@link Result} 格式返回给前端，
 * 避免将异常堆栈直接暴露给客户端。处理优先级如下：</p>
 * <ol>
 *   <li>{@link ClientException} -- 客户端参数/业务错误，返回 HTTP 400</li>
 *   <li>{@link ServiceException} -- 服务端内部错误，返回 HTTP 500</li>
 *   <li>{@link AbstractException} -- 其他自定义业务异常，返回 HTTP 500</li>
 *   <li>{@link MethodArgumentNotValidException} -- 参数校验失败，返回 HTTP 400</li>
 *   <li>{@link ConstraintViolationException} -- 约束校验失败，返回 HTTP 400</li>
 *   <li>{@link Throwable} -- 兜底处理，返回 HTTP 500</li>
 * </ol>
 *
 * @see Results
 * @see BaseErrorCode
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    /**
     * 拦截客户端异常（{@link ClientException}），返回 HTTP 400。
     */
    @ExceptionHandler(ClientException.class)
    public ResponseEntity<Result<Void>> clientException(HttpServletRequest request, ClientException ex) {
        log.warn("[{}] {} [client] {}", request.getMethod(), request.getRequestURI(), ex.errorMessage);
        return ResponseEntity.status(ex.getHttpStatus())
                .body(Results.failure(ex.errorCode, ex.errorMessage));
    }

    /**
     * 拦截服务端异常（{@link ServiceException}），返回 HTTP 500。
     */
    @ExceptionHandler(ServiceException.class)
    public ResponseEntity<Result<Void>> serviceException(HttpServletRequest request, ServiceException ex) {
        log.error("[{}] {} [service] {}", request.getMethod(), request.getRequestURI(), ex.errorMessage);
        return ResponseEntity.status(ex.getHttpStatus())
                .body(Results.failure(ex.errorCode, ex.errorMessage));
    }

    /**
     * 拦截抽象异常体系中的所有异常（{@link AbstractException}），返回 HTTP 500。
     */
    @ExceptionHandler(AbstractException.class)
    public ResponseEntity<Result<Void>> abstractException(HttpServletRequest request, AbstractException ex) {
        if (ex.getCause() != null) {
            log.error("[{}] {} [ex] {}", request.getMethod(), request.getRequestURI(), ex, ex.getCause());
        } else {
            log.error("[{}] {} [ex] {}", request.getMethod(), request.getRequestURI(), ex.errorMessage);
        }
        return ResponseEntity.internalServerError()
                .body(Results.failure(ex));
    }

    /**
     * 拦截 Spring 参数校验异常（{@link MethodArgumentNotValidException}），返回 HTTP 400。
     * <p>提取第一条字段错误信息作为响应消息。</p>
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Result<Void>> methodArgumentNotValidException(MethodArgumentNotValidException ex) {
        FieldError error = ex.getBindingResult().getFieldErrors().stream().findFirst().orElse(null);
        String message = error == null ? "请求参数不合法" : error.getDefaultMessage();
        return ResponseEntity.badRequest().body(Results.failure(BaseErrorCode.CLIENT_ERROR.code(), message));
    }

    /**
     * 拦截 Bean Validation 约束校验异常（{@link ConstraintViolationException}），返回 HTTP 400。
     */
    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<Result<Void>> constraintViolationException(ConstraintViolationException ex) {
        return ResponseEntity.badRequest().body(Results.failure(BaseErrorCode.CLIENT_ERROR.code(), ex.getMessage()));
    }

    /**
     * 兜底拦截所有未捕获异常（{@link Throwable}），返回 HTTP 500。
     * <p>返回通用错误提示"系统异常，请稍后重试"，避免泄露内部实现细节。</p>
     */
    @ExceptionHandler(Throwable.class)
    public ResponseEntity<Result<Void>> throwable(HttpServletRequest request, Throwable ex) {
        log.error("[{}] {} failed", request.getMethod(), request.getRequestURI(), ex);
        return ResponseEntity.internalServerError()
                .body(Results.failure(BaseErrorCode.SERVICE_ERROR.code(), "系统异常，请稍后重试"));
    }
}
