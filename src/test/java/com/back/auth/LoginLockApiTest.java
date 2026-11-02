package com.back.auth;

import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.back.support.AuthApiTest;
import java.time.Duration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class LoginLockApiTest extends AuthApiTest {

    private static final String BOB = "bob@example.com";
    private static final String WRONG = "wrong-password";

    @BeforeEach
    void setUp() {
        createActiveUser(BOB);
    }

    @Test
    @DisplayName("AUTH-12: 연속 5회째 실패하면 423 ACCOUNT_LOCKED(unlockAt 포함)이고, 잠긴 동안은 올바른 비밀번호도 423")
    void lockAfterFiveFailures() throws Exception {
        for (int i = 0; i < 4; i++) {
            login(BOB, WRONG).andExpect(status().isUnauthorized());
        }

        login(BOB, WRONG)
                .andExpect(status().isLocked())
                .andExpect(jsonPath("$.code").value("ACCOUNT_LOCKED"))
                .andExpect(jsonPath("$.unlockAt").value(clock.instant().plus(Duration.ofMinutes(15)).toString()));
        login(BOB, PASSWORD)
                .andExpect(status().isLocked())
                .andExpect(jsonPath("$.code").value("ACCOUNT_LOCKED"));
    }

    @Test
    @DisplayName("AUTH-13: 15분이 지나면 잠금이 풀리고, 로그인에 성공하면 실패 횟수가 초기화된다")
    void unlockAfterLockDuration() throws Exception {
        for (int i = 0; i < 5; i++) {
            login(BOB, WRONG);
        }
        clock.advance(Duration.ofMinutes(14).plusSeconds(59));
        login(BOB, PASSWORD).andExpect(status().isLocked());

        clock.advance(Duration.ofSeconds(2));
        login(BOB, PASSWORD).andExpect(status().isOk());
        login(BOB, WRONG).andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("AUTH-14: 성공하면 횟수가 초기화되므로 연속 실패만 센다")
    void onlyConsecutiveFailuresCount() throws Exception {
        for (int i = 0; i < 4; i++) {
            login(BOB, WRONG).andExpect(status().isUnauthorized());
        }
        login(BOB, PASSWORD).andExpect(status().isOk());

        for (int i = 0; i < 4; i++) {
            login(BOB, WRONG).andExpect(status().isUnauthorized());
        }
        login(BOB, PASSWORD).andExpect(status().isOk());
    }

    @Test
    @DisplayName("AUTH-14b: 없는 이메일은 몇 번 실패해도 401이다 (존재하지 않는 계정은 잠그지 않음)")
    void unknownEmailNeverLocks() throws Exception {
        for (int i = 0; i < 6; i++) {
            login("nobody@example.com", WRONG)
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"));
        }
    }
}
