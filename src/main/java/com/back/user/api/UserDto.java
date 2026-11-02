package com.back.user.api;

import com.back.user.domain.User;
import com.back.user.domain.UserStatus;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.Instant;

public final class UserDto {

    /** 비밀번호 길이 (정책 7.6). BCrypt가 72바이트까지만 쓰므로 상한을 둔다. */
    public static final int PASSWORD_MIN = 8;
    public static final int PASSWORD_MAX = 64;
    static final String PHONE_PATTERN = "[0-9+\\-() ]+";

    private UserDto() {
    }

    public record SignupReq(
            @NotBlank @Email @Size(max = 254) String email,
            @NotNull @Size(min = PASSWORD_MIN, max = PASSWORD_MAX) String password,
            @NotBlank @Size(max = 50) String name,
            @Size(max = 20) @Pattern(regexp = PHONE_PATTERN, message = "숫자와 + - ( ) 공백만 쓸 수 있습니다.")
            String workPhone) {
    }

    public record SignupRes(long id, String email, String name, String workPhone, UserStatus status) {

        public static SignupRes from(User user) {
            return new SignupRes(user.getId(), user.getEmail(), user.getName(), user.getWorkPhone(), user.getStatus());
        }
    }

    public record DetailRes(
            long id, String email, String name, String workPhone, boolean twoFactorEnabled, Instant createdAt) {

        public static DetailRes from(User user) {
            return new DetailRes(user.getId(), user.getEmail(), user.getName(), user.getWorkPhone(),
                    user.isTwoFactorEnabled(), user.getCreatedAt());
        }
    }

    /** 이름과 업무용 전화번호를 통째로 바꾼다. {@code workPhone}이 null이면 지운다. 이메일은 바꿀 수 없다. */
    public record UpdateReq(
            @NotBlank @Size(max = 50) String name,
            @Size(max = 20) @Pattern(regexp = PHONE_PATTERN, message = "숫자와 + - ( ) 공백만 쓸 수 있습니다.")
            String workPhone) {
    }

    public record ChangePasswordReq(
            @NotNull String currentPassword,
            @NotNull @Size(min = PASSWORD_MIN, max = PASSWORD_MAX) String newPassword) {
    }
}
