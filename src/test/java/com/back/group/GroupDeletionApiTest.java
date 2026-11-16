package com.back.group;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.back.auth.api.AuthDto;
import com.back.group.api.InvitationDto;
import com.back.space.api.SpaceDto;
import com.back.space.application.SpaceDeletionService;
import com.back.support.GroupTestSupport;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;

class GroupDeletionApiTest extends GroupTestSupport {

    private static final String ERIN = "erin@example.com";

    @Autowired
    SpaceDeletionService spaceDeletionService;

    private String deletionUrl() {
        return "/api/v1/groups/" + g + "/deletion";
    }

    @Test
    @DisplayName("GRP-05: 삭제하면 그룹 행만 soft delete되고, 그룹·스페이스는 OWNER에게도 404, 목록에서도 사라진다")
    void delete() throws Exception {
        createUser(ERIN, "에린", null);
        invitationService.invite(alice, g, new InvitationDto.CreateReq(ERIN));
        requestSpaceDeletion();

        post(deletionUrl(), ALICE, body("\"confirmName\":\"G\"," + REAUTH)).andExpect(status().isNoContent());

        assertThat(count("SELECT count(*) FROM groups WHERE id = ? AND deleted_at IS NOT NULL", g)).isOne();
        assertThat(count("SELECT count(*) FROM space WHERE group_id = ? AND deleted_at IS NULL", g)).isEqualTo(2);
        assertThat(count("SELECT count(*) FROM invitation WHERE group_id = ? AND status = 'PENDING'", g)).isZero();
        assertThat(count("SELECT count(*) FROM space_deletion_request WHERE status = 'PENDING'")).isZero();

        get("/api/v1/groups/" + g, ALICE).andExpect(status().isNotFound());
        get("/api/v1/spaces/" + s, ALICE).andExpect(status().isNotFound());
        get("/api/v1/spaces/" + s, BOB).andExpect(status().isNotFound());
        get("/api/v1/groups", ALICE).andExpect(jsonPath("$.items", hasSize(0)));
    }

    @ParameterizedTest
    @ValueSource(strings = {"g", "G2", ""})
    @DisplayName("GRP-05a: 확인 이름이 그룹 이름과 정확히 같지 않으면 400 GROUP_NAME_MISMATCH")
    void nameMismatch(String confirmName) throws Exception {
        post(deletionUrl(), ALICE, body("\"confirmName\":\"" + confirmName + "\"," + REAUTH))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("GROUP_NAME_MISMATCH"));
        assertNotDeleted();
    }

    @Test
    @DisplayName("GRP-05b: 확인 이름의 앞뒤 공백은 무시한다")
    void trimsConfirmName() throws Exception {
        post(deletionUrl(), ALICE, body("\"confirmName\":\" G \"," + REAUTH)).andExpect(status().isNoContent());
    }

    @Test
    @DisplayName("GRP-05c: 본인 재확인 값이 없거나 틀리면 400 REAUTH_FAILED")
    void reauthRequired() throws Exception {
        post(deletionUrl(), ALICE, body("\"confirmName\":\"G\""))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("REAUTH_FAILED"));
        post(deletionUrl(), ALICE, body("\"confirmName\":\"G\",\"reauth\":{\"password\":\"wrong-password\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("REAUTH_FAILED"));
        assertNotDeleted();
    }

    @Test
    @DisplayName("GRP-05d: OWNER가 아니면 삭제할 수 없다 (403)")
    void onlyOwner() throws Exception {
        post(deletionUrl(), BOB, body("\"confirmName\":\"G\"," + REAUTH)).andExpect(status().isForbidden());
        assertNotDeleted();
    }

    @Test
    @DisplayName("GRP-05e: 삭제된 그룹의 대기 초대는 수락할 수 없고(409), 스페이스 삭제 요청은 승인할 수 없다(404)")
    void pendingWorkInvalidated() throws Exception {
        long erin = createUser(ERIN, "에린", null);
        long invitationId = invitationService.invite(alice, g, new InvitationDto.CreateReq(ERIN)).id();
        requestSpaceDeletion();

        post(deletionUrl(), ALICE, body("\"confirmName\":\"G\"," + REAUTH)).andExpect(status().isNoContent());

        post("/api/v1/invitations/" + invitationId + "/accept", ERIN, null)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("INVITATION_NOT_PENDING"));
        post("/api/v1/spaces/" + s + "/deletion-request/approve", BOB, body(REAUTH))
                .andExpect(status().isNotFound());
        assertThat(count("SELECT count(*) FROM group_member WHERE group_id = ? AND user_id = ?", g, erin)).isZero();
    }

    @Test
    @DisplayName("GRP-05f: 이미 삭제한 그룹을 다시 삭제하면 404")
    void deleteTwice() throws Exception {
        post(deletionUrl(), ALICE, body("\"confirmName\":\"G\"," + REAUTH)).andExpect(status().isNoContent());
        post(deletionUrl(), ALICE, body("\"confirmName\":\"G\"," + REAUTH)).andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("GRP-06: 인계가 진행 중인 스페이스가 있으면 409 HANDOVER_IN_PROGRESS, 아무것도 바뀌지 않는다")
    void handoverInProgress() throws Exception {
        handoverStatus.start(s, g, bob, carol);

        post(deletionUrl(), ALICE, body("\"confirmName\":\"G\"," + REAUTH))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("HANDOVER_IN_PROGRESS"));
        assertNotDeleted();
    }

    private void requestSpaceDeletion() {
        spaceDeletionService.request(alice, s, new SpaceDto.DeletionReq(new AuthDto.ReauthReq(PASSWORD, null)));
    }

    private void assertNotDeleted() {
        assertThat(count("SELECT count(*) FROM groups WHERE id = ? AND deleted_at IS NULL", g)).isOne();
    }
}
