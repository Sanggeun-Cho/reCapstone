package com.back.support;

import com.back.common.error.BusinessException;
import com.back.common.error.ErrorCode;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 공통 오류 응답을 검증하기 위한 테스트 전용 엔드포인트.
 */
@RestController
@RequestMapping("/api/v1/test-probe")
public class ProbeController {

    @GetMapping("/business-error")
    public void businessError() {
        throw new BusinessException(ErrorCode.CONFLICT, "이미 진행 중인 인계가 있습니다.");
    }

    @GetMapping("/denied")
    public void denied() {
        throw new AccessDeniedException("denied");
    }

    @GetMapping("/unexpected")
    public void unexpected() {
        throw new IllegalStateException("boom");
    }

    @PostMapping("/validation")
    public void validation(@Valid @RequestBody ProbeReq req) {
    }

    public record ProbeReq(@NotBlank String name, String password) {
    }
}
