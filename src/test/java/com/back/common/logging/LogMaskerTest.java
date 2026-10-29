package com.back.common.logging;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class LogMaskerTest {

    @Test
    @DisplayName("JSON 본문의 비밀번호·토큰 값을 가린다")
    void masksJsonFields() {
        String masked = LogMasker.mask(
                "{\"username\":\"alice\",\"password\":\"p@ss w0rd!\",\"newPassword\":\"next1\",\"refreshToken\":\"abc.def\"}");

        assertThat(masked)
                .contains("\"username\":\"alice\"")
                .doesNotContain("p@ss", "next1", "abc.def")
                .contains("\"password\":\"****");
    }

    @Test
    @DisplayName("key=value 형식과 Bearer 토큰을 가린다")
    void masksKeyValueAndBearer() {
        String masked = LogMasker.mask("login password=hunter2 access_token=xyz Authorization: Bearer eyJhbGciOi.payload.sig");

        assertThat(masked)
                .doesNotContain("hunter2", "xyz", "eyJhbGciOi")
                .contains("password=****", "Bearer ****");
    }

    @Test
    @DisplayName("민감 정보가 없는 메시지는 그대로 둔다")
    void keepsOrdinaryMessage() {
        assertThat(LogMasker.mask("GET /api/v1/groups -> 200 (3ms)")).isEqualTo("GET /api/v1/groups -> 200 (3ms)");
    }
}
