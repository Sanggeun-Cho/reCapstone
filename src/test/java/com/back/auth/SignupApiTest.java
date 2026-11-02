package com.back.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.back.support.AuthApiTest;
import java.time.Duration;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.http.MediaType;

class SignupApiTest extends AuthApiTest {

    @Test
    @DisplayName("AUTH-01: 가입하면 인증 대기 계정이 생기고, 비밀번호는 BCrypt 해시로 저장되며, 인증 메일이 간다")
    void signup() throws Exception {
        mockMvc.perform(post("/api/v1/users").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json("email", "bob@example.com", "password", PASSWORD, "name", "밥",
                                "workPhone", "02-123-4567")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNumber())
                .andExpect(jsonPath("$.email").value("bob@example.com"))
                .andExpect(jsonPath("$.name").value("밥"))
                .andExpect(jsonPath("$.workPhone").value("02-123-4567"))
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.password").doesNotExist());

        String hash = jdbcTemplate.queryForObject(
                "SELECT password_hash FROM users WHERE email = 'bob@example.com'", String.class);
        assertThat(hash).startsWith("{bcrypt}$2").doesNotContain(PASSWORD);

        assertThat(mailsTo("bob@example.com")).hasSize(1);
        assertThat(mailBody(mailsTo("bob@example.com").getFirst()))
                .contains("http://localhost:3000/verify-email?token=");
    }

    @Test
    @DisplayName("AUTH-01b: 업무용 전화번호 없이도 가입할 수 있다")
    void signupWithoutPhone() throws Exception {
        signup("bob@example.com", PASSWORD)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.workPhone").doesNotExist());
    }

    @Test
    @DisplayName("AUTH-02: 활성 계정의 이메일로 가입하면 409 EMAIL_DUPLICATED (대소문자 무시), 메일은 가지 않는다")
    void duplicatedEmail() throws Exception {
        createActiveUser("e1@example.com");

        signup("E1@Example.COM", PASSWORD)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("EMAIL_DUPLICATED"));
        assertThat(greenMail.getReceivedMessages()).isEmpty();
    }

    static Stream<Arguments> invalidSignups() {
        return Stream.of(
                Arguments.of("이메일 형식", json("email", "not-an-email", "password", PASSWORD, "name", "n"), "email"),
                Arguments.of("비밀번호 7자", json("email", "a@example.com", "password", "1234567", "name", "n"), "password"),
                Arguments.of("비밀번호 65자", json("email", "a@example.com", "password", "p".repeat(65), "name", "n"), "password"),
                Arguments.of("이름 공백", json("email", "a@example.com", "password", PASSWORD, "name", "  "), "name"),
                Arguments.of("이름 51자", json("email", "a@example.com", "password", PASSWORD, "name", "가".repeat(51)), "name"),
                Arguments.of("전화번호에 문자", json("email", "a@example.com", "password", PASSWORD, "name", "n",
                        "workPhone", "010-abcd"), "workPhone"));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("invalidSignups")
    @DisplayName("AUTH-03: 입력 검증에 실패하면 400 INVALID_INPUT과 필드 오류")
    void invalidInput(String caseName, String body, String field) throws Exception {
        mockMvc.perform(post("/api/v1/users").with(csrf()).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_INPUT"))
                .andExpect(jsonPath("$.errors[0].field").value(field));
    }

    @Test
    @DisplayName("비밀번호 8자와 64자는 허용한다 (경계값)")
    void passwordBoundaries() throws Exception {
        signup("min@example.com", "12345678").andExpect(status().isCreated());
        signup("max@example.com", "p".repeat(64)).andExpect(status().isCreated());
    }

    @Test
    @DisplayName("AUTH-04: 인증 대기 중(24시간 이내)인 이메일로 다시 가입하면 409 EMAIL_PENDING_VERIFICATION")
    void pendingEmail() throws Exception {
        signup("e2@example.com", PASSWORD).andExpect(status().isCreated());
        clock.advance(Duration.ofHours(23));

        signup("e2@example.com", PASSWORD)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("EMAIL_PENDING_VERIFICATION"));
    }

    @Test
    @DisplayName("AUTH-05: 인증 기한(24시간)이 지난 대기 계정의 이메일로는 다시 가입할 수 있고, 이전 대기 계정은 soft delete된다")
    void expiredPendingEmail() throws Exception {
        signup("e2@example.com", PASSWORD).andExpect(status().isCreated());
        Long oldId = jdbcTemplate.queryForObject("SELECT id FROM users WHERE email = 'e2@example.com'", Long.class);
        clock.advance(Duration.ofHours(24).plusMinutes(1));

        signup("e2@example.com", PASSWORD)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(org.hamcrest.Matchers.not(oldId.intValue())));

        assertThat(jdbcTemplate.queryForObject(
                "SELECT deleted_at IS NOT NULL FROM users WHERE id = ?", Boolean.class, oldId)).isTrue();
    }
}
