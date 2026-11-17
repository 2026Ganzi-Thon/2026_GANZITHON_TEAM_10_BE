package com.ganzithon.moyang.sheep.controller;
import com.ganzithon.moyang.common.exception.ErrorCode;
import com.ganzithon.moyang.common.response.ApiResponse;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
/** Scoped to Sheep to avoid replacing the shared handler in the pending drawing/AI PR. */
@RestControllerAdvice(assignableTypes=SheepController.class) @Order(Ordered.HIGHEST_PRECEDENCE)
public class SheepRequestExceptionHandler {
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ApiResponse<Void>> invalid(Exception e){var code=ErrorCode.INVALID_REQUEST;return ResponseEntity.badRequest().body(ApiResponse.fail(code.name(),code.getMessage()));}
}
