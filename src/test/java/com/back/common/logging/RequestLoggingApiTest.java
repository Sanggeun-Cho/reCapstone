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
import org.springframework.http.MediaType;

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

    @Test
    @DisplayName("NFR-07: 비밀번호와 토큰은 로그에 남지 않는다")
    void secretsNotLogged(CapturedOutput output) throws Exception {
        mockMvc.perform(post("/api/v1/test-probe/validation?access_token=query-secret")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer header-secret")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"alice\",\"password\":\"body-secret\"}"))
                .andExpect(status().isUnauthorized());
        log.info("payload {\"name\":\"alice\",\"password\":\"logged-secret\"}");

        assertThat(output).doesNotContain("query-secret", "header-secret", "body-secret", "logged-secret");
    }
}
