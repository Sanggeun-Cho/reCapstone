package com.back.space;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.back.group.api.InvitationDto;
import com.back.support.GroupTestSupport;
import com.jayway.jsonpath.JsonPath;
import java.util.Map;
import java.util.Set;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.http.HttpMethod;

/**
 * AUTHZ-01: {@code TEST_SPEC.md} 2장 인가 매트릭스 중 그룹·스페이스 행. 각 칸의 상태 코드를 그대로 확인한다.
 * carol 열은 인계 진행 중(인수자) 기준이고, 나머지 열은 평상시 기준이다. "–" 칸은 검사하지 않는다.
 * 볼 수 없는 스페이스와 속하지 않은 그룹은 404(존재를 드러내지 않음), 볼 수 있지만 권한이 없는 행위는 403이다.
 */
class AuthzMatrixApiTest extends GroupTestSupport {

    private static final int SKIP = 0;
    private static final int OK = 200;
    private static final String[] USERS = {"anon", "mallory", "dave", "carol", "bob", "alice"};

    private record Row(String feature, HttpMethod method, String path, String body, int... expected) {
    }

    private static final Row[] ROWS = {
            new Row("스페이스 상세 조회", HttpMethod.GET, "/api/v1/spaces/{s}", null,
                    401, 404, 404, 200, 200, 200),
            new Row("스페이스 생성", HttpMethod.POST, "/api/v1/groups/{g}/spaces", "{\"name\":\"N\"}",
                    401, 404, 403, 403, 403, OK),
            new Row("스페이스 이름 변경", HttpMethod.PATCH, "/api/v1/spaces/{s}", "{\"name\":\"N\"}",
                    401, 404, 404, 403, 403, OK),
            new Row("스페이스 삭제 요청", HttpMethod.POST, "/api/v1/spaces/{s}/deletion-request", "{" + REAUTH + "}",
                    401, 404, 404, 403, OK, OK),
            new Row("첫 담당자 지정", HttpMethod.PUT, "/api/v1/spaces/{s2}/assignee", "{\"userId\":{dave}}",
                    401, 404, 404, SKIP, SKIP, OK),
            new Row("긴급 담당자 재지정", HttpMethod.POST, "/api/v1/spaces/{s}/forced-reassignment",
                    "{\"userId\":{dave},\"reason\":\"사유\"}",
                    401, 404, 404, 403, 403, OK),
            new Row("그룹명 수정", HttpMethod.PATCH, "/api/v1/groups/{g}", "{\"name\":\"N\"}",
                    401, 404, 403, 403, 403, OK),
            new Row("그룹 삭제", HttpMethod.POST, "/api/v1/groups/{g}/deletion", "{\"confirmName\":\"G\"," + REAUTH + "}",
                    401, 404, 403, 403, 403, OK),
            new Row("OWNER 양도", HttpMethod.POST, "/api/v1/groups/{g}/owner-transfer",
                    "{\"userId\":{dave}," + REAUTH + "}",
                    401, 404, 403, 403, 403, OK),
            new Row("멤버 초대", HttpMethod.POST, "/api/v1/groups/{g}/invitations", "{\"email\":\"erin@example.com\"}",
                    401, 404, 403, 403, 403, OK),
            new Row("초대 취소", HttpMethod.DELETE, "/api/v1/groups/{g}/invitations/{inv}", null,
                    401, 404, 403, 403, 403, OK),
            new Row("멤버 제거", HttpMethod.DELETE, "/api/v1/groups/{g}/members/{dave}", null,
                    401, 404, 403, 403, 403, OK),
            new Row("멤버 이력 조회", HttpMethod.GET, "/api/v1/groups/{g}/member-history", null,
                    401, 404, 403, 403, 403, OK),
            new Row("그룹 상세 조회", HttpMethod.GET, "/api/v1/groups/{g}", null,
                    401, 404, 200, 200, 200, 200),
            new Row("멤버 목록 조회", HttpMethod.GET, "/api/v1/groups/{g}/members", null,
                    401, 404, 200, 200, 200, 200),
    };

    static Stream<Arguments> cells() {
        return Stream.of(ROWS).flatMap(row -> Stream.of(0, 1, 2, 3, 4, 5)
                .filter(i -> row.expected()[i] != SKIP)
                .map(i -> Arguments.of(row.feature(), USERS[i], row, row.expected()[i])));
    }

    @ParameterizedTest(name = "{0} / {1} → {3}")
    @MethodSource("cells")
    @DisplayName("AUTHZ-01: 그룹·스페이스 인가 매트릭스")
    void matrix(String feature, String user, Row row, int expected) throws Exception {
        long erinInvitation = invitationService.invite(alice, g,
                new InvitationDto.CreateReq("pending@example.com")).id();
        if (user.equals("carol")) {
            handoverStatus.start(s, g, bob, carol);
        }
        Map<String, Long> ids = Map.of("{g}", g, "{s}", s, "{s2}", s2, "{dave}", dave, "{inv}", erinInvitation);

        int actual = call(row.method(), fill(row.path(), ids), email(user), row.body() == null ? null : fill(row.body(), ids))
                .andReturn().getResponse().getStatus();

        assertCell(feature, user, expected, actual);
    }

    /** "스페이스 삭제 요청·승인" 행의 승인 칸. 상대방이 먼저 요청해 두어야 하므로 매트릭스 표와 따로 확인한다. */
    static Stream<Arguments> approvalCells() {
        int[] expected = {401, 404, 404, 403, OK, OK};
        return IntStream.range(0, USERS.length).mapToObj(i -> Arguments.of(USERS[i], expected[i]));
    }

    @ParameterizedTest(name = "스페이스 삭제 승인 / {0} → {1}")
    @MethodSource("approvalCells")
    @DisplayName("AUTHZ-01: 스페이스 삭제 승인 (상대방의 요청이 대기 중일 때)")
    void deletionApproval(String user, int expected) throws Exception {
        String requester = user.equals("alice") ? BOB : ALICE;
        post("/api/v1/spaces/" + s + "/deletion-request", requester, "{" + REAUTH + "}").andExpect(status().isOk());
        if (user.equals("carol")) {
            handoverStatus.start(s, g, bob, carol);
        }

        int actual = call(HttpMethod.POST, "/api/v1/spaces/" + s + "/deletion-request/approve", email(user),
                "{" + REAUTH + "}").andReturn().getResponse().getStatus();

        assertCell("스페이스 삭제 승인", user, expected, actual);
    }

    private static void assertCell(String feature, String user, int expected, int actual) {
        if (expected == OK) {
            assertThat(actual / 100).as("%s / %s: %d", feature, user, actual).isEqualTo(2);
        } else {
            assertThat(actual).as(feature + " / " + user).isEqualTo(expected);
        }
    }

    @Test
    @DisplayName("AUTHZ-02: 멤버 직접 추가, 그룹·스페이스 복구, 권한(RBAC) 부여 API는 없다")
    void apisNotProvided() throws Exception {
        String docs = mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .get("/v3/api-docs"))
                .andReturn().getResponse().getContentAsString();
        Map<String, Map<String, Object>> paths = JsonPath.read(docs, "$.paths");

        assertThat(paths.get("/api/v1/groups/{groupId}/members")).doesNotContainKeys("post", "put");
        // 휴지통 항목 복구(정책 8.5)는 담당자 기능이라 예외다. 그룹·스페이스 복구는 운영자 기능(S8)으로 남긴다
        assertThat(paths.keySet()).noneMatch(path -> (path.contains("restore") && !path.contains("/trash/"))
                || path.contains("role")
                || path.contains("permission") || path.contains("rbac"));
        assertThat(paths.keySet()).doesNotContain("/api/v1/users/{userId}");
        assertThat(Set.copyOf(paths.keySet())).contains("/api/v1/groups/{groupId}/invitations");
    }

    private static String fill(String template, Map<String, Long> ids) {
        String filled = template;
        for (Map.Entry<String, Long> id : ids.entrySet()) {
            filled = filled.replace(id.getKey(), String.valueOf(id.getValue()));
        }
        return filled;
    }

    private static String email(String user) {
        return switch (user) {
            case "anon" -> ANON;
            case "mallory" -> MALLORY;
            case "dave" -> DAVE;
            case "carol" -> CAROL;
            case "bob" -> BOB;
            default -> ALICE;
        };
    }
}
