package com.back.group;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.back.support.GroupTestSupport;
import com.jayway.jsonpath.JsonPath;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class GroupApiTest extends GroupTestSupport {

    @Test
    @DisplayName("GRP-01: 그룹을 만들면 만든 사람이 OWNER이자 첫 멤버가 되고, 생성자와 가입 이력이 남는다")
    void create() throws Exception {
        String res = post("/api/v1/groups", CAROL, body("\"name\":\"H\""))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("H"))
                .andReturn().getResponse().getContentAsString();
        long h = ((Number) JsonPath.read(res, "$.id")).longValue();

        Map<String, Object> row = jdbcTemplate.queryForMap("SELECT owner_id, created_by FROM groups WHERE id = ?", h);
        assertThat(row.get("owner_id")).isEqualTo(carol);
        assertThat(row.get("created_by")).isEqualTo(carol);
        assertThat(count("SELECT count(*) FROM group_member WHERE group_id = ? AND user_id = ?", h, carol)).isOne();
        assertThat(count("""
                SELECT count(*) FROM group_member_history
                WHERE group_id = ? AND user_id = ? AND type = 'JOINED' AND actor_id = ?
                """, h, carol, carol)).isOne();
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "   ", "　　", "123456789012345678901234567890123456789012345678901"})
    @DisplayName("GRP-02: 그룹 이름이 비었거나 공백(전각 공백 포함)뿐이거나 51자면 400")
    void invalidName(String name) throws Exception {
        post("/api/v1/groups", CAROL, body("\"name\":\"" + name + "\""))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_INPUT"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"a", "12345678901234567890123456789012345678901234567890", "  앞뒤 공백  "})
    @DisplayName("GRP-02b: 그룹 이름 1자, 50자(경계값)와 앞뒤 공백이 있는 이름은 만들 수 있다")
    void boundaryName(String name) throws Exception {
        post("/api/v1/groups", CAROL, body("\"name\":\"" + name + "\""))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value(name.strip()));
    }

    @Test
    @DisplayName("GRP-07: 내 그룹 목록에는 내가 멤버인 그룹만 나오고, OWNER인지 표시한다")
    void myGroups() throws Exception {
        long h = createGroup(alice, "H");

        get("/api/v1/groups", ALICE)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[*].id", contains((int) h, (int) g)))
                .andExpect(jsonPath("$.items[*].owner", contains(true, true)));
        get("/api/v1/groups", DAVE)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[*].name", contains("G")))
                .andExpect(jsonPath("$.items[0].owner").value(false));
        get("/api/v1/groups", MALLORY)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items", hasSize(0)))
                .andExpect(jsonPath("$.nextCursor").value(nullValue()));
    }

    @Test
    @DisplayName("GRP-07b: 커서로 이어 읽으면 중복·누락 없이 모두 나오고, 마지막 페이지의 nextCursor는 null")
    void myGroupsCursor() throws Exception {
        createGroup(alice, "H");
        createGroup(alice, "I");

        String first = get("/api/v1/groups?size=2", ALICE)
                .andExpect(jsonPath("$.items", hasSize(2)))
                .andReturn().getResponse().getContentAsString();
        Number cursor = JsonPath.read(first, "$.nextCursor");
        assertThat(cursor).isNotNull();

        String second = get("/api/v1/groups?size=2&cursor=" + cursor, ALICE)
                .andExpect(jsonPath("$.items", hasSize(1)))
                .andExpect(jsonPath("$.nextCursor").value(nullValue()))
                .andReturn().getResponse().getContentAsString();

        List<String> names = new ArrayList<>(JsonPath.read(first, "$.items[*].name"));
        names.addAll(JsonPath.read(second, "$.items[*].name"));
        assertThat(names).containsExactly("I", "H", "G");
    }

    @ParameterizedTest
    @ValueSource(strings = {"0", "101"})
    @DisplayName("GRP-07b: 페이지 크기가 1~100을 벗어나면 400")
    void myGroupsInvalidSize(String size) throws Exception {
        get("/api/v1/groups?size=" + size, ALICE)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_INPUT"));
    }

    @Test
    @DisplayName("GRP-07c: 삭제한 그룹은 내 그룹 목록에 나오지 않는다")
    void deletedGroupHiddenFromList() throws Exception {
        deleteGroupG();

        get("/api/v1/groups", DAVE).andExpect(jsonPath("$.items", hasSize(0)));
        get("/api/v1/groups", ALICE).andExpect(jsonPath("$.items", hasSize(0)));
    }

    @Test
    @DisplayName("GRP-16: 멤버는 그룹 상세와 멤버 목록(이름·이메일·업무용 전화번호)을 본다")
    void memberViewsGroup() throws Exception {
        get("/api/v1/groups/" + g, DAVE)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("G"))
                .andExpect(jsonPath("$.owner.id").value(alice))
                .andExpect(jsonPath("$.owner.name").value("앨리스"))
                .andExpect(jsonPath("$.memberCount").value(4));

        get("/api/v1/groups/" + g + "/members", DAVE)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(4)))
                .andExpect(jsonPath("$[?(@.userId == " + bob + ")].email", contains(BOB)))
                .andExpect(jsonPath("$[?(@.userId == " + bob + ")].workPhone", contains("010-1234-5678")))
                .andExpect(jsonPath("$[?(@.userId == " + bob + ")].name", contains("밥")))
                .andExpect(jsonPath("$[?(@.owner == true)].userId", contains((int) alice)))
                .andExpect(jsonPath("$[*].joinedAt", hasSize(4)));
    }

    @Test
    @DisplayName("GRP-16b: 그룹 밖 사용자는 그룹 상세와 멤버 목록을 볼 수 없다 (403)")
    void outsiderCannotViewGroup() throws Exception {
        get("/api/v1/groups/" + g, MALLORY)
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
        get("/api/v1/groups/" + g + "/members", MALLORY).andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("GRP-16c: 삭제한 그룹은 멤버에게도 404")
    void deletedGroupNotFound() throws Exception {
        deleteGroupG();

        get("/api/v1/groups/" + g, DAVE)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));
        get("/api/v1/groups/" + g + "/members", DAVE).andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("GRP-16c: 없는 그룹은 404")
    void unknownGroupNotFound() throws Exception {
        get("/api/v1/groups/999999", DAVE).andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("GRP-18: 그룹 이름은 OWNER만 바꾼다")
    void rename() throws Exception {
        patch("/api/v1/groups/" + g, ALICE, body("\"name\":\"G2\""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("G2"));
        patch("/api/v1/groups/" + g, BOB, body("\"name\":\"G3\""))
                .andExpect(status().isForbidden());

        assertThat(jdbcTemplate.queryForObject("SELECT name FROM groups WHERE id = ?", String.class, g))
                .isEqualTo("G2");
    }

    @Test
    @DisplayName("GRP-18: 이름 변경도 이름 규칙(1~50자)을 따른다")
    void renameInvalid() throws Exception {
        patch("/api/v1/groups/" + g, ALICE, body("\"name\":\" \"")).andExpect(status().isBadRequest());
    }

    private void deleteGroupG() throws Exception {
        post("/api/v1/groups/" + g + "/deletion", ALICE, body("\"confirmName\":\"G\"," + REAUTH))
                .andExpect(status().isNoContent());
    }

    @Test
    @DisplayName("GRP-16: 멤버 목록의 이메일 순서와 관계없이 네 명이 모두 나온다")
    void membersContainAll() throws Exception {
        get("/api/v1/groups/" + g + "/members", ALICE)
                .andExpect(jsonPath("$[*].email", containsInAnyOrder(ALICE, BOB, CAROL, DAVE)));
    }
}
