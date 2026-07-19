package com.agentx.common.exception;

import com.agentx.common.api.ApiResponse;
import com.agentx.common.api.ErrorCode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(BizException.class)
    public ResponseEntity<ApiResponse<Void>> handleBiz(BizException e) {
        return ResponseEntity.status(e.getErrorCode().getHttpStatus())
                .body(ApiResponse.error(e.getErrorCode(), e.getMessage()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Void>> handleValidation(MethodArgumentNotValidException e) {
        String msg = e.getBindingResult().getFieldErrors().stream()
                .map(f -> f.getField() + ": " + f.getDefaultMessage())
                .findFirst().orElse("参数校验失败");
        return ResponseEntity.badRequest().body(ApiResponse.error(ErrorCode.BAD_REQUEST, msg));
    }

    /**
     * 路由不存在单列为 404：新前端调到旧后端（未重启/未升级）的典型场景。
     * 若并入下方兜底会伪装成「服务内部错误」，把版本不一致误导成服务故障。
     */
    @ExceptionHandler(org.springframework.web.servlet.resource.NoResourceFoundException.class)
    public ResponseEntity<ApiResponse<Void>> handleNoRoute(
            org.springframework.web.servlet.resource.NoResourceFoundException e) {
        log.warn("接口不存在: {}", e.getResourcePath());
        return ResponseEntity.status(404)
                .body(ApiResponse.error(ErrorCode.NOT_FOUND, "接口不存在（后端版本可能过旧，请重启/升级后端）"));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleOther(Exception e) {
        log.error("unhandled exception", e);
        return ResponseEntity.internalServerError()
                .body(ApiResponse.error(ErrorCode.INTERNAL_ERROR, "服务内部错误"));
    }
}
