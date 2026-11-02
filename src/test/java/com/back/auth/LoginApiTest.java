package com.back.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.back.support.AuthApiTest;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockCookie;
import org.springframework.test.web.servlet.MvcResult;

class LoginApiTest extends AuthApiTest {

    private static final String BOB = "bob@example.com";

    @Test
    @DisplayName("AUTH-10: 로그인하면 사용자 요약과 HttpOnly·Secure·SameSite=Lax 세션 쿠키를 받고, 그 쿠키로 인증된다")
    void login() throws Exception {
        long bobId = createActiveUser(BOB);

        MvcResult result = login(BOB, PASSWORD)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.mfaRequired").value(false))
                .andExpect(jsonPath("$.user.id").value(bobId))
                .andExpect(jsonPath("$.user.email").value(BOB))
                .andReturn();

        MockCookie cookie = (MockCookie) sessionCookie(result);
        assertThat(cookie.isHttpOnly()).isTrue();
        assertThat(cookie.getSecure()).isTrue();
        assertThat(cookie.getSameSite()).isEqualToIgnoringCase("Lax");

        me(cookie).andExpect(status().isOk()).andExpect(jsonPath("$.id").value(bobId));
    }

    @Test
    @DisplayName("AUTH-10b: 이미 세션이 있는 상태로 로그인하면 세션 ID가 바뀌고, 이전 ID로는 인증되지 않는다 (세션 고정 방지)")
    void sessionIdRenewedOnLogin() throws Exception {
        createActiveUser(BOB);
        Cookie first = loginCookie(BOB);

        Cookie second = sessionCookie(login(BOB, PASSWORD, CHROME_WINDOWS, first).andExpect(status().isOk()).andReturn());

        assertThat(sessionId(second)).isNotEqualTo(sessionId(first));
        me(first).andExpect(status().isUnauthorized());
        me(second).andExpect(status().isOk());
    }

    @Test
    @DisplayName("AUTH-11: 틀린 비밀번호와 없는 이메일은 같은 401 응답을 받는다")
    void sameResponseForWrongPasswordAndUnknownEmail() throws Exception {
        createActiveUser(BOB);

        String wrongPassword = login(BOB, "wrong-password")
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"))
                .andReturn().getResponse().getContentAsString();
        String unknownEmail = login("nobody@example.com", PASSWORD)
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"))
                .andReturn().getResponse().getContentAsString();

        assertThat(unknownEmail).isEqualTo(wrongPassword.replace(BOB, "nobody@example.com"));
    }

    @Test
    @DisplayName("이메일은 대소문자와 앞뒤 공백을 무시한다")
    void emailNormalized() throws Exception {
        createActiveUser(BOB);

        login("  BOB@Example.com ", PASSWORD).andExpect(status().isOk());
    }

    @Test
    @DisplayName("AUTH-15: 세션 없이 보호된 API를 호출하면 401 UNAUTHENTICATED")
    void unauthenticated() throws Exception {
        mockMvc.perform(get("/api/v1/users/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
    }

    @Test
    @DisplayName("AUTH-16: 탈퇴한 사용자는 로그인할 수 없다 (401)")
    void withdrawnUserCannotLogin() throws Exception {
        long id = createActiveUser(BOB);
        jdbcTemplate.update("UPDATE users SET deleted_at = now() WHERE id = ?", id);

        login(BOB, PASSWORD)
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"));
    }

    @Test
    @DisplayName("AUTH-17: 로그인 상태라도 CSRF 토큰 없이 상태를 바꾸면 403 CSRF_INVALID")
    void csrfRequired() throws Exception {
        createActiveUser(BOB);
        Cookie session = loginCookie(BOB);

        mockMvc.perform(put("/api/v1/users/me").cookie(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json("name", "새 이름")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("CSRF_INVALID"));
    }

    @Test
    @DisplayName("AUTH-17b: CSRF 쿠키(HttpOnly 아님)를 받아 그 값을 헤더로 보내면 상태 변경 요청이 통과한다")
    void csrfCookieFlow() throws Exception {
        createActiveUser(BOB);
        Cookie session = loginCookie(BOB);
        useRealCsrfRepository();

        MvcResult csrfResult = mockMvc.perform(get("/api/v1/auth/csrf"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.headerName").value("X-XSRF-TOKEN"))
                .andExpect(jsonPath("$.cookieName").value("XSRF-TOKEN"))
                .andReturn();
        Cookie csrfCookie = csrfResult.getResponse().getCookie("XSRF-TOKEN");
        assertThat(csrfCookie).isNotNull();
        assertThat(csrfCookie.isHttpOnly()).isFalse();

        mockMvc.perform(put("/api/v1/users/me").cookie(session, csrfCookie)
                        .header("X-XSRF-TOKEN", csrfCookie.getValue())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json("name", "새 이름")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("새 이름"));
    }

    @Test
    @DisplayName("로그아웃 요청에도 CSRF 토큰이 필요하다")
    void logoutRequiresCsrf() throws Exception {
        createActiveUser(BOB);
        Cookie session = loginCookie(BOB);

        mockMvc.perform(post("/api/v1/auth/logout").cookie(session)).andExpect(status().isForbidden());
        mockMvc.perform(post("/api/v1/auth/logout").with(csrf()).cookie(session)).andExpect(status().isNoContent());
    }
}
