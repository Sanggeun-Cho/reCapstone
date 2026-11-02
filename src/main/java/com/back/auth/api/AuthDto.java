package com.back.auth.api;

import com.back.user.api.UserDto;
import com.back.user.domain.User;
import com.back.user.domain.UserStatus;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public final class AuthDto {

    private AuthDto() {
    }

    public record LoginReq(@NotBlank String email, @NotNull String password) {
    }

    /** 일회용 코드(6자리) 또는 복구 코드. */
    public record MfaReq(@NotBlank String code) {
    }

    /**
     * @param mfaRequired true이면 아직 로그인되지 않았고 {@code /auth/login/mfa}로 코드를 보내야 한다
     * @param user        로그인을 마쳤을 때만 채운다
     */
    public record LoginRes(boolean mfaRequired, UserSummary user) {

        public static LoginRes mfaPending() {
            return new LoginRes(true, null);
        }

        public static LoginRes completed(UserSummary user) {
            return new LoginRes(false, user);
        }
    }

    public record UserSummary(long id, String email, String name) {

        public static UserSummary from(User user) {
            return new UserSummary(user.getId(), user.getEmail(), user.getName());
        }
    }

    /**
     * SPA는 이 응답을 받은 뒤 {@code cookieName} 쿠키 값을 읽어 {@code headerName} 헤더로 보낸다.
     */
    public record CsrfRes(String headerName, String cookieName) {
    }

    public record EmailVerificationReq(@NotBlank String token) {
    }

    public record EmailVerificationRes(UserStatus status) {
    }

    public record EmailReq(@NotBlank @Email String email) {
    }

    public record PasswordResetConfirmReq(
            @NotBlank String token,
            @NotNull @Size(min = UserDto.PASSWORD_MIN, max = UserDto.PASSWORD_MAX) String newPassword) {
    }

    /**
     * 본인 재확인 (정책 7.5). 2단계 인증을 켰으면 {@code code}, 아니면 {@code password}를 보낸다.
     */
    public record ReauthReq(String password, String code) {
    }
}
