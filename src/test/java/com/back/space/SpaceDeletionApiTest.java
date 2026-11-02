package com.back.space;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.back.auth.api.AuthDto;
import com.back.common.error.BusinessException;
import com.back.common.error.ErrorCode;
import com.back.space.api.SpaceDto;
import com.back.space.application.SpaceDeletionService;
import com.back.support.GroupTestSupport;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.ResultActions;

class SpaceDeletionApiTest extends GroupTestSupport {

    @Autowired
    SpaceDeletionService deletionService;

    @Test
    @DisplayName("SDEL-01: 담당자가 있는 스페이스에 OWNER가 요청하면 7일짜리 대기 요청이 생기고 스페이스는 남는다")
    void ownerRequests() throws Exception {
        Instant now = clock.instant();

        request(s, ALICE)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.deleted").value(false))
                .andExpect(jsonPath("$.request.requestedBy.id").value(alice))
                .andExpect(jsonPath("$.request.expiresAt").value(now.plus(Duration.ofDays(7)).toString()));
        get("/api/v1/spaces/" + s, ALICE).andExpect(status().isOk());
    }

    @Test
    @DisplayName("SDEL-02: 담당자가 승인하면 스페이스가 삭제되고 요청은 APPROVED(승인자 기록)가 된다")
    void assigneeApproves() throws Exception {
        request(s, ALICE).andExpect(status().isOk());

        approve(s, BOB).andExpect(status().isNoContent());

        assertThat(count("SELECT count(*) FROM space WHERE id = ? AND deleted_at IS NOT NULL", s)).isOne();
        Map<String, Object> row = lastRequest(s);
        assertThat(row.get("status")).isEqualTo("APPROVED");
        assertThat(row.get("decided_by")).isEqualTo(bob);
        get("/api/v1/spaces/" + s, ALICE).andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("SDEL-03: 담당자가 요청하고 OWNER가 승인해도 삭제된다")
    void assigneeRequestsOwnerApproves() throws Exception {
        request(s, BOB).andExpect(status().isOk()).andExpect(jsonPath("$.deleted").value(false));
        approve(s, ALICE).andExpect(status().isNoContent());
        assertThat(count("SELECT count(*) FROM space WHERE id = ? AND deleted_at IS NOT NULL", s)).isOne();
    }

    @Test
    @DisplayName("SDEL-04: 요청한 사람은 스스로 승인할 수 없다 (409)")
    void selfApproval() throws Exception {
        request(s, ALICE).andExpect(status().isOk());
        approve(s, ALICE)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("SELF_APPROVAL_NOT_ALLOWED"));
    }

    @Test
    @DisplayName("SDEL-05: 담당자가 없는 스페이스는 OWNER의 요청만으로 바로 삭제되고, 요청 행은 APPROVED(요청자=승인자)")
    void vacantSpaceDeletedAlone() throws Exception {
        request(s2, ALICE).andExpect(status().isOk()).andExpect(jsonPath("$.deleted").value(true));

        assertThat(count("SELECT count(*) FROM space WHERE id = ? AND deleted_at IS NOT NULL", s2)).isOne();
        Map<String, Object> row = lastRequest(s2);
        assertThat(row.get("status")).isEqualTo("APPROVED");
        assertThat(row.get("requester_id")).isEqualTo(alice);
        assertThat(row.get("decided_by")).isEqualTo(alice);
    }

    @Test
    @DisplayName("SDEL-06: OWNER가 담당자를 겸하면 바로 삭제된다")
    void ownerIsAssignee() throws Exception {
        long s3 = createSpace(alice, g, "S3");
        assign(alice, s3, alice);

        request(s3, ALICE).andExpect(status().isOk()).andExpect(jsonPath("$.deleted").value(true));
    }

    @Test
    @DisplayName("SDEL-07: OWNER·담당자가 아니면 요청·승인·거절할 수 없다 (403)")
    void forbidden() throws Exception {
        request(s, DAVE).andExpect(status().isForbidden());
        request(s, MALLORY).andExpect(status().isForbidden());
        request(s, ALICE).andExpect(status().isOk());
        approve(s, CAROL).andExpect(status().isForbidden());
        post("/api/v1/spaces/" + s + "/deletion-request/reject", CAROL, body("")).andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("SDEL-08: 상대방은 사유와 함께 또는 사유 없이 거절할 수 있고, 스페이스는 남는다")
    void reject() throws Exception {
        request(s, ALICE).andExpect(status().isOk());
        post("/api/v1/spaces/" + s + "/deletion-request/reject", BOB, body("\"reason\":\"아직 필요\""))
                .andExpect(status().isNoContent());
        Map<String, Object> row = lastRequest(s);
        assertThat(row.get("status")).isEqualTo("REJECTED");
        assertThat(row.get("reason")).isEqualTo("아직 필요");
        assertThat(row.get("decided_by")).isEqualTo(bob);
        get("/api/v1/spaces/" + s, ALICE).andExpect(status().isOk());

        request(s, ALICE).andExpect(status().isOk());
        post("/api/v1/spaces/" + s + "/deletion-request/reject", BOB, null).andExpect(status().isNoContent());
        assertThat(lastRequest(s).get("reason")).isNull();
    }

    @Test
    @DisplayName("SDEL-08b: 거절 사유가 500자를 넘으면 400")
    void rejectReasonTooLong() throws Exception {
        request(s, ALICE).andExpect(status().isOk());
        post("/api/v1/spaces/" + s + "/deletion-request/reject", BOB, body("\"reason\":\"" + "가".repeat(501) + "\""))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("SDEL-09: 요청한 사람만 철회할 수 있다")
    void withdraw() throws Exception {
        request(s, ALICE).andExpect(status().isOk());

        delete("/api/v1/spaces/" + s + "/deletion-request", BOB).andExpect(status().isForbidden());
        delete("/api/v1/spaces/" + s + "/deletion-request", ALICE).andExpect(status().isNoContent());
        assertThat(lastRequest(s).get("status")).isEqualTo("WITHDRAWN");
    }

    @Test
    @DisplayName("SDEL-10: 만료 1초 전에는 승인할 수 있다")
    void approveBeforeExpiry() throws Exception {
        request(s, ALICE).andExpect(status().isOk());
        clock.advance(Duration.ofDays(7).minusSeconds(1));
        approve(s, BOB).andExpect(status().isNoContent());
    }

    @Test
    @DisplayName("SDEL-10: 정확히 7일이 지나면 만료되어 승인할 수 없다 (409)")
    void approveAtExpiry() throws Exception {
        request(s, ALICE).andExpect(status().isOk());
        clock.advance(Duration.ofDays(7));
        approve(s, BOB)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("DELETION_REQUEST_NOT_PENDING"));
        get("/api/v1/spaces/" + s + "/deletion-request", ALICE).andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("SDEL-10b: 만료된 요청은 대기 중으로 세지 않아 새로 요청할 수 있다")
    void requestAfterExpiry() throws Exception {
        request(s, ALICE).andExpect(status().isOk());
        clock.advance(Duration.ofDays(7));
        request(s, ALICE).andExpect(status().isOk()).andExpect(jsonPath("$.deleted").value(false));
        assertThat(count("SELECT count(*) FROM space_deletion_request WHERE space_id = ? AND status = 'EXPIRED'", s))
                .isOne();
    }

    @Test
    @DisplayName("SDEL-11: 대기 중인 요청이 있으면 다시 요청할 수 없다 (409)")
    void requestExists() throws Exception {
        request(s, ALICE).andExpect(status().isOk());
        request(s, BOB)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("DELETION_REQUEST_EXISTS"));
    }

    @Test
    @DisplayName("SDEL-12: 긴급 재지정으로 담당자가 바뀌면 대기 요청이 무효가 되어 새 담당자가 승인할 수 없다")
    void invalidatedByForcedReassignment() throws Exception {
        request(s, ALICE).andExpect(status().isOk());
        post("/api/v1/spaces/" + s + "/forced-reassignment", ALICE,
                body("\"userId\":" + carol + ",\"reason\":\"퇴사\"")).andExpect(status().isOk());

        assertThat(lastRequest(s).get("status")).isEqualTo("INVALIDATED");
        approve(s, CAROL)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("DELETION_REQUEST_NOT_PENDING"));
    }

    @Test
    @DisplayName("SDEL-12b: OWNER가 바뀌면 대기 요청이 무효가 된다")
    void invalidatedByOwnerTransfer() throws Exception {
        request(s, BOB).andExpect(status().isOk());
        post("/api/v1/groups/" + g + "/owner-transfer", ALICE, body("\"userId\":" + dave + "," + REAUTH))
                .andExpect(status().isOk());

        assertThat(lastRequest(s).get("status")).isEqualTo("INVALIDATED");
        approve(s, DAVE).andExpect(status().isConflict());
    }

    @Test
    @DisplayName("SDEL-12c: 스페이스 이름을 바꿔도 대기 요청은 그대로다")
    void renameKeepsRequest() throws Exception {
        request(s, ALICE).andExpect(status().isOk());
        patch("/api/v1/spaces/" + s, ALICE, body("\"name\":\"새 이름\"")).andExpect(status().isOk());
        assertThat(lastRequest(s).get("status")).isEqualTo("PENDING");
    }

    @Test
    @DisplayName("SDEL-15: 요청·승인에 본인 재확인이 없거나 틀리면 400 REAUTH_FAILED, 상태는 그대로다")
    void reauthRequired() throws Exception {
        post("/api/v1/spaces/" + s + "/deletion-request", ALICE, body(""))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("REAUTH_FAILED"));
        assertThat(count("SELECT count(*) FROM space_deletion_request WHERE space_id = ?", s)).isZero();

        request(s, ALICE).andExpect(status().isOk());
        post("/api/v1/spaces/" + s + "/deletion-request/approve", BOB, body("\"reauth\":{\"password\":\"wrong\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("REAUTH_FAILED"));
        assertThat(lastRequest(s).get("status")).isEqualTo("PENDING");
        assertThat(count("SELECT count(*) FROM space WHERE id = ? AND deleted_at IS NULL", s)).isOne();
    }

    @Test
    @DisplayName("SDEL-16: 담당자가 부재하면 긴급 재지정으로 비운 뒤 OWNER가 혼자 삭제한다")
    void deleteAfterVacating() throws Exception {
        post("/api/v1/spaces/" + s + "/forced-reassignment", ALICE, body("\"reason\":\"연락 두절\""))
                .andExpect(status().isOk());
        request(s, ALICE).andExpect(status().isOk()).andExpect(jsonPath("$.deleted").value(true));
    }

    @Test
    @DisplayName("SDEL-17: 대기 요청은 OWNER와 담당자가 조회하고, 없으면 404, 관계없는 멤버는 403")
    void viewPending() throws Exception {
        get("/api/v1/spaces/" + s + "/deletion-request", ALICE).andExpect(status().isNotFound());
        request(s, ALICE).andExpect(status().isOk());

        get("/api/v1/spaces/" + s + "/deletion-request", BOB)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.requestedBy.name").value("앨리스"))
                .andExpect(jsonPath("$.requestedAt").exists())
                .andExpect(jsonPath("$.expiresAt").exists());
        get("/api/v1/spaces/" + s + "/deletion-request", DAVE).andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("SDEL-18: OWNER와 담당자가 동시에 요청하면 한 건만 성공하고 다른 쪽은 409")
    void concurrentRequests() throws Exception {
        SpaceDto.DeletionReq req = new SpaceDto.DeletionReq(new AuthDto.ReauthReq(PASSWORD, null));
        List<Callable<SpaceDto.DeletionResultRes>> tasks = List.of(
                () -> as(alice, () -> deletionService.request(alice, s, req)),
                () -> as(bob, () -> deletionService.request(bob, s, req)));

        List<ErrorCode> failures = new ArrayList<>();
        int succeeded = 0;
        try (ExecutorService executor = Executors.newFixedThreadPool(2)) {
            for (Future<SpaceDto.DeletionResultRes> result : executor.invokeAll(tasks)) {
                try {
                    result.get();
                    succeeded++;
                } catch (ExecutionException e) {
                    failures.add(((BusinessException) e.getCause()).getErrorCode());
                }
            }
        }

        assertThat(succeeded).isOne();
        assertThat(failures).containsExactly(ErrorCode.DELETION_REQUEST_EXISTS);
        assertThat(count("SELECT count(*) FROM space_deletion_request WHERE space_id = ? AND status = 'PENDING'", s))
                .isOne();
    }

    @Test
    @DisplayName("SDEL: 인계가 진행 중인 스페이스에는 삭제를 요청할 수 없다 (409)")
    void handoverInProgress() throws Exception {
        handoverStatus.start(s, g, bob, carol);
        request(s, ALICE)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("HANDOVER_IN_PROGRESS"));
    }

    private ResultActions request(long spaceId, String email) throws Exception {
        return post("/api/v1/spaces/" + spaceId + "/deletion-request", email, body(REAUTH));
    }

    private ResultActions approve(long spaceId, String email) throws Exception {
        return post("/api/v1/spaces/" + spaceId + "/deletion-request/approve", email, body(REAUTH));
    }

    private Map<String, Object> lastRequest(long spaceId) {
        return jdbcTemplate.queryForMap(
                "SELECT * FROM space_deletion_request WHERE space_id = ? ORDER BY id DESC LIMIT 1", spaceId);
    }
}
