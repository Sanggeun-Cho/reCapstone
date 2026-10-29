package com.back.common.error;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

/**
 * 도메인 오류 코드. 응답 {@code ProblemDetail}의 {@code code} 속성에는 상수 이름이 들어간다.
 */
@Getter
@RequiredArgsConstructor
public enum ErrorCode {

    // 공통
    INVALID_INPUT(HttpStatus.BAD_REQUEST, "요청 값이 올바르지 않습니다."),
    UNAUTHENTICATED(HttpStatus.UNAUTHORIZED, "인증이 필요합니다."),
    ACCESS_DENIED(HttpStatus.FORBIDDEN, "권한이 없습니다."),
    NOT_FOUND(HttpStatus.NOT_FOUND, "대상을 찾을 수 없습니다."),
    METHOD_NOT_ALLOWED(HttpStatus.METHOD_NOT_ALLOWED, "지원하지 않는 메서드입니다."),
    NOT_ACCEPTABLE(HttpStatus.NOT_ACCEPTABLE, "지원하지 않는 응답 형식입니다."),
    CONFLICT(HttpStatus.CONFLICT, "현재 상태와 충돌하는 요청입니다."),
    UNSUPPORTED_MEDIA_TYPE(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "지원하지 않는 요청 형식입니다."),
    INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "서버 오류가 발생했습니다.");

    private final HttpStatus status;
    private final String message;

    /**
     * 프레임워크가 던진 예외처럼 도메인 코드가 없는 응답에 붙일 공통 코드를 고른다.
     */
    public static ErrorCode fromStatus(int status) {
        for (ErrorCode code : values()) {
            if (code.status.value() == status) {
                return code;
            }
        }
        return status < 500 ? INVALID_INPUT : INTERNAL_ERROR;
    }
}
