package com.back.common.openapi;

import com.back.common.error.ErrorCode;
import com.back.common.error.GlobalExceptionHandler;
import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.media.ArraySchema;
import io.swagger.v3.oas.models.media.Content;
import io.swagger.v3.oas.models.media.IntegerSchema;
import io.swagger.v3.oas.models.media.MediaType;
import io.swagger.v3.oas.models.media.ObjectSchema;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.media.StringSchema;
import io.swagger.v3.oas.models.responses.ApiResponse;
import io.swagger.v3.oas.models.responses.ApiResponses;
import java.util.Arrays;
import java.util.List;
import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * OpenAPI 문서에 {@code ProblemDetail} 오류 스키마와 오류 코드 enum을 노출하고, 모든 엔드포인트에 공통 오류 응답을 붙인다.
 * 엔드포인트별 오류(404, 409 등)는 각 슬라이스의 컨트롤러에서 선언한다.
 */
@Configuration(proxyBeanMethods = false)
public class OpenApiConfig {

    static final String PROBLEM_DETAIL = "ProblemDetail";
    static final String FIELD_ERROR = "FieldError";
    static final String PROBLEM_JSON = "application/problem+json";

    private static final List<ErrorCode> COMMON_ERRORS =
            List.of(ErrorCode.INVALID_INPUT, ErrorCode.UNAUTHENTICATED, ErrorCode.ACCESS_DENIED, ErrorCode.INTERNAL_ERROR);

    @Bean
    public OpenAPI openAPI() {
        return new OpenAPI().info(new Info().title("인계점 API").version("v1"));
    }

    @Bean
    public OpenApiCustomizer problemDetailCustomizer() {
        return openApi -> {
            if (openApi.getComponents() == null) {
                openApi.setComponents(new Components());
            }
            openApi.getComponents()
                    .addSchemas(FIELD_ERROR, fieldErrorSchema())
                    .addSchemas(PROBLEM_DETAIL, problemDetailSchema());

            if (openApi.getPaths() == null) {
                return;
            }
            openApi.getPaths().values().forEach(pathItem ->
                    pathItem.readOperations().forEach(OpenApiConfig::addCommonErrors));
        };
    }

    private static void addCommonErrors(Operation operation) {
        if (operation.getResponses() == null) {
            operation.setResponses(new ApiResponses());
        }
        for (ErrorCode errorCode : COMMON_ERRORS) {
            String status = String.valueOf(errorCode.getStatus().value());
            operation.getResponses().putIfAbsent(status, new ApiResponse()
                    .description(errorCode.getMessage())
                    .content(new Content().addMediaType(PROBLEM_JSON,
                            new MediaType().schema(new Schema<>().$ref("#/components/schemas/" + PROBLEM_DETAIL)))));
        }
    }

    private static Schema<?> problemDetailSchema() {
        List<String> codes = Arrays.stream(ErrorCode.values()).map(Enum::name).toList();
        return new ObjectSchema()
                .description("RFC 9457 오류 응답")
                .addProperty("type", new StringSchema().format("uri"))
                .addProperty("title", new StringSchema())
                .addProperty("status", new IntegerSchema())
                .addProperty("detail", new StringSchema().description("오류 메시지"))
                .addProperty("instance", new StringSchema().format("uri"))
                .addProperty(GlobalExceptionHandler.CODE, new StringSchema()._enum(codes).description("도메인 오류 코드"))
                .addProperty(GlobalExceptionHandler.ERRORS, new ArraySchema()
                        .items(new Schema<>().$ref("#/components/schemas/" + FIELD_ERROR))
                        .description("입력 검증 오류 (INVALID_INPUT일 때)"))
                .required(List.of("status", GlobalExceptionHandler.CODE));
    }

    private static Schema<?> fieldErrorSchema() {
        return new ObjectSchema()
                .addProperty("field", new StringSchema())
                .addProperty("message", new StringSchema());
    }
}
