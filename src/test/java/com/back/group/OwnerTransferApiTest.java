package com.back.group;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.back.support.GroupTestSupport;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;

class OwnerTransferApiTest extends GroupTestSupport {

    private String transferUrl() {
        return "/api/v1/groups/" + g + "/owner-transfer";
    }

    @Test
    @DisplayName("GRP-08: 양도하면 OWNER가 바로 바뀌고 생성자 기록은 그대로다. 이전 OWNER는 멤버로 남는다")
    void transfer() throws Exception {
        post(transferUrl(), ALICE, body("\"userId\":" + dave + "," + REAUTH))
                .andExpect(status().isOk());

        Map<String, Object> row = jdbcTemplate.queryForMap("SELECT owner_id, created_by FROM groups WHERE id = ?", g);
        assertThat(row.get("owner_id")).isEqualTo(dave);
        assertThat(row.get("created_by")).isEqualTo(alice);
        assertThat(count("SELECT count(*) FROM group_member WHERE group_id = ? AND user_id = ?", g, alice)).isOne();

        patch("/api/v1/groups/" + g, ALICE, body("\"name\":\"X\"")).andExpect(status().isForbidden());
        patch("/api/v1/groups/" + g, DAVE, body("\"name\":\"X\"")).andExpect(status().isOk());
    }

    @Test
    @DisplayName("GRP-08b: 본인 재확인 값이 없거나 틀리면 400 REAUTH_FAILED, OWNER는 그대로다. 틀린 값만 로그인 실패로 센다")
    void reauthRequired() throws Exception {
        post(transferUrl(), ALICE, body("\"userId\":" + dave))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("REAUTH_FAILED"));
        post(transferUrl(), ALICE, body("\"userId\":" + dave + ",\"reauth\":{\"password\":\"wrong-password\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("REAUTH_FAILED"));

        assertThat(ownerId()).isEqualTo(alice);
        // 틀린 값 1회만 실패로 센다(값이 없는 요청은 세지 않는다). 4회 더 틀리면 잠긴다 (계정당 5회, 정책 7.2)
        for (int i = 0; i < 3; i++) {
            post(transferUrl(), ALICE, body("\"userId\":" + dave + ",\"reauth\":{\"password\":\"wrong\"}"))
                    .andExpect(status().isBadRequest());
        }
        post(transferUrl(), ALICE, body("\"userId\":" + dave + ",\"reauth\":{\"password\":\"wrong\"}"))
                .andExpect(status().isLocked())
                .andExpect(jsonPath("$.code").value("ACCOUNT_LOCKED"));
    }

    @Test
    @DisplayName("GRP-08b: 재확인 값이 아예 없으면 400 REAUTH_FAILED이지만 로그인 실패 횟수에는 넣지 않는다")
    void missingReauthNotCounted() throws Exception {
        for (int i = 0; i < 6; i++) {
            post(transferUrl(), ALICE, body("\"userId\":" + dave))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("REAUTH_FAILED"));
            post(transferUrl(), ALICE, body("\"userId\":" + dave + ",\"reauth\":{}"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("REAUTH_FAILED"));
        }

        login(ALICE, PASSWORD).andExpect(status().isOk());
        post(transferUrl(), ALICE, body("\"userId\":" + dave + "," + REAUTH)).andExpect(status().isOk());
    }

    @Test
    @DisplayName("GRP-08c: OWNER가 아니면 양도할 수 없다 (403)")
    void onlyOwner() throws Exception {
        post(transferUrl(), BOB, body("\"userId\":" + dave + "," + REAUTH)).andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("GRP-08d: 자기 자신에게는 양도할 수 없다 (400)")
    void notToSelf() throws Exception {
        post(transferUrl(), ALICE, body("\"userId\":" + alice + "," + REAUTH))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_INPUT"));
    }

    @Test
    @DisplayName("GRP-09: 그룹 멤버가 아닌 사람에게는 양도할 수 없다")
    void onlyToMember() throws Exception {
        post(transferUrl(), ALICE, body("\"userId\":" + mallory + "," + REAUTH))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("NOT_GROUP_MEMBER"));
        assertThat(ownerId()).isEqualTo(alice);
    }

    @Test
    @DisplayName("GRP-08: 받을 사람을 빠뜨리면 400")
    void userIdRequired() throws Exception {
        post(transferUrl(), ALICE, body(REAUTH)).andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("GRP-12: DB에 직접 비멤버를 OWNER로 넣으면 FK 위반")
    void ownerMustBeMemberInDb() {
        assertThatThrownBy(() -> jdbcTemplate.update("UPDATE groups SET owner_id = ? WHERE id = ?", mallory, g))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbcTemplate.update("UPDATE space SET assignee_id = ? WHERE id = ?", mallory, s2))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    private long ownerId() {
        return count("SELECT owner_id FROM groups WHERE id = ?", g);
    }
}
