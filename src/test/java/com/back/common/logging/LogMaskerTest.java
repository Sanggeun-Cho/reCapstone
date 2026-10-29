package com.back.common.logging;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class LogMaskerTest {

    @Test
    @DisplayName("JSON 본문의 비밀번호·토큰 값을 가린다")
    void masksJsonFields() {
        String masked = LogMasker.mask(
                "{\"username\":\"alice\",\"password\":\"p@ss w0rd!\",\"newPassword\":\"next1\",\"refreshToken\":\"abc.def\"}");

        assertThat(masked)
                .contains("\"username\":\"alice\"")
                .doesNotContain("p@ss w0rd!", "w0rd!", "next1", "abc.def")
                .contains("\"password\":\"****\"", "\"newPassword\":\"****\"", "\"refreshToken\":\"****\"");
    }

    @ParameterizedTest
    @ValueSource(strings = {"p@ss w0rd!", "pass,word", "pass}word", "pass&word", "pa\\\"ss\\\"word", "a b,c}d&e\\\"f"})
    @DisplayName("JSON 문자열 값은 공백·쉼표·중괄호·&·이스케이프된 따옴표가 있어도 닫는 따옴표까지 전부 가린다")
    void masksWholeJsonStringValue(String secret) {
        String masked = LogMasker.mask("{\"password\":\"" + secret + "\",\"name\":\"alice\"}");

        assertThat(masked)
                .isEqualTo("{\"password\":\"****\",\"name\":\"alice\"}")
                .doesNotContain(secret);
    }

    @Test
    @DisplayName("key=value 형식과 Bearer 토큰을 가린다")
    void masksKeyValueAndBearer() {
        String masked = LogMasker.mask("login password=hunter2 access_token=xyz Authorization: Bearer eyJhbGciOi.payload.sig");

        assertThat(masked)
                .doesNotContain("hunter2", "xyz", "eyJhbGciOi.payload.sig")
                .contains("password=****", "access_token=****", "Bearer ****");
    }

    @Test
    @DisplayName("key=value 형식은 다음 구분자(공백·&·쉼표) 앞까지 가리고 뒤의 내용은 남긴다")
    void masksKeyValueUntilDelimiter() {
        String masked = LogMasker.mask("token=abc123&page=2, secret=s3cr3t next");

        assertThat(masked)
                .doesNotContain("abc123", "s3cr3t")
                .isEqualTo("token=****&page=2, secret=**** next");
    }

    @Test
    @DisplayName("민감 정보가 없는 메시지는 그대로 둔다")
    void keepsOrdinaryMessage() {
        assertThat(LogMasker.mask("GET /api/v1/groups -> 200 (3ms)")).isEqualTo("GET /api/v1/groups -> 200 (3ms)");
    }
}
