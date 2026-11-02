package com.back.group;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.back.support.GroupTestSupport;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.ResultActions;

/**
 * 탈퇴와 그룹의 연동 (정책 7.8). S1의 {@code WithdrawalApiTest}에 이어지는 케이스다.
 */
class GroupWithdrawalApiTest extends GroupTestSupport {

    @Test
    @DisplayName("USER-05: OWNER와 담당자는 탈퇴할 수 없고, 응답에 그 사유가 담긴다")
    void ownerAndAssigneeBlocked() throws Exception {
        withdraw(ALICE)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("WITHDRAWAL_BLOCKED"))
                .andExpect(jsonPath("$.reasons", contains("OWNER")));
        withdraw(BOB)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.reasons", contains("ASSIGNEE")));
        assertThat(count("SELECT count(*) FROM users WHERE id IN (?, ?) AND deleted_at IS NULL", alice, bob))
                .isEqualTo(2);
    }

    @Test
    @DisplayName("USER-05: OWNER이면서 담당자이면 두 사유가 모두 담긴다")
    void bothReasons() throws Exception {
        assign(alice, s2, alice);
        withdraw(ALICE).andExpect(jsonPath("$.reasons", contains("OWNER", "ASSIGNEE")));
    }

    @Test
    @DisplayName("USER-07 (S2): 멤버가 탈퇴하면 그룹에서 빠지고 본인이 실행자인 LEFT 이력이 남는다")
    void memberLeavesGroupsOnWithdrawal() throws Exception {
        long h = createGroup(carol, "H");
        join(h, carol, dave);

        withdraw(DAVE).andExpect(status().isNoContent());

        assertThat(count("SELECT count(*) FROM group_member WHERE user_id = ?", dave)).isZero();
        assertThat(count("""
                SELECT count(*) FROM group_member_history
                WHERE user_id = ? AND type = 'LEFT' AND actor_id = ? AND group_id IN (?, ?)
                """, dave, dave, g, h)).isEqualTo(2);
    }

    @Test
    @DisplayName("PURGE-08: 보관 중인 그룹의 OWNER 지위는 탈퇴를 막지 않는다")
    void archivedOwnershipDoesNotBlock() throws Exception {
        long h = createGroup(mallory, "H");
        post("/api/v1/groups/" + h + "/deletion", MALLORY, body("\"confirmName\":\"H\"," + REAUTH))
                .andExpect(status().isNoContent());

        withdraw(MALLORY).andExpect(status().isNoContent());
        // 보관 중인 그룹의 멤버 행은 영구 삭제 때까지 남는다
        assertThat(count("SELECT count(*) FROM group_member WHERE group_id = ? AND user_id = ?", h, mallory)).isOne();
    }

    @Test
    @DisplayName("PURGE-08: 보관 중인 스페이스의 담당은 탈퇴를 막지 않는다")
    void archivedAssignmentDoesNotBlock() throws Exception {
        long s3 = createSpace(alice, g, "S3");
        assign(alice, s3, dave);
        jdbcTemplate.update("UPDATE space SET deleted_at = now() WHERE id = ?", s3);

        withdraw(DAVE).andExpect(status().isNoContent());
    }

    private ResultActions withdraw(String email) throws Exception {
        return post("/api/v1/users/me/withdrawal", email, body("\"password\":\"" + PASSWORD + "\""));
    }
}
