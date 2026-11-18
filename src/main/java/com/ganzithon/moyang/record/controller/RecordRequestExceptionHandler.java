package com.ganzithon.moyang.record.controller;

import com.ganzithon.moyang.common.exception.ErrorCode;
import com.ganzithon.moyang.common.response.ApiResponse;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.bind.MissingServletRequestParameterException;

@Order(Ordered.HIGHEST_PRECEDENCE)
@RestControllerAdvice(assignableTypes=RecordController.class)
public class RecordRequestExceptionHandler {
    @ExceptionHandler({MethodArgumentTypeMismatchException.class,MissingServletRequestParameterException.class})
    public ResponseEntity<ApiResponse<Void>> invalid(Exception e){var code=ErrorCode.INVALID_REQUEST;return ResponseEntity.badRequest().body(ApiResponse.fail(code.name(),code.getMessage()));}
}
