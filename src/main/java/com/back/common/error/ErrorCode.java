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
    INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "서버 오류가 발생했습니다."),
    CSRF_INVALID(HttpStatus.FORBIDDEN, "요청 위조 방지 토큰이 없거나 올바르지 않습니다."),

    // 회원·인증 (정책 제7장)
    EMAIL_DUPLICATED(HttpStatus.CONFLICT, "이미 가입된 이메일입니다."),
    EMAIL_PENDING_VERIFICATION(HttpStatus.CONFLICT, "인증을 기다리는 이메일입니다. 메일함을 확인하거나 인증 메일을 다시 받으세요."),
    EMAIL_NOT_VERIFIED(HttpStatus.FORBIDDEN, "이메일 인증을 마쳐야 로그인할 수 있습니다."),
    INVALID_TOKEN(HttpStatus.BAD_REQUEST, "링크가 유효하지 않거나 만료되었습니다."),
    INVALID_CREDENTIALS(HttpStatus.UNAUTHORIZED, "이메일 또는 비밀번호가 올바르지 않습니다."),
    ACCOUNT_LOCKED(HttpStatus.LOCKED, "로그인에 여러 번 실패해 계정이 잠겼습니다."),
    INVALID_MFA_CODE(HttpStatus.UNAUTHORIZED, "인증 코드가 올바르지 않습니다."),
    MFA_SESSION_EXPIRED(HttpStatus.UNAUTHORIZED, "인증 시간이 지났습니다. 다시 로그인하세요."),
    REAUTH_FAILED(HttpStatus.BAD_REQUEST, "본인 확인에 실패했습니다."),
    CURRENT_PASSWORD_MISMATCH(HttpStatus.BAD_REQUEST, "현재 비밀번호가 올바르지 않습니다."),
    TWO_FACTOR_ALREADY_ENABLED(HttpStatus.CONFLICT, "2단계 인증이 이미 켜져 있습니다."),
    TWO_FACTOR_NOT_ENABLED(HttpStatus.CONFLICT, "2단계 인증이 켜져 있지 않습니다."),
    TWO_FACTOR_SETUP_REQUIRED(HttpStatus.CONFLICT, "2단계 인증 등록을 먼저 시작하세요."),
    TWO_FACTOR_CODE_MISMATCH(HttpStatus.BAD_REQUEST, "인증 앱의 코드가 올바르지 않습니다."),
    WITHDRAWAL_BLOCKED(HttpStatus.CONFLICT, "지금은 탈퇴할 수 없습니다.");

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
