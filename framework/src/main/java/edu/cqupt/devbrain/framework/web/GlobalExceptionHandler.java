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
 * 全局异常处理器
 * 拦截指定异常并通过优雅构建方式返回前端信息
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    /**
     * 拦截客户端异常（HTTP 400）
     */
    @ExceptionHandler(ClientException.class)
    public ResponseEntity<Result<Void>> clientException(HttpServletRequest request, ClientException ex) {
        log.warn("[{}] {} [client] {}", request.getMethod(), request.getRequestURI(), ex.errorMessage);
        return ResponseEntity.status(ex.getHttpStatus())
                .body(Results.failure(ex.errorCode, ex.errorMessage));
    }

    /**
     * 拦截服务端异常（HTTP 500）
     */
    @ExceptionHandler(ServiceException.class)
    public ResponseEntity<Result<Void>> serviceException(HttpServletRequest request, ServiceException ex) {
        log.error("[{}] {} [service] {}", request.getMethod(), request.getRequestURI(), ex.errorMessage);
        return ResponseEntity.status(ex.getHttpStatus())
                .body(Results.failure(ex.errorCode, ex.errorMessage));
    }

    /**
     * 拦截抽象异常体系中的所有异常
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
     * 拦截参数验证异常
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Result<Void>> methodArgumentNotValidException(MethodArgumentNotValidException ex) {
        FieldError error = ex.getBindingResult().getFieldErrors().stream().findFirst().orElse(null);
        String message = error == null ? "请求参数不合法" : error.getDefaultMessage();
        return ResponseEntity.badRequest().body(Results.failure(BaseErrorCode.CLIENT_ERROR.code(), message));
    }

    /**
     * 拦截约束校验异常
     */
    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<Result<Void>> constraintViolationException(ConstraintViolationException ex) {
        return ResponseEntity.badRequest().body(Results.failure(BaseErrorCode.CLIENT_ERROR.code(), ex.getMessage()));
    }

    /**
     * 拦截未捕获异常
     */
    @ExceptionHandler(Throwable.class)
    public ResponseEntity<Result<Void>> throwable(HttpServletRequest request, Throwable ex) {
        log.error("[{}] {} failed", request.getMethod(), request.getRequestURI(), ex);
        return ResponseEntity.internalServerError()
                .body(Results.failure(BaseErrorCode.SERVICE_ERROR.code(), "系统异常，请稍后重试"));
    }
}
