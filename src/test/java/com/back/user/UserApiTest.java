package com.back.user;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.back.support.AuthApiTest;
import com.jayway.jsonpath.JsonPath;
import jakarta.servlet.http.Cookie;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

class UserApiTest extends AuthApiTest {

    private static final String BOB = "bob@example.com";
    private static final String NEW_PASSWORD = "battery-staple-2";

    private long bobId;
    private Cookie session;

    @BeforeEach
    void setUp() throws Exception {
        bobId = createActiveUser(BOB);
        session = loginCookie(BOB);
    }

    @Test
    @DisplayName("USER-01: 내 정보에는 비밀번호와 TOTP 비밀키가 없다")
    void getMe() throws Exception {
        String body = me(session)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(bobId))
                .andExpect(jsonPath("$.email").value(BOB))
                .andExpect(jsonPath("$.name").exists())
                .andExpect(jsonPath("$.twoFactorEnabled").value(false))
                .andExpect(jsonPath("$.createdAt").exists())
                .andReturn().getResponse().getContentAsString();

        assertThat(body).doesNotContainIgnoringCase("password").doesNotContainIgnoringCase("totp");
    }

    @Test
    @DisplayName("USER-02: 이름과 업무용 전화번호를 바꾸고, 전화번호는 null로 지울 수 있다. 이메일은 바뀌지 않는다")
    void updateMe() throws Exception {
        update(json("name", "새 이름", "workPhone", "010-1234-5678", "email", "changed@example.com"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("새 이름"))
                .andExpect(jsonPath("$.workPhone").value("010-1234-5678"))
                .andExpect(jsonPath("$.email").value(BOB));

        update(json("name", "새 이름", "workPhone", null))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.workPhone").doesNotExist());
    }

    @Test
    @DisplayName("USER-03: 비밀번호를 바꾸면 현재 기기는 유지되고 다른 기기의 세션은 끝난다")
    void changePassword() throws Exception {
        Cookie otherDevice = loginCookie(BOB, SAFARI_IPHONE);

        changePassword(PASSWORD, NEW_PASSWORD).andExpect(status().isNoContent());

        me(session).andExpect(status().isOk());
        me(otherDevice).andExpect(status().isUnauthorized());
        login(BOB, NEW_PASSWORD).andExpect(status().isOk());
        login(BOB, PASSWORD).andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("USER-04: 현재 비밀번호가 틀리면 400이고 잠금 실패 횟수에 들어간다 (5회째 423)")
    void wrongCurrentPassword() throws Exception {
        for (int i = 0; i < 4; i++) {
            changePassword("wrong-password", NEW_PASSWORD)
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("CURRENT_PASSWORD_MISMATCH"));
        }
        changePassword("wrong-password", NEW_PASSWORD)
                .andExpect(status().isLocked())
                .andExpect(jsonPath("$.code").value("ACCOUNT_LOCKED"));
    }

    @Test
    @DisplayName("USER-06: 사용자 목록·검색 API는 없다 (/api/v1/users는 가입 POST만)")
    void noUserSearchApi() throws Exception {
        String docs = mockMvc.perform(get("/v3/api-docs")).andReturn().getResponse().getContentAsString();

        Map<String, Object> usersPath = JsonPath.read(docs, "$.paths['/api/v1/users']");
        assertThat(usersPath).containsOnlyKeys("post");
        mockMvc.perform(get("/api/v1/users").cookie(session)).andExpect(status().isMethodNotAllowed());
    }

    private ResultActions update(String body) throws Exception {
        return mockMvc.perform(put("/api/v1/users/me").with(csrf()).cookie(session)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body));
    }

    private ResultActions changePassword(String current, String next) throws Exception {
        return mockMvc.perform(put("/api/v1/users/me/password").with(csrf()).cookie(session)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json("currentPassword", current, "newPassword", next)));
    }
}
