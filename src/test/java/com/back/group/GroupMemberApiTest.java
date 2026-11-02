package com.back.group;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.back.group.api.GroupDto;
import com.back.space.api.SpaceDto;
import com.back.support.GroupTestSupport;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class GroupMemberApiTest extends GroupTestSupport {

    @Test
    @DisplayName("GRP-10: OWNER는 양도하기 전에는 나가거나 자신을 제거할 수 없다")
    void ownerCannotLeave() throws Exception {
        delete("/api/v1/groups/" + g + "/members/me", ALICE)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("OWNER_MUST_TRANSFER"));
        delete("/api/v1/groups/" + g + "/members/" + alice, ALICE)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("OWNER_MUST_TRANSFER"));
    }

    @Test
    @DisplayName("GRP-11: 스페이스 담당자는 제거되거나 스스로 나갈 수 없고, 이력도 남지 않는다")
    void assigneeCannotLeave() throws Exception {
        long before = historyCount();

        delete("/api/v1/groups/" + g + "/members/" + bob, ALICE)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("MEMBER_HAS_RESPONSIBILITY"));
        delete("/api/v1/groups/" + g + "/members/me", BOB)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("MEMBER_HAS_RESPONSIBILITY"));

        assertThat(isMember(bob)).isTrue();
        assertThat(historyCount()).isEqualTo(before);
    }

    @Test
    @DisplayName("GRP-11: 진행 중인 인계의 당사자(인수자)도 나갈 수 없다")
    void handoverPartyCannotLeave() throws Exception {
        handoverStatus.start(s, g, bob, carol);

        delete("/api/v1/groups/" + g + "/members/me", CAROL)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("MEMBER_HAS_RESPONSIBILITY"));
    }

    @Test
    @DisplayName("GRP-11b: 긴급 재지정으로 담당에서 벗어나면 제거할 수 있고, REMOVED 이력이 남는다")
    void removeAfterForcedReassignment() throws Exception {
        spaceService.forceReassign(alice, s, new SpaceDto.ForcedReassignmentReq(null, "연락 두절"));

        delete("/api/v1/groups/" + g + "/members/" + bob, ALICE).andExpect(status().isNoContent());

        assertThat(isMember(bob)).isFalse();
        assertThat(count("""
                SELECT count(*) FROM group_member_history
                WHERE group_id = ? AND user_id = ? AND type = 'REMOVED' AND actor_id = ?
                """, g, bob, alice)).isOne();
    }

    @Test
    @DisplayName("GRP-11c: 삭제되어 보관 중인 스페이스의 담당은 제거를 막지 않는다")
    void archivedAssignmentDoesNotBlock() throws Exception {
        long s3 = createSpace(alice, g, "S3");
        assign(alice, s3, dave);
        jdbcTemplate.update("UPDATE space SET deleted_at = now() WHERE id = ?", s3);

        delete("/api/v1/groups/" + g + "/members/" + dave, ALICE).andExpect(status().isNoContent());

        assertThat(isMember(dave)).isFalse();
    }

    @Test
    @DisplayName("GRP-13: 제거 → 재초대·수락 → 스스로 나가기. 멤버 행은 현재 상태만, 이력은 네 건이 쌓인다")
    void membershipLifecycle() throws Exception {
        delete("/api/v1/groups/" + g + "/members/" + dave, ALICE).andExpect(status().isNoContent());
        clock.advance(Duration.ofSeconds(1));
        join(g, alice, dave);
        clock.advance(Duration.ofSeconds(1));
        delete("/api/v1/groups/" + g + "/members/me", DAVE).andExpect(status().isNoContent());

        assertThat(isMember(dave)).isFalse();
        List<String> history = jdbcTemplate.queryForList("""
                SELECT type || ':' || actor_id FROM group_member_history
                WHERE group_id = ? AND user_id = ? ORDER BY occurred_at, id
                """, String.class, g, dave);
        assertThat(history).containsExactly(
                "JOINED:" + dave, "REMOVED:" + alice, "JOINED:" + dave, "LEFT:" + dave);
    }

    @Test
    @DisplayName("GRP-13b: 일반 멤버는 다른 멤버를 제거할 수 없다 (403)")
    void memberCannotRemoveOthers() throws Exception {
        delete("/api/v1/groups/" + g + "/members/" + carol, DAVE).andExpect(status().isForbidden());
        assertThat(isMember(carol)).isTrue();
    }

    @Test
    @DisplayName("GRP-13c: 멤버가 아닌 사용자를 제거하면 404")
    void removeNonMember() throws Exception {
        delete("/api/v1/groups/" + g + "/members/" + mallory, ALICE)
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("GRP-13: 그룹 밖 사용자가 나가기를 호출하면 403")
    void outsiderLeave() throws Exception {
        delete("/api/v1/groups/" + g + "/members/me", MALLORY).andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("GRP-17·USER-08: 멤버 이력은 OWNER만 시간순으로 보고, 탈퇴한 사용자는 \"탈퇴한 사용자\"로 표시된다")
    void history() throws Exception {
        delete("/api/v1/groups/" + g + "/members/" + dave, ALICE).andExpect(status().isNoContent());
        clock.advance(Duration.ofSeconds(1));
        join(g, alice, dave);
        clock.advance(Duration.ofSeconds(1));
        post("/api/v1/users/me/withdrawal", DAVE, body("\"password\":\"" + PASSWORD + "\""))
                .andExpect(status().isNoContent());

        get("/api/v1/groups/" + g + "/member-history", ALICE)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.user.id == " + dave + ")].type",
                        contains("JOINED", "REMOVED", "JOINED", "LEFT")))
                .andExpect(jsonPath("$[?(@.user.id == " + dave + ")].user.name",
                        contains(GroupDto.WITHDRAWN_USER_NAME, GroupDto.WITHDRAWN_USER_NAME,
                                GroupDto.WITHDRAWN_USER_NAME, GroupDto.WITHDRAWN_USER_NAME)))
                .andExpect(jsonPath("$[?(@.type == 'REMOVED')].actor.name", contains("앨리스")))
                .andExpect(jsonPath("$[0].user.id").value(alice));
        get("/api/v1/groups/" + g + "/member-history", CAROL).andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("USER-11: 그룹 밖 사용자는 멤버의 연락처를 볼 수 없다")
    void outsiderCannotSeeContacts() throws Exception {
        get("/api/v1/groups/" + g + "/members", MALLORY).andExpect(status().isForbidden());
        get("/api/v1/users/" + bob, MALLORY).andExpect(status().is4xxClientError());
    }

    private boolean isMember(long userId) {
        return count("SELECT count(*) FROM group_member WHERE group_id = ? AND user_id = ?", g, userId) == 1;
    }

    private long historyCount() {
        return count("SELECT count(*) FROM group_member_history WHERE group_id = ?", g);
    }
}
