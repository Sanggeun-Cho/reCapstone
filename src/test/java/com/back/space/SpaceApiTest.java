package com.back.space;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.back.auth.api.AuthDto;
import com.back.space.api.SpaceDto;
import com.back.space.application.SpaceDeletionService;
import com.back.support.GroupTestSupport;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;

class SpaceApiTest extends GroupTestSupport {

    @Autowired
    SpaceDeletionService deletionService;

    @Test
    @DisplayName("SPC-01: OWNER가 스페이스를 만들면 담당자 없이 생긴다. 응답에 spaceCode가 없다")
    void create() throws Exception {
        post("/api/v1/groups/" + g + "/spaces", ALICE, body("\"name\":\"S9\""))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("S9"))
                .andExpect(jsonPath("$.assignee").value(nullValue()))
                .andExpect(jsonPath("$.spaceCode").doesNotExist());
    }

    @Test
    @DisplayName("SPC-01b: 담당자나 그룹 밖 사용자는 스페이스를 만들 수 없다 (403)")
    void createForbidden() throws Exception {
        post("/api/v1/groups/" + g + "/spaces", BOB, body("\"name\":\"S9\"")).andExpect(status().isForbidden());
        post("/api/v1/groups/" + g + "/spaces", MALLORY, body("\"name\":\"S9\"")).andExpect(status().isForbidden());
    }

    @ParameterizedTest
    @ValueSource(strings = {"123456789012345678901234567890123456789012345678901", " ", "　　"})
    @DisplayName("SPC-01c: 이름이 51자이거나 공백(전각 공백 포함)뿐이면 400")
    void createInvalidName(String name) throws Exception {
        post("/api/v1/groups/" + g + "/spaces", ALICE, body("\"name\":\"" + name + "\""))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("SPC-01d: 삭제한 그룹에는 스페이스를 만들 수 없다 (404)")
    void createInDeletedGroup() throws Exception {
        deleteGroupG();
        post("/api/v1/groups/" + g + "/spaces", ALICE, body("\"name\":\"S9\"")).andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("SPC-08: OWNER의 스페이스 목록에는 삭제하지 않은 스페이스가 담당자·삭제 대기 여부와 함께 나온다")
    void groupSpaces() throws Exception {
        long s3 = createSpace(alice, g, "S3");
        jdbcTemplate.update("UPDATE space SET deleted_at = now() WHERE id = ?", s3);
        deletionService.request(alice, s, new SpaceDto.DeletionReq(new AuthDto.ReauthReq(PASSWORD, null)));

        get("/api/v1/groups/" + g + "/spaces?page=1&size=10", ALICE)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[*].name", contains("S", "S2")))
                .andExpect(jsonPath("$.content[0].assignee.id").value(bob))
                .andExpect(jsonPath("$.content[0].assignee.name").value("밥"))
                .andExpect(jsonPath("$.content[0].ongoingHandoverId").value(nullValue()))
                .andExpect(jsonPath("$.content[0].deletionPending").value(true))
                .andExpect(jsonPath("$.content[1].assignee").value(nullValue()))
                .andExpect(jsonPath("$.content[1].deletionPending").value(false))
                .andExpect(jsonPath("$.totalElements").value(2));
    }

    @Test
    @DisplayName("SPC-08: 인계가 진행 중인 스페이스는 목록에 인계 id가 나온다")
    void groupSpacesShowOngoingHandover() throws Exception {
        handoverStatus.start(s, g, bob, carol);

        get("/api/v1/groups/" + g + "/spaces", ALICE)
                .andExpect(jsonPath("$.content[0].ongoingHandoverId").isNumber());
    }

    @Test
    @DisplayName("SPC-08b: 스페이스 12개를 10개씩 나누면 1쪽 10건, 2쪽 2건")
    void groupSpacesPaging() throws Exception {
        for (int i = 3; i <= 12; i++) {
            createSpace(alice, g, "S" + i);
        }

        get("/api/v1/groups/" + g + "/spaces?page=1&size=10", ALICE)
                .andExpect(jsonPath("$.content", hasSize(10)))
                .andExpect(jsonPath("$.totalElements").value(12))
                .andExpect(jsonPath("$.totalPages").value(2));
        get("/api/v1/groups/" + g + "/spaces?page=2&size=10", ALICE)
                .andExpect(jsonPath("$.content", hasSize(2)))
                .andExpect(jsonPath("$.page").value(2));
    }

    @ParameterizedTest
    @ValueSource(strings = {"page=0", "size=0", "size=101"})
    @DisplayName("SPC-08b: 페이지 번호가 1보다 작거나 크기가 1~100을 벗어나면 400")
    void groupSpacesInvalidPaging(String query) throws Exception {
        get("/api/v1/groups/" + g + "/spaces?" + query, ALICE).andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("SPC-09: 관계없는 멤버는 그룹의 스페이스 목록과 스페이스 상세를 볼 수 없다 (403)")
    void unrelatedMember() throws Exception {
        get("/api/v1/groups/" + g + "/spaces", DAVE).andExpect(status().isForbidden());
        get("/api/v1/spaces/" + s, DAVE).andExpect(status().isForbidden());
        get("/api/v1/groups/" + g + "/spaces", BOB).andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("SPC-19: OWNER와 담당자는 스페이스 상세를 보고, 삭제된 스페이스는 404")
    void detail() throws Exception {
        get("/api/v1/spaces/" + s, ALICE)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("S"))
                .andExpect(jsonPath("$.group.id").value(g))
                .andExpect(jsonPath("$.group.name").value("G"))
                .andExpect(jsonPath("$.assignee.id").value(bob));
        get("/api/v1/spaces/" + s, BOB).andExpect(status().isOk());

        jdbcTemplate.update("UPDATE space SET deleted_at = now() WHERE id = ?", s);
        get("/api/v1/spaces/" + s, ALICE).andExpect(status().isNotFound());
        get("/api/v1/spaces/" + s, BOB).andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("SPC-19: 인수자는 스페이스 상세를 본다")
    void receiverReads() throws Exception {
        handoverStatus.start(s, g, bob, carol);
        get("/api/v1/spaces/" + s, CAROL).andExpect(status().isOk());
    }

    @Test
    @DisplayName("SPC-20: 스페이스 이름은 OWNER만 바꾼다")
    void rename() throws Exception {
        patch("/api/v1/spaces/" + s, ALICE, body("\"name\":\"새 이름\""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("새 이름"));
        patch("/api/v1/spaces/" + s, BOB, body("\"name\":\"다른 이름\"")).andExpect(status().isForbidden());
        assertThat(jdbcTemplate.queryForObject("SELECT name FROM space WHERE id = ?", String.class, s))
                .isEqualTo("새 이름");
    }

    @Test
    @DisplayName("SPC-20: 인계가 진행 중인 스페이스는 이름을 바꿀 수 없다 (409)")
    void renameDuringHandover() throws Exception {
        handoverStatus.start(s, g, bob, carol);
        patch("/api/v1/spaces/" + s, ALICE, body("\"name\":\"새 이름\""))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("HANDOVER_IN_PROGRESS"));
    }

    private void deleteGroupG() throws Exception {
        post("/api/v1/groups/" + g + "/deletion", ALICE, body("\"confirmName\":\"G\"," + REAUTH))
                .andExpect(status().isNoContent());
    }
}
