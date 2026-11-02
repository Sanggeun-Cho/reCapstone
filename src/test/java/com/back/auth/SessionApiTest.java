package com.back.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
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
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.session.FindByIndexNameSessionRepository;
import org.springframework.session.Session;

class SessionApiTest extends AuthApiTest {

    private static final String BOB = "bob@example.com";
    private static final String CAROL = "carol@example.com";

    @Autowired
    FindByIndexNameSessionRepository<? extends Session> sessionRepository;

    private Cookie deviceA;
    private Cookie deviceB;

    @BeforeEach
    void setUp() throws Exception {
        createActiveUser(BOB);
        createActiveUser(CAROL);
        deviceA = loginCookie(BOB, CHROME_WINDOWS);
        deviceB = loginCookie(BOB, SAFARI_IPHONE);
    }

    @Test
    @DisplayName("AUTH-30: 여러 기기에서 동시에 로그인할 수 있다")
    void multipleDevices() throws Exception {
        me(deviceA).andExpect(status().isOk());
        me(deviceB).andExpect(status().isOk());
    }

    @Test
    @DisplayName("AUTH-31: 세션 목록에는 내 세션만 기기 요약과 함께 나오고, IP와 세션 ID 원문은 없다")
    void listSessions() throws Exception {
        loginCookie(CAROL);

        String body = mockMvc.perform(get("/api/v1/auth/sessions").cookie(deviceA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[*].device", containsInAnyOrder("Chrome / Windows", "Safari / iOS")))
                .andExpect(jsonPath("$[?(@.current == true)].device", containsInAnyOrder("Chrome / Windows")))
                .andExpect(jsonPath("$[0].createdAt").exists())
                .andExpect(jsonPath("$[0].lastUsedAt").exists())
                .andReturn().getResponse().getContentAsString();

        assertThat(body)
                .doesNotContain(sessionId(deviceA), sessionId(deviceB), deviceA.getValue())
                .doesNotContain("\"ip");
    }

    @Test
    @DisplayName("AUTH-32: 다른 기기의 세션을 종료하면 그 기기는 즉시 401이 되고, 현재 기기는 유지된다")
    void revokeOtherDevice() throws Exception {
        String deviceBId = publicIdOf("Safari / iOS");

        mockMvc.perform(delete("/api/v1/auth/sessions/{id}", deviceBId).with(csrf()).cookie(deviceA))
                .andExpect(status().isNoContent());

        me(deviceB).andExpect(status().isUnauthorized());
        me(deviceA).andExpect(status().isOk());
    }

    @Test
    @DisplayName("현재 기기의 세션 id로 종료하면 로그아웃과 같다")
    void revokeCurrentDevice() throws Exception {
        String deviceAId = publicIdOf("Chrome / Windows");

        mockMvc.perform(delete("/api/v1/auth/sessions/{id}", deviceAId).with(csrf()).cookie(deviceA))
                .andExpect(status().isNoContent());

        me(deviceA).andExpect(status().isUnauthorized());
        me(deviceB).andExpect(status().isOk());
    }

    @Test
    @DisplayName("AUTH-33: 다른 사용자의 세션 id로 종료를 요청하면 404이고 그 세션은 유지된다")
    void cannotRevokeOthersSession() throws Exception {
        Cookie carol = loginCookie(CAROL);
        String carolSessionId = JsonPath.read(
                mockMvc.perform(get("/api/v1/auth/sessions").cookie(carol)).andReturn().getResponse().getContentAsString(),
                "$[0].id");

        mockMvc.perform(delete("/api/v1/auth/sessions/{id}", carolSessionId).with(csrf()).cookie(deviceA))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));

        me(carol).andExpect(status().isOk());
    }

    @Test
    @DisplayName("AUTH-34: 현재 기기를 뺀 나머지 세션을 모두 종료한다")
    void revokeOthers() throws Exception {
        Cookie deviceC = loginCookie(BOB, CHROME_WINDOWS);

        mockMvc.perform(delete("/api/v1/auth/sessions").with(csrf()).cookie(deviceA))
                .andExpect(status().isNoContent());

        me(deviceA).andExpect(status().isOk());
        me(deviceB).andExpect(status().isUnauthorized());
        me(deviceC).andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("AUTH-35: 로그아웃하면 같은 쿠키로는 인증되지 않는다")
    void logout() throws Exception {
        mockMvc.perform(post("/api/v1/auth/logout").with(csrf()).cookie(deviceA))
                .andExpect(status().isNoContent());

        me(deviceA).andExpect(status().isUnauthorized());
        assertThat(sessionRepository.findById(sessionId(deviceA))).isNull();
    }

    @Test
    @DisplayName("AUTH-36: 세션의 미사용 만료 시간은 14일이다")
    void idleTimeout() {
        Session session = sessionRepository.findById(sessionId(deviceA));

        assertThat(session.getMaxInactiveInterval()).isEqualTo(Duration.ofDays(14));
    }

    @Test
    @DisplayName("AUTH-37: 계속 쓰더라도 로그인한 지 30일이 지나면 세션이 끝난다")
    void absoluteTimeout() throws Exception {
        clock.advance(Duration.ofDays(29).plusHours(23));
        me(deviceA).andExpect(status().isOk());

        clock.advance(Duration.ofHours(1).plusSeconds(1));
        me(deviceA)
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
        assertThat(sessionRepository.findById(sessionId(deviceA))).isNull();
    }

    private String publicIdOf(String device) throws Exception {
        String body = mockMvc.perform(get("/api/v1/auth/sessions").cookie(deviceA))
                .andReturn().getResponse().getContentAsString();
        List<String> ids = JsonPath.read(body, "$[?(@.device == '" + device + "')].id");
        assertThat(ids).hasSize(1);
        return ids.getFirst();
    }
}
