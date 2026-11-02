package com.back.space;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.contains;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.back.support.GroupTestSupport;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.dao.DataIntegrityViolationException;

class AssignmentApiTest extends GroupTestSupport {

    @Test
    @DisplayName("SPC-10: 담당자 없는 스페이스에 지정하면 INITIAL 이력이 남는다")
    void initialAssignment() throws Exception {
        put("/api/v1/spaces/" + s2 + "/assignee", ALICE, body("\"userId\":" + carol))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.assignee.id").value(carol));

        Map<String, Object> change = lastChange(s2);
        assertThat(change.get("type")).isEqualTo("INITIAL");
        assertThat(change.get("from_user_id")).isNull();
        assertThat(change.get("to_user_id")).isEqualTo(carol);
        assertThat(change.get("actor_id")).isEqualTo(alice);
        assertThat(change.get("reason")).isNull();
    }

    @Test
    @DisplayName("SPC-11: 담당자가 있는 스페이스에 지정 API를 쓰면 409, 변화 없음")
    void assigneeAlreadySet() throws Exception {
        put("/api/v1/spaces/" + s + "/assignee", ALICE, body("\"userId\":" + carol))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ASSIGNEE_ALREADY_SET"));
        assertThat(assigneeOf(s)).isEqualTo(bob);
    }

    @Test
    @DisplayName("SPC-11: OWNER가 아니면 지정할 수 없고(403), 인계 중이면 409")
    void assignForbiddenOrLocked() throws Exception {
        put("/api/v1/spaces/" + s2 + "/assignee", BOB, body("\"userId\":" + carol)).andExpect(status().isForbidden());

        handoverStatus.start(s2, g, alice, carol);
        put("/api/v1/spaces/" + s2 + "/assignee", ALICE, body("\"userId\":" + carol))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("HANDOVER_IN_PROGRESS"));
    }

    @Test
    @DisplayName("SPC-12: 긴급 재지정하면 담당자가 바로 바뀌고 FORCED 이력이 사유와 함께 남는다. 이전 담당자는 접근을 잃는다")
    void forcedReassignment() throws Exception {
        forced(s, carol, "연락 두절").andExpect(status().isOk()).andExpect(jsonPath("$.assignee.id").value(carol));

        Map<String, Object> change = lastChange(s);
        assertThat(change.get("type")).isEqualTo("FORCED");
        assertThat(change.get("from_user_id")).isEqualTo(bob);
        assertThat(change.get("to_user_id")).isEqualTo(carol);
        assertThat(change.get("actor_id")).isEqualTo(alice);
        assertThat(change.get("reason")).isEqualTo("연락 두절");
        get("/api/v1/spaces/" + s, BOB).andExpect(status().isForbidden());
        get("/api/v1/spaces/" + s, CAROL).andExpect(status().isOk());
    }

    @Test
    @DisplayName("SPC-12: 인계가 진행 중이어도 긴급 재지정은 할 수 있다 (진행 중 인계 취소는 S5)")
    void forcedDuringHandover() throws Exception {
        handoverStatus.start(s, g, bob, carol);
        forced(s, dave, "퇴사").andExpect(status().isOk());
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "   "})
    @DisplayName("SPC-14: 사유가 비었거나 공백뿐이면 400")
    void reasonRequired(String reason) throws Exception {
        forced(s, carol, reason).andExpect(status().isBadRequest());
        post("/api/v1/spaces/" + s + "/forced-reassignment", ALICE, body("\"userId\":" + carol))
                .andExpect(status().isBadRequest());
        assertThat(assigneeOf(s)).isEqualTo(bob);
    }

    @Test
    @DisplayName("SPC-14: 사유는 500자까지, 501자면 400")
    void reasonLength() throws Exception {
        forced(s, carol, "가".repeat(501)).andExpect(status().isBadRequest());
        forced(s, carol, "가".repeat(500)).andExpect(status().isOk());
    }

    @Test
    @DisplayName("SPC-14: 그룹 밖 사용자로는 재지정할 수 없다 (400 NOT_GROUP_MEMBER)")
    void forcedToOutsider() throws Exception {
        forced(s, mallory, "사유")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("NOT_GROUP_MEMBER"));
    }

    @Test
    @DisplayName("SPC-14b: 담당자는 긴급 재지정을 할 수 없다 (403)")
    void assigneeCannotForce() throws Exception {
        post("/api/v1/spaces/" + s + "/forced-reassignment", BOB,
                body("\"userId\":" + carol + ",\"reason\":\"사유\"")).andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("SPC-15: 긴급 재지정으로 비운 뒤 다시 지정하면 FORCED(to=null) 다음 REFILL이 남는다")
    void refill() throws Exception {
        forced(s, null, "사망").andExpect(status().isOk()).andExpect(jsonPath("$.assignee").doesNotExist());
        clock.advance(Duration.ofSeconds(1));
        put("/api/v1/spaces/" + s + "/assignee", ALICE, body("\"userId\":" + carol)).andExpect(status().isOk());

        List<String> types = jdbcTemplate.queryForList(
                "SELECT type || ':' || coalesce(from_user_id::text, '-') || '>' || coalesce(to_user_id::text, '-') "
                        + "FROM assignment_change WHERE space_id = ? ORDER BY occurred_at, id", String.class, s);
        assertThat(types).containsExactly(
                "INITIAL:->" + bob, "FORCED:" + bob + ">-", "REFILL:->" + carol);
    }

    @Test
    @DisplayName("SPC-15b·SPC-17: FORCED 이력이 없는 스페이스의 지정은 INITIAL이다")
    void initialWithoutForcedHistory() throws Exception {
        long s3 = createSpace(alice, g, "S3");
        put("/api/v1/spaces/" + s3 + "/assignee", ALICE, body("\"userId\":" + dave)).andExpect(status().isOk());
        assertThat(lastChange(s3).get("type")).isEqualTo("INITIAL");
    }

    @Test
    @DisplayName("SPC-15c: 담당자가 없는 스페이스는 긴급 재지정할 수 없다 (400)")
    void forcedOnVacantSpace() throws Exception {
        forced(s2, null, "사유")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_INPUT"));
    }

    @Test
    @DisplayName("SPC-16: 담당자 변경 이력은 OWNER와 현재 담당자가 시간순으로 보고, 관계없는 멤버는 403")
    void history() throws Exception {
        forced(s, null, "사망").andExpect(status().isOk());
        clock.advance(Duration.ofSeconds(1));
        put("/api/v1/spaces/" + s + "/assignee", ALICE, body("\"userId\":" + carol)).andExpect(status().isOk());

        get("/api/v1/spaces/" + s + "/assignment-changes", ALICE)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].type", contains("INITIAL", "FORCED", "REFILL")))
                .andExpect(jsonPath("$[1].reason").value("사망"))
                .andExpect(jsonPath("$[1].actor.name").value("앨리스"))
                .andExpect(jsonPath("$[1].from.name").value("밥"));
        get("/api/v1/spaces/" + s + "/assignment-changes", CAROL).andExpect(status().isOk());
        get("/api/v1/spaces/" + s + "/assignment-changes", BOB).andExpect(status().isForbidden());
        get("/api/v1/spaces/" + s + "/assignment-changes", DAVE).andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("GRP-15: 제거된 멤버는 담당자로 지정할 수 없다. DB에 직접 넣어도 FK가 거부한다")
    void removedMemberCannotBeAssigned() throws Exception {
        delete("/api/v1/groups/" + g + "/members/" + dave, ALICE).andExpect(status().isNoContent());

        put("/api/v1/spaces/" + s2 + "/assignee", ALICE, body("\"userId\":" + dave))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("NOT_GROUP_MEMBER"));
        assertThatThrownBy(() -> jdbcTemplate.update("UPDATE space SET assignee_id = ? WHERE id = ?", dave, s2))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    private org.springframework.test.web.servlet.ResultActions forced(long spaceId, Long userId, String reason)
            throws Exception {
        String fields = (userId == null ? "" : "\"userId\":" + userId + ",") + "\"reason\":\"" + reason + "\"";
        return post("/api/v1/spaces/" + spaceId + "/forced-reassignment", ALICE, body(fields));
    }

    private Map<String, Object> lastChange(long spaceId) {
        return jdbcTemplate.queryForMap(
                "SELECT * FROM assignment_change WHERE space_id = ? ORDER BY occurred_at DESC, id DESC LIMIT 1", spaceId);
    }

    private Long assigneeOf(long spaceId) {
        return jdbcTemplate.queryForObject("SELECT assignee_id FROM space WHERE id = ?", Long.class, spaceId);
    }
}
