package com.back.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.everyItem;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.matchesPattern;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.back.support.AuthApiTest;
import com.jayway.jsonpath.JsonPath;
import jakarta.servlet.http.Cookie;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;

class TwoFactorApiTest extends AuthApiTest {

    private static final String BOB = "bob@example.com";

    private Cookie session;

    @BeforeEach
    void setUp() throws Exception {
        createActiveUser(BOB);
        session = loginCookie(BOB);
    }

    @Test
    @DisplayName("AUTH-20: 등록을 시작하면 Base32 비밀키와 등록 URI를 받고, 아직 꺼진 상태다")
    void setup() throws Exception {
        mockMvc.perform(post("/api/v1/users/me/2fa/setup").with(csrf()).cookie(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.secret").value(matchesPattern("[A-Z2-7]{32}")))
                .andExpect(jsonPath("$.otpauthUri").value(startsWith("otpauth://totp/")));

        me(session).andExpect(jsonPath("$.twoFactorEnabled").value(false));
    }

    @Test
    @DisplayName("AUTH-20b: 이미 켜져 있으면 등록을 다시 시작할 수 없다 (409)")
    void setupWhenEnabled() throws Exception {
        enableTwoFactor(session);

        mockMvc.perform(post("/api/v1/users/me/2fa/setup").with(csrf()).cookie(session))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("TWO_FACTOR_ALREADY_ENABLED"));
    }

    @Test
    @DisplayName("AUTH-21: 올바른 코드로 확인하면 켜지고 복구 코드 10개(xxxxx-xxxxx)를 받는다")
    void enable() throws Exception {
        TwoFactor twoFactor = enableTwoFactor(session);

        assertThat(twoFactor.recoveryCodes()).hasSize(10)
                .allMatch(code -> code.matches("[a-z2-9]{5}-[a-z2-9]{5}"))
                .doesNotHaveDuplicates();
        me(session).andExpect(jsonPath("$.twoFactorEnabled").value(true));
    }

    @Test
    @DisplayName("AUTH-21b: 틀린 코드로 확인하면 400 TWO_FACTOR_CODE_MISMATCH이고 꺼진 상태로 남는다")
    void enableWithWrongCode() throws Exception {
        mockMvc.perform(post("/api/v1/users/me/2fa/setup").with(csrf()).cookie(session)).andExpect(status().isOk());

        postCode("/api/v1/users/me/2fa/enable", "000000")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("TWO_FACTOR_CODE_MISMATCH"));
        me(session).andExpect(jsonPath("$.twoFactorEnabled").value(false));
    }

    @Test
    @DisplayName("AUTH-21c: 등록을 시작하지 않고 켜려 하면 409 TWO_FACTOR_SETUP_REQUIRED")
    void enableWithoutSetup() throws Exception {
        postCode("/api/v1/users/me/2fa/enable", "123456")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("TWO_FACTOR_SETUP_REQUIRED"));
    }

    @Test
    @DisplayName("AUTH-22: 2단계 인증을 켠 사용자는 비밀번호만으로는 로그인이 끝나지 않는다")
    void passwordOnlyIsNotEnough() throws Exception {
        enableTwoFactor(session);

        Cookie pending = sessionCookie(login(BOB, PASSWORD)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.mfaRequired").value(true))
                .andExpect(jsonPath("$.user").doesNotExist())
                .andReturn());

        me(pending).andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("AUTH-23: 올바른 코드를 보내면 로그인이 끝나고 세션 ID가 바뀐다")
    void completeWithTotp() throws Exception {
        TwoFactor twoFactor = enableTwoFactor(session);
        Cookie pending = startMfaLogin();

        MvcResult result = mfa(pending, nextCode(twoFactor.secret()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.mfaRequired").value(false))
                .andExpect(jsonPath("$.user.email").value(BOB))
                .andReturn();

        Cookie loggedIn = sessionCookie(result);
        assertThat(sessionId(loggedIn)).isNotEqualTo(sessionId(pending));
        me(loggedIn).andExpect(status().isOk());
    }

    @Test
    @DisplayName("AUTH-24: 틀린 코드는 401 INVALID_MFA_CODE이고 잠금 실패 횟수에 들어간다")
    void wrongCodeCountsAsFailure() throws Exception {
        enableTwoFactor(session);
        Cookie pending = startMfaLogin();

        for (int i = 0; i < 4; i++) {
            mfa(pending, "000000")
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.code").value("INVALID_MFA_CODE"));
        }
        mfa(pending, "000000")
                .andExpect(status().isLocked())
                .andExpect(jsonPath("$.code").value("ACCOUNT_LOCKED"));
    }

    @Test
    @DisplayName("AUTH-24b: 같은 30초 구간에 이미 쓴 코드는 다시 쓸 수 없다")
    void codeReuseRejected() throws Exception {
        TwoFactor twoFactor = enableTwoFactor(session);
        String code = nextCode(twoFactor.secret());
        mfa(startMfaLogin(), code).andExpect(status().isOk());

        mfa(startMfaLogin(), code)
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_MFA_CODE"));
    }

    @Test
    @DisplayName("AUTH-24c: 비밀번호 확인 후 5분이 지나면 401 MFA_SESSION_EXPIRED")
    void mfaPendingExpires() throws Exception {
        TwoFactor twoFactor = enableTwoFactor(session);
        Cookie pending = startMfaLogin();
        clock.advance(Duration.ofMinutes(5).plusSeconds(1));

        mfa(pending, currentCode(twoFactor.secret()))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("MFA_SESSION_EXPIRED"));
    }

    @Test
    @DisplayName("AUTH-24d: 비밀번호 확인 없이 2단계만 보내면 401 MFA_SESSION_EXPIRED")
    void mfaWithoutPassword() throws Exception {
        mockMvc.perform(post("/api/v1/auth/login/mfa").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json("code", "123456")))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("MFA_SESSION_EXPIRED"));
    }

    @Test
    @DisplayName("AUTH-25: 복구 코드로 로그인할 수 있고, 같은 복구 코드는 한 번만 쓸 수 있다")
    void recoveryCodeIsSingleUse() throws Exception {
        TwoFactor twoFactor = enableTwoFactor(session);
        String recoveryCode = twoFactor.recoveryCodes().getFirst();

        mfa(startMfaLogin(), recoveryCode.toUpperCase()).andExpect(status().isOk());
        mfa(startMfaLogin(), recoveryCode)
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_MFA_CODE"));
    }

    @Test
    @DisplayName("AUTH-26: 복구 코드를 다시 발급하면 이전 코드는 모두 무효가 된다")
    void regenerateRecoveryCodes() throws Exception {
        TwoFactor twoFactor = enableTwoFactor(session);

        String response = postCode("/api/v1/users/me/2fa/recovery-codes", nextCode(twoFactor.secret()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.recoveryCodes", hasSize(10)))
                .andExpect(jsonPath("$.recoveryCodes", everyItem(matchesPattern("[a-z2-9]{5}-[a-z2-9]{5}"))))
                .andReturn().getResponse().getContentAsString();
        List<String> newCodes = JsonPath.read(response, "$.recoveryCodes");

        mfa(startMfaLogin(), twoFactor.recoveryCodes().getFirst()).andExpect(status().isUnauthorized());
        mfa(startMfaLogin(), newCodes.getFirst()).andExpect(status().isOk());
    }

    @Test
    @DisplayName("복구 코드 재발급에는 복구 코드가 아니라 인증 앱 코드가 필요하다")
    void regenerateRequiresTotp() throws Exception {
        TwoFactor twoFactor = enableTwoFactor(session);

        postCode("/api/v1/users/me/2fa/recovery-codes", twoFactor.recoveryCodes().getFirst())
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("REAUTH_FAILED"));
    }

    @Test
    @DisplayName("AUTH-27: 비밀번호와 코드로 끄면 다음 로그인은 비밀번호만으로 끝난다")
    void disable() throws Exception {
        TwoFactor twoFactor = enableTwoFactor(session);

        disable(PASSWORD, nextCode(twoFactor.secret())).andExpect(status().isNoContent());

        me(session).andExpect(jsonPath("$.twoFactorEnabled").value(false));
        login(BOB, PASSWORD).andExpect(jsonPath("$.mfaRequired").value(false));
        assertThat(jdbcTemplate.queryForObject(
                "SELECT count(*) FROM recovery_code WHERE deleted_at IS NULL", Integer.class)).isZero();
    }

    @Test
    @DisplayName("AUTH-27b: 비밀번호나 코드가 틀리면 400 REAUTH_FAILED이고 켜진 상태로 남는다")
    void disableWithWrongCredentials() throws Exception {
        TwoFactor twoFactor = enableTwoFactor(session);

        disable("wrong-password", nextCode(twoFactor.secret()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("REAUTH_FAILED"));
        disable(PASSWORD, "000000")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("REAUTH_FAILED"));

        me(session).andExpect(jsonPath("$.twoFactorEnabled").value(true));
    }

    @Test
    @DisplayName("AUTH-27c: 꺼진 상태에서 끄려 하면 409 TWO_FACTOR_NOT_ENABLED")
    void disableWhenNotEnabled() throws Exception {
        disable(PASSWORD, "123456")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("TWO_FACTOR_NOT_ENABLED"));
    }

    private Cookie startMfaLogin() throws Exception {
        return sessionCookie(login(BOB, PASSWORD)
                .andExpect(jsonPath("$.mfaRequired").value(true))
                .andReturn());
    }

    private ResultActions mfa(Cookie pending, String code) throws Exception {
        return mockMvc.perform(post("/api/v1/auth/login/mfa").with(csrf()).cookie(pending)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json("code", code)));
    }

    private ResultActions postCode(String path, String code) throws Exception {
        return mockMvc.perform(post(path).with(csrf()).cookie(session)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json("code", code)));
    }

    private ResultActions disable(String password, String code) throws Exception {
        return mockMvc.perform(post("/api/v1/users/me/2fa/disable").with(csrf()).cookie(session)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json("password", password, "code", code)));
    }
}
