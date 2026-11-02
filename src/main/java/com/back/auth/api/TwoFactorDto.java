package com.back.auth.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.List;

public final class TwoFactorDto {

    private TwoFactorDto() {
    }

    /**
     * @param secret     인증 앱에 직접 입력할 Base32 비밀키
     * @param otpauthUri QR 코드로 보여 줄 등록 URI
     */
    public record SetupRes(String secret, String otpauthUri) {
    }

    public record CodeReq(@NotBlank String code) {
    }

    /** 복구 코드 원문. 이 응답에서만 보여 주고 다시 조회할 수 없다. */
    public record RecoveryCodesRes(List<String> recoveryCodes) {
    }

    public record DisableReq(@NotNull String password, @NotBlank String code) {
    }
}
