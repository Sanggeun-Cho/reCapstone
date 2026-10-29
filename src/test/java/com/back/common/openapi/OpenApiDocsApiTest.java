package com.back.common.openapi;

import static org.hamcrest.Matchers.hasItems;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.back.support.ApiTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class OpenApiDocsApiTest extends ApiTest {

    @Test
    @DisplayName("NFR-05: /v3/api-docs는 인증 없이 열리고 ProblemDetail 오류 스키마와 오류 코드 enum을 노출한다")
    void errorSchemaExposed() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.components.schemas.ProblemDetail.properties.code.enum",
                        hasItems("INVALID_INPUT", "UNAUTHENTICATED", "ACCESS_DENIED", "NOT_FOUND")))
                .andExpect(jsonPath("$.components.schemas.ProblemDetail.properties.errors.items.$ref")
                        .value("#/components/schemas/FieldError"));
    }

    @Test
    @DisplayName("NFR-05: 엔드포인트마다 공통 오류 응답(400·401·403·500)이 ProblemDetail로 선언된다")
    void commonErrorResponsesAttached() throws Exception {
        String responses = "$.paths['/api/v1/test-probe/validation'].post.responses";
        String schemaRef = "content['application/problem+json'].schema.$ref";

        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath(responses + "['400']." + schemaRef).value("#/components/schemas/ProblemDetail"))
                .andExpect(jsonPath(responses + "['401']." + schemaRef).value("#/components/schemas/ProblemDetail"))
                .andExpect(jsonPath(responses + "['403']." + schemaRef).value("#/components/schemas/ProblemDetail"))
                .andExpect(jsonPath(responses + "['500']." + schemaRef).value("#/components/schemas/ProblemDetail"));
    }
}
