package com.back.auth.api;

import com.back.auth.application.TwoFactorService;
import com.back.common.security.AuthUser;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/users/me/2fa")
@RequiredArgsConstructor
public class TwoFactorController {

    private final TwoFactorService twoFactorService;

    @PostMapping("/setup")
    public TwoFactorDto.SetupRes setup(@AuthenticationPrincipal AuthUser user) {
        return twoFactorService.setup(user.userId());
    }

    @PostMapping("/enable")
    public TwoFactorDto.RecoveryCodesRes enable(@AuthenticationPrincipal AuthUser user,
            @Valid @RequestBody TwoFactorDto.CodeReq req) {
        return twoFactorService.enable(user.userId(), req.code());
    }

    @PostMapping("/disable")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void disable(@AuthenticationPrincipal AuthUser user, @Valid @RequestBody TwoFactorDto.DisableReq req) {
        twoFactorService.disable(user.userId(), req.password(), req.code());
    }

    @PostMapping("/recovery-codes")
    public TwoFactorDto.RecoveryCodesRes regenerateRecoveryCodes(@AuthenticationPrincipal AuthUser user,
            @Valid @RequestBody TwoFactorDto.CodeReq req) {
        return twoFactorService.regenerateRecoveryCodes(user.userId(), req.code());
    }
}
