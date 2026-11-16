package com.back.group;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.back.auth.api.AuthDto;
import com.back.group.api.GroupDto;
import com.back.group.api.InvitationDto;
import com.back.space.api.SpaceDto;
import com.back.space.application.SpaceDeletionService;
import com.back.support.GroupTestSupport;
import com.back.user.application.UserService;
import java.time.Duration;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;

/**
 * 영구 삭제 작업 (정책 2.6, 7.9)과 추가 전용 이력 테이블의 DB 보장 (GRP-14, SPC-18).
 */
class PurgeJobTest extends GroupTestSupport {

    private static final Duration RETENTION = Duration.ofDays(30);
    private static final AuthDto.ReauthReq REAUTH_REQ = new AuthDto.ReauthReq(PASSWORD, null);

    @Autowired
    SpaceDeletionService deletionService;

    @Autowired
    UserService userService;

    @Test
    @DisplayName("PURGE-01: 삭제 후 30일이 되기 1초 전에는 아무것도 지우지 않는다")
    void notYet() {
        deleteGroup(alice, g, "G");
        clock.advance(RETENTION.minusSeconds(1));

        purgeService.purgeExpired();

        assertThat(count("SELECT count(*) FROM groups WHERE id = ?", g)).isOne();
        assertThat(count("SELECT count(*) FROM space WHERE group_id = ?", g)).isEqualTo(2);
    }

    @Test
    @DisplayName("PURGE-02: 삭제 후 정확히 30일이 지나면 그룹과 그 스페이스·멤버·이력·초대·삭제 요청을 모두 지운다")
    void purgeGroup() {
        createUser("erin@example.com", "에린", null);
        as(alice, () -> invitationService.invite(alice, g, new InvitationDto.CreateReq("erin@example.com")));
        as(alice, () -> deletionService.request(alice, s, new SpaceDto.DeletionReq(REAUTH_REQ)));
        deleteGroup(alice, g, "G");
        clock.advance(RETENTION);

        purgeService.purgeExpired();

        assertThat(count("SELECT count(*) FROM groups WHERE id = ?", g)).isZero();
        assertThat(count("SELECT count(*) FROM space WHERE group_id = ?", g)).isZero();
        assertThat(count("SELECT count(*) FROM group_member WHERE group_id = ?", g)).isZero();
        assertThat(count("SELECT count(*) FROM group_member_history WHERE group_id = ?", g)).isZero();
        assertThat(count("SELECT count(*) FROM invitation WHERE group_id = ?", g)).isZero();
        assertThat(count("SELECT count(*) FROM assignment_change WHERE space_id IN (?, ?)", s, s2)).isZero();
        assertThat(count("SELECT count(*) FROM space_deletion_request WHERE space_id IN (?, ?)", s, s2)).isZero();
    }

    @Test
    @DisplayName("PURGE-03: 따로 삭제한 스페이스만 30일 뒤 지우고, 그룹과 다른 스페이스는 남긴다")
    void purgeSpaceOnly() {
        as(alice, () -> deletionService.request(alice, s, new SpaceDto.DeletionReq(REAUTH_REQ)));
        as(bob, () -> {
            deletionService.approve(bob, s, new SpaceDto.DeletionReq(REAUTH_REQ));
            return null;
        });
        clock.advance(RETENTION);

        purgeService.purgeExpired();

        assertThat(count("SELECT count(*) FROM space WHERE id = ?", s)).isZero();
        assertThat(count("SELECT count(*) FROM assignment_change WHERE space_id = ?", s)).isZero();
        assertThat(count("SELECT count(*) FROM space_deletion_request WHERE space_id = ?", s)).isZero();
        assertThat(count("SELECT count(*) FROM groups WHERE id = ?", g)).isOne();
        assertThat(count("SELECT count(*) FROM space WHERE id = ?", s2)).isOne();
    }

    @Test
    @DisplayName("PURGE-04: 그룹을 복구하면 그룹 삭제 전 상태로 돌아오고, 먼저 따로 삭제한 스페이스는 삭제된 채로 남는다")
    void restoreGroup() throws Exception {
        as(alice, () -> deletionService.request(alice, s2, new SpaceDto.DeletionReq(REAUTH_REQ)));
        deleteGroup(alice, g, "G");
        clock.advance(Duration.ofDays(10));
        // 복구는 운영자가 DB에서 한다 (정책 2.6 제2항)
        jdbcTemplate.update("UPDATE groups SET deleted_at = NULL WHERE id = ?", g);

        get("/api/v1/groups/" + g, ALICE).andExpect(status().isOk());
        get("/api/v1/spaces/" + s, BOB).andExpect(status().isOk());
        get("/api/v1/spaces/" + s2, ALICE).andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("PURGE-05: 탈퇴자의 기록이 남은 그룹이 모두 영구 삭제되면 이름과 이메일을 지운다")
    void anonymizeWhenAllRecordsGone() {
        userService.withdraw(dave, PASSWORD, null);
        deleteGroup(alice, g, "G");
        clock.advance(RETENTION);

        purgeService.purgeExpired();

        Map<String, Object> row = user(dave);
        assertThat(row.get("name")).isEqualTo(GroupDto.WITHDRAWN_USER_NAME);
        assertThat(row.get("email")).isNotEqualTo(DAVE);
    }

    @Test
    @DisplayName("PURGE-06: 다른 그룹에 기록이 남아 있으면 이름과 이메일을 그대로 둔다")
    void keepWhileRecordsRemain() {
        long h = createGroup(carol, "H");
        join(h, carol, dave);
        userService.withdraw(dave, PASSWORD, null);
        deleteGroup(alice, g, "G");
        clock.advance(RETENTION);

        purgeService.purgeExpired();

        assertThat(user(dave).get("email")).isEqualTo(DAVE);
        assertThat(user(dave).get("name")).isEqualTo("데이브");
    }

    @Test
    @DisplayName("PURGE-06b: 그룹 기록이 없는 탈퇴자는 작업을 돌리면 익명화된다")
    void anonymizeWithoutRecords() {
        userService.withdraw(mallory, PASSWORD, null);

        purgeService.purgeExpired();

        assertThat(user(mallory).get("email")).isNotEqualTo(MALLORY);
    }

    @Test
    @DisplayName("PURGE-06c: 활성 사용자는 영향이 없다")
    void activeUsersUntouched() {
        purgeService.purgeExpired();

        assertThat(user(mallory).get("email")).isEqualTo(MALLORY);
        assertThat(user(mallory).get("name")).isEqualTo("맬러리");
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "UPDATE group_member_history SET type = 'LEFT'",
            "DELETE FROM group_member_history",
            "TRUNCATE group_member_history",
            "UPDATE assignment_change SET reason = 'forged'",
            "DELETE FROM assignment_change",
            "TRUNCATE assignment_change"})
    @DisplayName("GRP-14·SPC-18: 일반 트랜잭션에서 이력을 바꾸거나 지우면 DB 트리거가 거부한다")
    void historyIsAppendOnly(String sql) {
        assertThatThrownBy(() -> jdbcTemplate.execute(sql))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("append-only");
    }

    @Test
    @DisplayName("PURGE-07: 영구 삭제 표시(app.purge)를 켠 트랜잭션에서도 DELETE만 허용하고 UPDATE는 거부한다")
    void purgeFlagAllowsDeleteOnly() {
        assertThatThrownBy(() -> transactionTemplate.executeWithoutResult(tx -> {
            jdbcTemplate.queryForObject("SELECT set_config('app.purge', 'on', true)", String.class);
            jdbcTemplate.update("UPDATE assignment_change SET reason = 'forged'");
        })).isInstanceOf(DataIntegrityViolationException.class);

        transactionTemplate.executeWithoutResult(tx -> {
            jdbcTemplate.queryForObject("SELECT set_config('app.purge', 'on', true)", String.class);
            jdbcTemplate.update("DELETE FROM assignment_change WHERE space_id = ?", s);
        });
        assertThat(count("SELECT count(*) FROM assignment_change WHERE space_id = ?", s)).isZero();
    }

    private void deleteGroup(long ownerId, long groupId, String name) {
        as(ownerId, () -> {
            groupService.delete(ownerId, groupId, new GroupDto.DeleteReq(name, REAUTH_REQ));
            return null;
        });
    }

    private Map<String, Object> user(long userId) {
        return jdbcTemplate.queryForMap("SELECT name, email FROM users WHERE id = ?", userId);
    }
}
