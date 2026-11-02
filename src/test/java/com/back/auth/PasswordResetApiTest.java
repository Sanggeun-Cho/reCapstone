package com.back.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.back.support.AuthApiTest;
import jakarta.servlet.http.Cookie;
import java.time.Duration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

class PasswordResetApiTest extends AuthApiTest {

    private static final String BOB = "bob@example.com";
    private static final String NEW_PASSWORD = "battery-staple-2";

    @Test
    @DisplayName("AUTH-40: 활성·없는·인증 대기 이메일 모두 같은 202 응답을 받고, 메일은 활성 계정에만 간다")
    void sameResponseRegardlessOfAccount() throws Exception {
        createActiveUser(BOB);
        signup("pending@example.com", PASSWORD);
        greenMail.purgeEmailFromAllMailboxes();

        String active = request(BOB).andExpect(status().isAccepted()).andReturn().getResponse().getContentAsString();
        String unknown = request("nobody@example.com").andExpect(status().isAccepted())
                .andReturn().getResponse().getContentAsString();
        String pending = request("pending@example.com").andExpect(status().isAccepted())
                .andReturn().getResponse().getContentAsString();

        assertThat(unknown).isEqualTo(active);
        assertThat(pending).isEqualTo(active);
        assertThat(greenMail.getReceivedMessages()).hasSize(1);
        assertThat(mailBody(mailsTo(BOB).getFirst())).contains("http://localhost:3000/reset-password?token=");
    }

    @Test
    @DisplayName("AUTH-41: 재설정하면 기존 세션이 모두 끝나고, 잠금이 풀리고, 새 비밀번호로만 로그인된다")
    void confirm() throws Exception {
        createActiveUser(BOB);
        Cookie deviceA = loginCookie(BOB, CHROME_WINDOWS);
        Cookie deviceB = loginCookie(BOB, SAFARI_IPHONE);
        for (int i = 0; i < 5; i++) {
            login(BOB, "wrong-password");
        }
        login(BOB, PASSWORD).andExpect(status().isLocked());
        request(BOB);

        confirm(lastMailToken(BOB), NEW_PASSWORD).andExpect(status().isNoContent());

        me(deviceA).andExpect(status().isUnauthorized());
        me(deviceB).andExpect(status().isUnauthorized());
        login(BOB, PASSWORD).andExpect(status().isUnauthorized());
        login(BOB, NEW_PASSWORD).andExpect(status().isOk());
    }

    @Test
    @DisplayName("AUTH-42: 같은 토큰은 다시 쓸 수 없다")
    void tokenReuse() throws Exception {
        createActiveUser(BOB);
        request(BOB);
        String token = lastMailToken(BOB);
        confirm(token, NEW_PASSWORD).andExpect(status().isNoContent());

        confirm(token, "another-password")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_TOKEN"));
    }

    @Test
    @DisplayName("AUTH-42b: 30분이 지난 토큰은 쓸 수 없다")
    void tokenExpired() throws Exception {
        createActiveUser(BOB);
        request(BOB);
        String token = lastMailToken(BOB);
        clock.advance(Duration.ofMinutes(30).plusSeconds(1));

        confirm(token, NEW_PASSWORD)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_TOKEN"));
        login(BOB, PASSWORD).andExpect(status().isOk());
    }

    @Test
    @DisplayName("AUTH-42c: 새 비밀번호도 길이 규칙(8~64자)을 따른다")
    void newPasswordValidated() throws Exception {
        createActiveUser(BOB);
        request(BOB);

        confirm(lastMailToken(BOB), "1234567")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_INPUT"))
                .andExpect(jsonPath("$.errors[0].field").value("newPassword"));
    }

    @Test
    @DisplayName("AUTH-43: 비밀번호를 재설정해도 2단계 인증은 유지된다")
    void twoFactorKept() throws Exception {
        createActiveUser(BOB);
        enableTwoFactor(loginCookie(BOB));
        request(BOB);

        confirm(lastMailToken(BOB), NEW_PASSWORD).andExpect(status().isNoContent());

        login(BOB, NEW_PASSWORD).andExpect(jsonPath("$.mfaRequired").value(true));
    }

    private ResultActions request(String email) throws Exception {
        return mockMvc.perform(post("/api/v1/auth/password-reset").with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(json("email", email)));
    }

    private ResultActions confirm(String token, String newPassword) throws Exception {
        return mockMvc.perform(post("/api/v1/auth/password-reset/confirm").with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(json("token", token, "newPassword", newPassword)));
    }
}
