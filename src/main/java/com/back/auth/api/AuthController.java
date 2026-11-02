package com.back.auth.api;

import com.back.auth.application.AuthService;
import com.back.auth.application.EmailVerificationService;
import com.back.auth.application.PasswordResetService;
import com.back.auth.web.SessionLogin;
import io.swagger.v3.oas.annotations.Parameter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {

    static final String CSRF_COOKIE = "XSRF-TOKEN";

    private final AuthService authService;
    private final EmailVerificationService emailVerificationService;
    private final PasswordResetService passwordResetService;
    private final SessionLogin sessionLogin;

    /**
     * CSRF 토큰 쿠키를 발급한다. 토큰을 읽는 순간 저장소가 쿠키를 내려보낸다.
     */
    @GetMapping("/csrf")
    public AuthDto.CsrfRes csrf(@Parameter(hidden = true) CsrfToken csrfToken) {
        csrfToken.getToken();
        return new AuthDto.CsrfRes(csrfToken.getHeaderName(), CSRF_COOKIE);
    }

    @PostMapping("/login")
    public AuthDto.LoginRes login(@Valid @RequestBody AuthDto.LoginReq req,
            HttpServletRequest request, HttpServletResponse response) {
        AuthService.LoginResult result = authService.login(req.email(), req.password());
        if (result.mfaRequired()) {
            sessionLogin.startMfa(result.userId(), request);
            return AuthDto.LoginRes.mfaPending();
        }
        sessionLogin.establish(result.userId(), request, response);
        return AuthDto.LoginRes.completed(result.user());
    }

    @PostMapping("/login/mfa")
    public AuthDto.LoginRes loginMfa(@Valid @RequestBody AuthDto.MfaReq req,
            HttpServletRequest request, HttpServletResponse response) {
        long userId = sessionLogin.pendingMfaUser(request);
        AuthDto.UserSummary user = authService.completeMfa(userId, req.code());
        sessionLogin.establish(userId, request, response);
        return AuthDto.LoginRes.completed(user);
    }

    @PostMapping("/logout")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void logout(HttpServletRequest request) {
        sessionLogin.logout(request);
    }

    @PostMapping("/email-verification")
    public AuthDto.EmailVerificationRes verifyEmail(@Valid @RequestBody AuthDto.EmailVerificationReq req) {
        return emailVerificationService.verify(req.token());
    }

    @PostMapping("/email-verification/resend")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public void resendVerification(@Valid @RequestBody AuthDto.EmailReq req) {
        emailVerificationService.resend(req.email());
    }

    @PostMapping("/password-reset")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public void requestPasswordReset(@Valid @RequestBody AuthDto.EmailReq req) {
        passwordResetService.request(req.email());
    }

    @PostMapping("/password-reset/confirm")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void confirmPasswordReset(@Valid @RequestBody AuthDto.PasswordResetConfirmReq req) {
        passwordResetService.confirm(req.token(), req.newPassword());
    }
}
