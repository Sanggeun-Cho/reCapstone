package com.back.common.logging;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.back.support.ApiTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.http.HttpHeaders;

@ExtendWith(OutputCaptureExtension.class)
class RequestLoggingApiTest extends ApiTest {

    private static final Logger log = LoggerFactory.getLogger(RequestLoggingApiTest.class);

    @Test
    @DisplayName("NFR-07: 로그에 요청 ID가 남고 응답 헤더로 돌려준다")
    void requestIdInLogAndHeader(CapturedOutput output) throws Exception {
        mockMvc.perform(get("/api/v1/test-probe/business-error").header(RequestIdFilter.HEADER, "req-nfr07"))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string(RequestIdFilter.HEADER, "req-nfr07"));

        assertThat(output).contains("[req-nfr07]").contains("GET /api/v1/test-probe/business-error -> 401");
    }

    @Test
    @DisplayName("NFR-07: 요청 ID 헤더가 없거나 형식이 틀리면 새로 만든다")
    void requestIdGenerated() throws Exception {
        mockMvc.perform(get("/api/v1/test-probe/business-error").header(RequestIdFilter.HEADER, "bad id\r\ninjected"))
                .andExpect(header().string(RequestIdFilter.HEADER, org.hamcrest.Matchers.matchesPattern("[0-9a-f-]{36}")));
    }

    // 요청 본문은 어디에서도 로그로 남기지 않으므로 검증 대상이 아니다.
    // 본문 형식의 값이 로그 메시지에 섞인 경우는 마스킹 변환어로 검증한다.
    @Test
    @DisplayName("NFR-07: 접근 로그에 쿼리 문자열의 토큰과 Authorization 헤더가 남지 않는다")
    void requestSecretsNotLogged(CapturedOutput output) throws Exception {
        mockMvc.perform(post("/api/v1/test-probe/validation?access_token=query-secret")
                        .header(RequestIdFilter.HEADER, "req-secrets")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer header-secret"))
                .andExpect(status().isUnauthorized());

        assertThat(output)
                .contains("[req-secrets]", "POST /api/v1/test-probe/validation -> 401")
                .doesNotContain("query-secret", "header-secret");
    }

    @Test
    @DisplayName("NFR-07: 로그 메시지에 섞인 비밀번호는 콘솔 출력에서 가려진다")
    void passwordInLogMessageMasked(CapturedOutput output) {
        log.info("payload {\"name\":\"alice\",\"password\":\"logged secret, with}punctuation\"}");

        assertThat(output)
                .contains("payload {\"name\":\"alice\",\"password\":\"****\"}")
                .doesNotContain("logged secret", "punctuation");
    }
}
