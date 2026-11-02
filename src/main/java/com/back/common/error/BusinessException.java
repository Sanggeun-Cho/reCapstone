package com.back.common.error;

import java.util.Map;
import lombok.Getter;

@Getter
public class BusinessException extends RuntimeException {

    private final ErrorCode errorCode;

    /**
     * 응답 {@code ProblemDetail}에 덧붙일 속성 (예: 잠금 해제 시각).
     */
    private final Map<String, Object> properties;

    public BusinessException(ErrorCode errorCode) {
        this(errorCode, errorCode.getMessage());
    }

    public BusinessException(ErrorCode errorCode, String detail) {
        this(errorCode, detail, Map.of());
    }

    public BusinessException(ErrorCode errorCode, String detail, Map<String, Object> properties) {
        super(detail);
        this.errorCode = errorCode;
        this.properties = Map.copyOf(properties);
    }
}
