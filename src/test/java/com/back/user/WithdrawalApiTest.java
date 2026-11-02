package com.back.user;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.back.support.AuthApiTest;
import com.back.user.application.UserEvents;
import jakarta.servlet.http.Cookie;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.context.event.ApplicationEvents;
import org.springframework.test.context.event.RecordApplicationEvents;
import org.springframework.test.web.servlet.ResultActions;

@RecordApplicationEvents
class WithdrawalApiTest extends AuthApiTest {

    private static final String DAVE = "dave@example.com";

    @Autowired
    ApplicationEvents events;

    private long daveId;
    private Cookie deviceA;

    @BeforeEach
    void setUp() throws Exception {
        daveId = createActiveUser(DAVE);
        deviceA = loginCookie(DAVE, CHROME_WINDOWS);
    }

    @Test
    @DisplayName("USER-07: 비밀번호로 재확인하고 탈퇴하면 모든 세션이 끝나고, 이름·이메일은 DB에 남는다")
    void withdraw() throws Exception {
        Cookie deviceB = loginCookie(DAVE, SAFARI_IPHONE);

        withdraw(deviceA, json("password", PASSWORD)).andExpect(status().isNoContent());

        me(deviceA).andExpect(status().isUnauthorized());
        me(deviceB).andExpect(status().isUnauthorized());
        login(DAVE, PASSWORD).andExpect(status().isUnauthorized());

        Map<String, Object> row = jdbcTemplate.queryForMap(
                "SELECT email, name, deleted_at FROM users WHERE id = ?", daveId);
        assertThat(row.get("email")).isEqualTo(DAVE);
        assertThat(row.get("name")).isNotNull();
        assertThat(row.get("deleted_at")).isNotNull();
        assertThat(events.stream(UserEvents.Withdrawn.class)).containsExactly(new UserEvents.Withdrawn(daveId));
    }

    @Test
    @DisplayName("USER-07b: 2단계 인증을 켠 사용자는 일회용 코드로 재확인하고 탈퇴한다")
    void withdrawWithTotp() throws Exception {
        TwoFactor twoFactor = enableTwoFactor(deviceA);

        withdraw(deviceA, json("password", PASSWORD))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("REAUTH_FAILED"));
        withdraw(deviceA, json("code", nextCode(twoFactor.secret()))).andExpect(status().isNoContent());
    }

    @Test
    @DisplayName("USER-09: 탈퇴한 이메일로 다시 가입하면 새 계정(새 id)이 되고 이전 행은 그대로 남는다")
    void signupAgainAfterWithdrawal() throws Exception {
        withdraw(deviceA, json("password", PASSWORD)).andExpect(status().isNoContent());

        signup(DAVE, PASSWORD)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(org.hamcrest.Matchers.not((int) daveId)));
        mockMvc.perform(post("/api/v1/auth/email-verification").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json("token", lastMailToken(DAVE))))
                .andExpect(status().isOk());
        login(DAVE, PASSWORD).andExpect(status().isOk());

        assertThat(jdbcTemplate.queryForObject(
                "SELECT count(*) FROM users WHERE email = ?", Integer.class, DAVE)).isEqualTo(2);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT deleted_at IS NOT NULL FROM users WHERE id = ?", Boolean.class, daveId)).isTrue();
    }

    @Test
    @DisplayName("USER-10: 재확인 값이 없거나 틀리면 400 REAUTH_FAILED이고 탈퇴되지 않는다")
    void reauthRequired() throws Exception {
        withdraw(deviceA, "{}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("REAUTH_FAILED"));
        withdraw(deviceA, json("password", "wrong-password"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("REAUTH_FAILED"));

        me(deviceA).andExpect(status().isOk());
        assertThat(events.stream(UserEvents.Withdrawn.class)).isEmpty();
    }

    @Test
    @DisplayName("USER-10b: 탈퇴 차단 조건(WithdrawalGuard)에 걸리면 409 WITHDRAWAL_BLOCKED이고 세션도 유지된다")
    void blockedByGuard() throws Exception {
        withdrawalGuard.block();

        withdraw(deviceA, json("password", PASSWORD))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("WITHDRAWAL_BLOCKED"));

        me(deviceA).andExpect(status().isOk());
        assertThat(jdbcTemplate.queryForObject(
                "SELECT deleted_at IS NULL FROM users WHERE id = ?", Boolean.class, daveId)).isTrue();
    }

    private ResultActions withdraw(Cookie session, String body) throws Exception {
        return mockMvc.perform(post("/api/v1/users/me/withdrawal").with(csrf()).cookie(session)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body));
    }
}
