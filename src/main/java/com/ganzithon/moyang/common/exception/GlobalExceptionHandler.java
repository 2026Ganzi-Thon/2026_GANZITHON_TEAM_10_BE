package com.ganzithon.moyang.common.exception;

import com.ganzithon.moyang.common.response.ApiResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.MultipartException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ApiResponse<Void>> handleBusiness(BusinessException e) {
        ErrorCode code = e.getErrorCode();
        return ResponseEntity.status(code.getStatus())
                .body(ApiResponse.fail(code.name(), e.getMessage()));
    }

    @ExceptionHandler({MethodArgumentNotValidException.class, HttpMessageNotReadableException.class,
            MethodArgumentTypeMismatchException.class, MultipartException.class})
    public ResponseEntity<ApiResponse<Void>> handleInvalid(Exception e) {
        ErrorCode code = ErrorCode.INVALID_REQUEST;
        return ResponseEntity.status(code.getStatus())
                .body(ApiResponse.fail(code.name(), code.getMessage()));
    }

    /** 같은 대화에 요청이 동시에 들어와 @Version 충돌이 난 경우 (ai). */
    @ExceptionHandler(OptimisticLockingFailureException.class)
    public ResponseEntity<ApiResponse<Void>> handleConflict(OptimisticLockingFailureException e) {
        ErrorCode code = ErrorCode.CONVERSATION_BUSY;
        return ResponseEntity.status(code.getStatus())
                .body(ApiResponse.fail(code.name(), code.getMessage()));
    }

    /** 업로드 용량 초과 (drawing). 상위 타입인 MultipartException보다 먼저 잡힌다. */
    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<ApiResponse<Void>> handleTooLarge(MaxUploadSizeExceededException e) {
        ErrorCode code = ErrorCode.IMAGE_TOO_LARGE;
        return ResponseEntity.status(code.getStatus())
                .body(ApiResponse.fail(code.name(), code.getMessage()));
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ApiResponse<Void>> handleNoResource(NoResourceFoundException e) {
        ErrorCode code = ErrorCode.NOT_FOUND;
        return ResponseEntity.status(code.getStatus())
                .body(ApiResponse.fail(code.name(), code.getMessage()));
    }

    /** 그 외 예외: 내부 내용은 응답에 넣지 않고 서버 로그에만 남긴다. */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleUnexpected(Exception e) {
        log.error("처리되지 않은 예외", e);
        ErrorCode code = ErrorCode.INTERNAL_ERROR;
        return ResponseEntity.status(code.getStatus())
                .body(ApiResponse.fail(code.name(), code.getMessage()));
    }
}
