package com.back.common.error;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.back.support.ApiTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

class ErrorResponseApiTest extends ApiTest {

    private static final String PROBE = "/api/v1/test-probe";

    @Test
    @DisplayName("인증 없이 보호된 경로를 호출하면 401 ProblemDetail")
    void unauthenticated() throws Exception {
        mockMvc.perform(get(PROBE + "/business-error"))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
    }

    @Test
    @DisplayName("존재하지 않는 경로도 인증 없이는 401 (기본 거부)")
    void unknownPathWithoutAuthentication() throws Exception {
        mockMvc.perform(get("/api/v1/no-such-resource"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
    }

    @Test
    @DisplayName("권한이 없으면 403 ProblemDetail")
    void accessDenied() throws Exception {
        mockMvc.perform(get(PROBE + "/denied").with(user("alice")))
                .andExpect(status().isForbidden())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
    }

    @Test
    @DisplayName("도메인 예외는 오류 코드의 상태와 메시지(detail)로 응답한다")
    void businessException() throws Exception {
        mockMvc.perform(get(PROBE + "/business-error").with(user("alice")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CONFLICT"))
                .andExpect(jsonPath("$.detail").value("이미 진행 중인 인계가 있습니다."));
    }

    @Test
    @DisplayName("입력 검증 실패는 400과 필드별 오류")
    void validationError() throws Exception {
        mockMvc.perform(post(PROBE + "/validation").with(user("alice"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_INPUT"))
                .andExpect(jsonPath("$.errors[0].field").value("name"));
    }

    @Test
    @DisplayName("읽을 수 없는 요청 본문은 400 INVALID_INPUT")
    void malformedBody() throws Exception {
        mockMvc.perform(post(PROBE + "/validation").with(user("alice"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{not json"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_INPUT"));
    }

    @Test
    @DisplayName("인증된 사용자가 없는 경로를 호출하면 404 NOT_FOUND")
    void notFound() throws Exception {
        mockMvc.perform(get("/api/v1/no-such-resource").with(user("alice")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));
    }

    @Test
    @DisplayName("지원하지 않는 메서드는 405 METHOD_NOT_ALLOWED")
    void methodNotAllowed() throws Exception {
        mockMvc.perform(post(PROBE + "/denied").with(user("alice")))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(jsonPath("$.code").value("METHOD_NOT_ALLOWED"));
    }

    @Test
    @DisplayName("예상하지 못한 예외는 500 INTERNAL_ERROR이며 내부 메시지를 노출하지 않는다")
    void unexpectedException() throws Exception {
        mockMvc.perform(get(PROBE + "/unexpected").with(user("alice")))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value("INTERNAL_ERROR"))
                .andExpect(jsonPath("$.detail").value(ErrorCode.INTERNAL_ERROR.getMessage()));
    }
}
