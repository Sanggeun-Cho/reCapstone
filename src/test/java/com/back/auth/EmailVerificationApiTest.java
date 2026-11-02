package com.back.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.back.support.AuthApiTest;
import java.time.Duration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

class EmailVerificationApiTest extends AuthApiTest {

    private static final String EMAIL = "bob@example.com";

    @Test
    @DisplayName("AUTH-06: 메일의 토큰으로 인증하면 계정이 활성화된다")
    void verify() throws Exception {
        signup(EMAIL, PASSWORD);

        verify(lastMailToken(EMAIL))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ACTIVE"));

        assertThat(jdbcTemplate.queryForObject(
                "SELECT email_verified_at IS NOT NULL FROM users WHERE email = ?", Boolean.class, EMAIL)).isTrue();
        login(EMAIL, PASSWORD).andExpect(status().isOk());
    }

    @Test
    @DisplayName("AUTH-06b: 같은 토큰은 한 번만 쓸 수 있다")
    void tokenReuse() throws Exception {
        signup(EMAIL, PASSWORD);
        String token = lastMailToken(EMAIL);
        verify(token).andExpect(status().isOk());

        verify(token)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_TOKEN"));
    }

    @Test
    @DisplayName("AUTH-06c: 발급 후 24시간이 지난 토큰은 400 INVALID_TOKEN이고 계정은 대기 상태로 남는다")
    void tokenExpired() throws Exception {
        signup(EMAIL, PASSWORD);
        String token = lastMailToken(EMAIL);
        clock.advance(Duration.ofHours(24).plusMinutes(1));

        verify(token)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_TOKEN"));
        assertThat(jdbcTemplate.queryForObject(
                "SELECT status FROM users WHERE email = ?", String.class, EMAIL)).isEqualTo("PENDING");
    }

    @Test
    @DisplayName("AUTH-06d: 존재하지 않는 토큰은 400 INVALID_TOKEN")
    void unknownToken() throws Exception {
        verify("no-such-token")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_TOKEN"));
    }

    @Test
    @DisplayName("AUTH-07: 재발송하면 새 메일이 가고, 이전 토큰은 무효가 된다")
    void resend() throws Exception {
        signup(EMAIL, PASSWORD);
        String oldToken = lastMailToken(EMAIL);

        resend(EMAIL).andExpect(status().isAccepted());

        assertThat(mailsTo(EMAIL)).hasSize(2);
        String newToken = lastMailToken(EMAIL);
        verify(oldToken).andExpect(status().isBadRequest());
        verify(newToken).andExpect(status().isOk());
    }

    @Test
    @DisplayName("AUTH-07b: 활성 계정이나 없는 이메일로 재발송을 요청해도 202이고 메일은 가지 않는다")
    void resendWithoutPendingAccount() throws Exception {
        createActiveUser("active@example.com");

        resend("active@example.com").andExpect(status().isAccepted());
        resend("nobody@example.com").andExpect(status().isAccepted());

        assertThat(greenMail.getReceivedMessages()).isEmpty();
    }

    @Test
    @DisplayName("AUTH-08: 인증 대기 계정은 올바른 비밀번호로도 로그인할 수 없다 (403 EMAIL_NOT_VERIFIED)")
    void pendingCannotLogin() throws Exception {
        signup(EMAIL, PASSWORD);

        login(EMAIL, PASSWORD)
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("EMAIL_NOT_VERIFIED"))
                .andExpect(cookie().doesNotExist(SESSION_COOKIE));
    }

    @Test
    @DisplayName("AUTH-08b: 인증 대기 계정에 틀린 비밀번호를 넣으면 401 (대기 상태를 드러내지 않음)")
    void pendingWithWrongPassword() throws Exception {
        signup(EMAIL, PASSWORD);

        login(EMAIL, "wrong-password")
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"));
    }

    private ResultActions verify(String token) throws Exception {
        return mockMvc.perform(post("/api/v1/auth/email-verification").with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(json("token", token)));
    }

    private ResultActions resend(String email) throws Exception {
        return mockMvc.perform(post("/api/v1/auth/email-verification/resend").with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(json("email", email)));
    }
}
