package com.ganzithon.moyang.common.exception;

import org.springframework.http.HttpStatus;

/**
 * 공통 에러 코드. 도메인별 코드(MEETING_FULL 등)는 각 도메인 PR에서 여기에 추가한다.
 */
public enum ErrorCode {
    INVALID_REQUEST(HttpStatus.BAD_REQUEST, "요청 값이 올바르지 않아요."),
    UNAUTHORIZED(HttpStatus.UNAUTHORIZED, "로그인이 필요해요."),
    FORBIDDEN(HttpStatus.FORBIDDEN, "접근 권한이 없어요."),
    NOT_FOUND(HttpStatus.NOT_FOUND, "찾는 대상이 없어요."),

    // drawing · ai (박준서)
    INVALID_IMAGE_FORMAT(HttpStatus.BAD_REQUEST, "jpg 또는 png 이미지만 올릴 수 있어요."),
    IMAGE_TOO_LARGE(HttpStatus.BAD_REQUEST, "이미지는 10MB까지 올릴 수 있어요."),
    INVALID_INPUT_TYPE(HttpStatus.BAD_REQUEST, "inputType은 PHOTO, UPLOAD, CANVAS 중 하나여야 해요."),
    NOTE_TOO_LONG(HttpStatus.BAD_REQUEST, "그림 설명은 200자까지 쓸 수 있어요."),
    INVALID_MESSAGE_TYPE(HttpStatus.BAD_REQUEST, "type은 ANSWER, DONT_KNOW, SKIP 중 하나여야 해요."),
    EMPTY_CONTENT(HttpStatus.BAD_REQUEST, "답변 내용을 입력해 주세요."),
    CONTENT_TOO_LONG(HttpStatus.BAD_REQUEST, "답변은 1000자까지 쓸 수 있어요."),
    INVALID_STATUS(HttpStatus.BAD_REQUEST, "status 값을 확인해 주세요."),
    NOT_PARTICIPANT(HttpStatus.FORBIDDEN, "이 모임의 참여자가 아니에요."),
    DRAWING_NOT_FOUND(HttpStatus.NOT_FOUND, "그림을 찾을 수 없어요."),
    TOPIC_NOT_FOUND(HttpStatus.NOT_FOUND, "주제를 찾을 수 없어요."),
    CONVERSATION_NOT_FOUND(HttpStatus.NOT_FOUND, "대화를 찾을 수 없어요."),
    MEETING_NOT_FOUND(HttpStatus.NOT_FOUND, "모임을 찾을 수 없어요."),
    NO_DRAWING_IN_MEETING(HttpStatus.NOT_FOUND, "모임에서 공유한 그림이 없어 카드가 없어요."),
    CONVERSATION_ALREADY_EXISTS(HttpStatus.CONFLICT, "이미 대화가 있는 그림이에요."),
    CONVERSATION_ALREADY_ENDED(HttpStatus.CONFLICT, "이미 끝난 대화예요."),
    CONVERSATION_BUSY(HttpStatus.CONFLICT, "이전 요청을 처리하고 있어요. 잠시 후 다시 시도해 주세요."),
    NOT_ENOUGH_WOOL(HttpStatus.CONFLICT, "양털이 부족해요."),
    EXTEND_LIMIT_REACHED(HttpStatus.CONFLICT, "더 이야기할 수 있는 횟수를 모두 썼어요."),
    MEETING_NOT_ENDED(HttpStatus.CONFLICT, "아직 모임이 끝나지 않았어요."),

    REFUND_NOT_ELIGIBLE(HttpStatus.CONFLICT, "환불할 양털 사용 내역이 없어요."),
    SHEEP_BALANCE_LIMIT(HttpStatus.CONFLICT, "양털 잔액 한도를 초과했어요."),

    RECORD_NOT_FOUND(HttpStatus.NOT_FOUND, "기록을 찾을 수 없어요."),
    RECORD_NOT_READY(HttpStatus.CONFLICT, "회고 저장에 필요한 데이터가 아직 준비되지 않았어요."),
    RECORD_SOURCE_UNAVAILABLE(HttpStatus.SERVICE_UNAVAILABLE, "그림·AI 기록 연동이 아직 준비되지 않았어요."),

    INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "서버에 문제가 생겼어요. 잠시 후 다시 시도해 주세요.");

    private final HttpStatus status;
    private final String message;

    ErrorCode(HttpStatus status, String message) {
        this.status = status;
        this.message = message;
    }

    public HttpStatus getStatus() {
        return status;
    }

    public String getMessage() {
        return message;
    }
}
