package com.back.group;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.back.group.api.InvitationDto;
import com.back.support.GroupTestSupport;
import com.jayway.jsonpath.JsonPath;
import jakarta.mail.internet.MimeMessage;
import java.time.Duration;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

/**
 * 초대 (정책 2.4). 픽스처의 carol·dave는 이미 멤버이므로, 초대받는 사람은 G 밖의 erin으로 둔다.
 */
class InvitationApiTest extends GroupTestSupport {

    private static final String ERIN = "erin@example.com";

    private long erin;

    @BeforeEach
    void setUpInvitee() {
        erin = createUser(ERIN, "에린", null);
    }

    @Test
    @DisplayName("INV-01: 초대하면 이메일을 소문자로 저장하고 7일 뒤 만료로 둔다. 커밋 후 받은 초대 화면 링크가 든 메일 1통")
    void invite() throws Exception {
        Instant now = clock.instant();

        invite(ALICE, "Erin@Example.com")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.email").value(ERIN))
                .andExpect(jsonPath("$.expiresAt").value(now.plus(Duration.ofDays(7)).toString()));

        List<MimeMessage> mails = mailsTo(ERIN);
        assertThat(mails).hasSize(1);
        assertThat(mailBody(mails.getFirst())).contains("http://localhost:3000/invitations");
    }

    @Test
    @DisplayName("INV-01b: 초대한 트랜잭션이 롤백되면 메일을 보내지 않는다")
    void noMailOnRollback() {
        transactionTemplate.executeWithoutResult(tx -> {
            invitationService.invite(alice, g, new InvitationDto.CreateReq(ERIN));
            tx.setRollbackOnly();
        });

        assertThat(mailsTo(ERIN)).isEmpty();
        assertThat(count("SELECT count(*) FROM invitation WHERE email = ?", ERIN)).isZero();
    }

    @Test
    @DisplayName("INV-02: 미가입 이메일도 초대할 수 있다. 메일은 가입 화면으로 안내하고, 가입·인증 후 받은 초대에 보인다")
    void inviteUnregistered() throws Exception {
        String newcomer = "new@example.com";
        invite(ALICE, newcomer).andExpect(status().isCreated());
        assertThat(mailBody(mailsTo(newcomer).getFirst())).contains("http://localhost:3000/signup");

        signup(newcomer, PASSWORD).andExpect(status().isCreated());
        mockMvc.perform(MockMvcRequestBuilders.post("/api/v1/auth/email-verification").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json("token", lastMailToken(newcomer))))
                .andExpect(status().isOk());

        get("/api/v1/users/me/invitations", newcomer)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].groupName").value("G"));
    }

    @Test
    @DisplayName("INV-03: 받은 초대 목록에는 대기 중인 초대만 그룹 이름·초대한 사람과 함께 나온다")
    void receivedList() throws Exception {
        long h = createGroup(alice, "H");
        long i = createGroup(carol, "I");
        long j = createGroup(dave, "J");
        inviteBy(alice, g);
        inviteBy(alice, h);
        long expired = inviteBy(carol, i);
        expire(expired);
        long canceled = inviteBy(dave, j);
        invitationService.cancel(dave, j, canceled);

        get("/api/v1/users/me/invitations", ERIN)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].groupName", containsInAnyOrder("G", "H")))
                .andExpect(jsonPath("$[0].inviterName").value("앨리스"))
                .andExpect(jsonPath("$[0].id").exists())
                .andExpect(jsonPath("$[0].expiresAt").exists());
    }

    @Test
    @DisplayName("INV-04: 수락하면 멤버가 되고, 본인이 실행자인 JOINED 이력이 남고, 초대는 ACCEPTED가 된다")
    void accept() throws Exception {
        long id = inviteBy(alice, g);

        accept(ERIN, id)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(g))
                .andExpect(jsonPath("$.name").value("G"));

        assertThat(count("SELECT count(*) FROM group_member WHERE group_id = ? AND user_id = ?", g, erin)).isOne();
        assertThat(count("""
                SELECT count(*) FROM group_member_history
                WHERE group_id = ? AND user_id = ? AND type = 'JOINED' AND actor_id = ?
                """, g, erin, erin)).isOne();
        Map<String, Object> row = jdbcTemplate.queryForMap("SELECT status, responded_at FROM invitation WHERE id = ?", id);
        assertThat(row.get("status")).isEqualTo("ACCEPTED");
        assertThat(row.get("responded_at")).isNotNull();
    }

    @Test
    @DisplayName("INV-05: 남의 초대는 수락하거나 거절할 수 없고, 있는지도 알려 주지 않는다 (404)")
    void othersInvitation() throws Exception {
        long id = inviteBy(alice, g);

        accept(MALLORY, id).andExpect(status().isNotFound());
        post("/api/v1/invitations/" + id + "/decline", MALLORY, null).andExpect(status().isNotFound());
        assertThat(invitationStatus(id)).isEqualTo("PENDING");
    }

    @Test
    @DisplayName("INV-05b: 없는 초대를 수락하면 404")
    void unknownInvitation() throws Exception {
        accept(ERIN, 999_999).andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("INV-06: 만료 1초 전에는 수락할 수 있다")
    void acceptBeforeExpiry() throws Exception {
        long id = inviteBy(alice, g);
        clock.advance(Duration.ofDays(7).minusSeconds(1));

        accept(ERIN, id).andExpect(status().isOk());
    }

    @Test
    @DisplayName("INV-06b: 정확히 7일이 지나면 만료되어 409이고, 목록에서도 빠진다")
    void acceptAtExpiry() throws Exception {
        long id = inviteBy(alice, g);
        clock.advance(Duration.ofDays(7));

        accept(ERIN, id)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("INVITATION_NOT_PENDING"));
        get("/api/v1/users/me/invitations", ERIN).andExpect(jsonPath("$", hasSize(0)));
        get("/api/v1/groups/" + g + "/invitations", ALICE).andExpect(jsonPath("$", hasSize(0)));
    }

    @Test
    @DisplayName("INV-07: 거절하면 멤버가 되지 않고 DECLINED가 된다. 다시 초대할 수 있다")
    void decline() throws Exception {
        long id = inviteBy(alice, g);

        post("/api/v1/invitations/" + id + "/decline", ERIN, null).andExpect(status().isNoContent());

        assertThat(invitationStatus(id)).isEqualTo("DECLINED");
        assertThat(count("SELECT count(*) FROM group_member WHERE group_id = ? AND user_id = ?", g, erin)).isZero();
        invite(ALICE, ERIN).andExpect(status().isCreated());
    }

    @Test
    @DisplayName("INV-08: OWNER가 취소한 초대는 수락할 수 없다")
    void cancel() throws Exception {
        long id = inviteBy(alice, g);

        delete("/api/v1/groups/" + g + "/invitations/" + id, ALICE).andExpect(status().isNoContent());
        accept(ERIN, id)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("INVITATION_NOT_PENDING"));
        assertThat(invitationStatus(id)).isEqualTo("CANCELED");
    }

    @Test
    @DisplayName("INV-08b: 이미 수락된 초대는 취소할 수 없다 (409)")
    void cancelAccepted() throws Exception {
        long id = inviteBy(alice, g);
        invitationService.accept(erin, id);

        delete("/api/v1/groups/" + g + "/invitations/" + id, ALICE)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("INVITATION_NOT_PENDING"));
    }

    @Test
    @DisplayName("INV-08: 다른 그룹의 초대 id로 취소하면 404")
    void cancelOtherGroupsInvitation() throws Exception {
        long h = createGroup(alice, "H");
        long id = inviteBy(alice, h);

        delete("/api/v1/groups/" + g + "/invitations/" + id, ALICE).andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("INV-09: 같은 이메일을 다시 초대하면 이전 초대는 REPLACED가 되고 새 초대만 대기 중이다")
    void reinviteReplaces() throws Exception {
        long first = inviteBy(alice, g);
        long second = inviteBy(alice, g);

        assertThat(invitationStatus(first)).isEqualTo("REPLACED");
        assertThat(invitationStatus(second)).isEqualTo("PENDING");
        accept(ERIN, first).andExpect(status().isConflict());
        accept(ERIN, second).andExpect(status().isOk());
    }

    @Test
    @DisplayName("INV-09b: 같은 이메일을 동시에 두 번 초대해도 대기 중인 초대는 1건만 남는다")
    void concurrentInvites() throws Exception {
        Callable<Object> task = () -> invitationService.invite(alice, g, new InvitationDto.CreateReq(ERIN));
        try (ExecutorService executor = Executors.newFixedThreadPool(2)) {
            List<Future<Object>> results = executor.invokeAll(List.of(task, task));
            for (Future<Object> result : results) {
                result.get();
            }
        }

        assertThat(count("SELECT count(*) FROM invitation WHERE group_id = ? AND email = ? AND status = 'PENDING'",
                g, ERIN)).isOne();
    }

    @Test
    @DisplayName("INV-10: 이미 멤버인 사람은 초대할 수 없다 (409)")
    void alreadyMember() throws Exception {
        invite(ALICE, DAVE)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ALREADY_GROUP_MEMBER"));
    }

    @Test
    @DisplayName("INV-10b: 여러 그룹의 초대는 서로 독립적으로 수락한다")
    void independentGroups() throws Exception {
        long h = createGroup(alice, "H");
        long toG = inviteBy(alice, g);
        long toH = inviteBy(alice, h);

        accept(ERIN, toG).andExpect(status().isOk());
        accept(ERIN, toH).andExpect(status().isOk());
    }

    @Test
    @DisplayName("INV-11: OWNER가 아니면 초대·대기 목록·취소를 할 수 없다 (403)")
    void onlyOwner() throws Exception {
        long id = inviteBy(alice, g);

        invite(BOB, ERIN).andExpect(status().isForbidden());
        get("/api/v1/groups/" + g + "/invitations", BOB).andExpect(status().isForbidden());
        delete("/api/v1/groups/" + g + "/invitations/" + id, BOB).andExpect(status().isForbidden());
    }

    @ParameterizedTest
    @ValueSource(strings = {"abc", ""})
    @DisplayName("INV-12: 이메일 형식이 아니면 400")
    void invalidEmail(String email) throws Exception {
        invite(ALICE, email)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_INPUT"));
    }

    @Test
    @DisplayName("INV-13: 가입한 이메일과 미가입 이메일의 초대 응답은 구조가 같아 가입 여부를 드러내지 않는다")
    void responseDoesNotRevealRegistration() throws Exception {
        String registered = invite(ALICE, ERIN).andReturn().getResponse().getContentAsString();
        String unregistered = invite(ALICE, "nobody@example.com").andReturn().getResponse().getContentAsString();

        Map<String, Object> a = JsonPath.read(registered, "$");
        Map<String, Object> b = JsonPath.read(unregistered, "$");
        assertThat(a.keySet()).containsExactlyInAnyOrder("id", "email", "expiresAt").isEqualTo(b.keySet());
    }

    @Test
    @DisplayName("INV-14: 그룹이 삭제되면 대기 초대는 수락할 수 없고(409) 받은 목록에서도 빠진다")
    void groupDeleted() throws Exception {
        long id = inviteBy(alice, g);
        post("/api/v1/groups/" + g + "/deletion", ALICE, body("\"confirmName\":\"G\"," + REAUTH))
                .andExpect(status().isNoContent());

        accept(ERIN, id).andExpect(status().isConflict());
        get("/api/v1/users/me/invitations", ERIN).andExpect(jsonPath("$", hasSize(0)));
    }

    @Test
    @DisplayName("INV-15: 그룹의 초대 목록에는 대기 중인 초대만 나온다")
    void groupPendingList() throws Exception {
        long frank = createUser("frank@example.com", "프랭크", null);
        createUser("grace@example.com", "그레이스", null);
        inviteBy(alice, g);
        invitationService.invite(alice, g, new InvitationDto.CreateReq("grace@example.com"));
        long accepted = invitationService.invite(alice, g, new InvitationDto.CreateReq("frank@example.com")).id();
        invitationService.accept(frank, accepted);
        long expired = invitationService.invite(alice, g, new InvitationDto.CreateReq("old@example.com")).id();
        expire(expired);

        get("/api/v1/groups/" + g + "/invitations", ALICE)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].email", contains(ERIN, "grace@example.com")));
    }

    private ResultActions invite(String owner, String email) throws Exception {
        return post("/api/v1/groups/" + g + "/invitations", owner, body("\"email\":\"" + email + "\""));
    }

    private ResultActions accept(String email, long id) throws Exception {
        return post("/api/v1/invitations/" + id + "/accept", email, null);
    }

    private long inviteBy(long ownerId, long groupId) {
        return invitationService.invite(ownerId, groupId, new InvitationDto.CreateReq(ERIN)).id();
    }

    /** 테스트 시계 기준으로 만료시킨다. */
    private void expire(long invitationId) {
        jdbcTemplate.update("UPDATE invitation SET expires_at = ? WHERE id = ?",
                Timestamp.from(clock.instant().minusSeconds(1)), invitationId);
    }

    private String invitationStatus(long invitationId) {
        return jdbcTemplate.queryForObject("SELECT status FROM invitation WHERE id = ?", String.class, invitationId);
    }
}
