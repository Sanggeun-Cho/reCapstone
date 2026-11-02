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

    @Test
    @DisplayName("S1: 회원·인증 엔드포인트가 노출되고, CSRF 토큰 인자는 요청 파라미터로 드러나지 않는다")
    void authEndpointsExposed() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paths['/api/v1/auth/login'].post").exists())
                .andExpect(jsonPath("$.paths['/api/v1/auth/login/mfa'].post").exists())
                .andExpect(jsonPath("$.paths['/api/v1/auth/sessions'].get").exists())
                .andExpect(jsonPath("$.paths['/api/v1/users/me'].get").exists())
                .andExpect(jsonPath("$.paths['/api/v1/users/me/2fa/setup'].post").exists())
                .andExpect(jsonPath("$.paths['/api/v1/auth/csrf'].get.parameters").doesNotExist())
                .andExpect(jsonPath("$.components.schemas.ProblemDetail.properties.code.enum",
                        hasItems("ACCOUNT_LOCKED", "EMAIL_NOT_VERIFIED", "REAUTH_FAILED", "CSRF_INVALID")));
    }

    @Test
    @DisplayName("S2: 그룹·초대·스페이스·삭제 요청 엔드포인트와 새 오류 코드가 노출된다")
    void groupEndpointsExposed() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paths['/api/v1/groups'].post").exists())
                .andExpect(jsonPath("$.paths['/api/v1/groups/{groupId}/owner-transfer'].post").exists())
                .andExpect(jsonPath("$.paths['/api/v1/groups/{groupId}/invitations'].post").exists())
                .andExpect(jsonPath("$.paths['/api/v1/invitations/{invitationId}/accept'].post").exists())
                .andExpect(jsonPath("$.paths['/api/v1/users/me/invitations'].get").exists())
                .andExpect(jsonPath("$.paths['/api/v1/spaces/{spaceId}/forced-reassignment'].post").exists())
                .andExpect(jsonPath("$.paths['/api/v1/spaces/{spaceId}/deletion-request/approve'].post").exists())
                .andExpect(jsonPath("$.components.schemas.ProblemDetail.properties.code.enum",
                        hasItems("NOT_GROUP_MEMBER", "OWNER_MUST_TRANSFER", "HANDOVER_IN_PROGRESS",
                                "INVITATION_NOT_PENDING", "SELF_APPROVAL_NOT_ALLOWED")));
    }
}
