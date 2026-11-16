package com.back.common;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.back.group.api.InvitationDto;
import com.back.space.api.SpaceDto;
import com.back.support.GroupTestSupport;
import com.back.support.QueryCounter;
import com.jayway.jsonpath.JsonPath;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * NFR-04: 목록 API의 SQL 수는 목록 건수와 관계없이 같다 (N+1 없음).
 * <p>
 * 목록 대상을 {@value #SMALL}건 추가한 뒤 한 번, {@value #LARGE}건이 되도록 더 추가한 뒤 한 번 조회해 실행된 SQL 수를 비교한다.
 * 응답 건수도 함께 늘었는지 확인해, 아무것도 조회하지 않아 통과하는 일을 막는다.
 * 세션 목록(NFR-04h)은 DB가 아니라 Redis(Spring Session)에서 읽으므로 이 측정 대상이 아니다.
 */
class ListQueryCountApiTest extends GroupTestSupport {

    private static final int SMALL = 2;
    private static final int LARGE = 12;

    @Autowired
    QueryCounter queryCounter;

    /** 목록 대상을 {@code count}건 더 만든다. */
    @FunctionalInterface
    private interface Seeder {
        void add(int count) throws Exception;
    }

    @Test
    @DisplayName("NFR-04a: 내 그룹 목록")
    void myGroups() throws Exception {
        assertConstantQueries("/api/v1/groups?size=20", ALICE, "$.items",
                count -> repeat(count, i -> createGroup(alice, "G" + i)));
    }

    @Test
    @DisplayName("NFR-04b: 그룹 멤버 목록 (업무용 전화번호 포함)")
    void members() throws Exception {
        assertConstantQueries("/api/v1/groups/" + g + "/members", ALICE, "$",
                count -> repeat(count, i -> join(g, alice, newUser("member", "010-0000-0000"))));
    }

    @Test
    @DisplayName("NFR-04c: 그룹 멤버 이력 (탈퇴한 사용자 포함)")
    void memberHistory() throws Exception {
        assertConstantQueries("/api/v1/groups/" + g + "/member-history", ALICE, "$",
                count -> repeat(count, i -> {
                    String email = "history" + sequence + "@example.com";
                    long user = createUser(email, "이력", null);
                    sequence++;
                    join(g, alice, user);
                    if (i % 2 == 0) {
                        post("/api/v1/users/me/withdrawal", email, body("\"password\":\"" + PASSWORD + "\""))
                                .andExpect(status().is2xxSuccessful());
                    }
                }));
    }

    @Test
    @DisplayName("NFR-04d: 그룹의 대기 초대 목록")
    void groupInvitations() throws Exception {
        assertConstantQueries("/api/v1/groups/" + g + "/invitations", ALICE, "$",
                count -> repeat(count, i -> as(alice, () -> invitationService.invite(alice, g,
                        new InvitationDto.CreateReq("invitee" + sequence++ + "@example.com")))));
    }

    @Test
    @DisplayName("NFR-04e: 내가 받은 초대 목록 (서로 다른 그룹)")
    void receivedInvitations() throws Exception {
        String erin = "erin@example.com";
        createUser(erin, "에린", null);
        assertConstantQueries("/api/v1/users/me/invitations", erin, "$",
                count -> repeat(count, i -> {
                    long group = createGroup(alice, "초대 그룹" + i);
                    as(alice, () -> invitationService.invite(alice, group, new InvitationDto.CreateReq(erin)));
                }));
    }

    @Test
    @DisplayName("NFR-04f (SPC-08b): 그룹의 스페이스 목록 (담당자 섞임)")
    void groupSpaces() throws Exception {
        assertConstantQueries("/api/v1/groups/" + g + "/spaces?page=1&size=20", ALICE, "$.content",
                count -> repeat(count, i -> {
                    long space = createSpace(alice, g, "S" + sequence++);
                    if (i % 2 == 0) {
                        assign(alice, space, i % 4 == 0 ? carol : dave);
                    }
                }));
    }

    @Test
    @DisplayName("NFR-04g: 스페이스 담당자 변경 이력 (이전·새 담당자, 실행자 이름)")
    void assignmentChanges() throws Exception {
        assertConstantQueries("/api/v1/spaces/" + s + "/assignment-changes", ALICE, "$",
                count -> repeat(count, i -> {
                    clock.advance(Duration.ofSeconds(1));
                    long to = forcedTurn++ % 2 == 0 ? carol : dave;
                    as(alice, () -> spaceService.forceReassign(alice, s,
                            new SpaceDto.ForcedReassignmentReq(to, "사유")));
                }));
    }

    @Test
    @DisplayName("NFR-04i: 카운터가 DB 연결에 붙어 SQL을 세고, reset()하면 0이 된다")
    void counterCountsSql() throws Exception {
        session(ALICE);
        queryCounter.reset();
        get("/api/v1/groups/" + g + "/members", ALICE).andExpect(status().isOk());
        assertThat(queryCounter.count()).isPositive();

        queryCounter.reset();
        assertThat(queryCounter.count()).isZero();
    }

    // --- 측정 ---

    private int sequence;
    private int forcedTurn;

    /**
     * {@value #SMALL}건을 추가하고 조회, {@value #LARGE}건이 되도록 더 추가하고 조회해 SQL 수가 같은지 본다.
     * 첫 조회는 로그인과 초기화 SQL을 빼려고 측정하지 않는다.
     */
    private void assertConstantQueries(String url, String email, String itemsPath, Seeder seeder) throws Exception {
        seeder.add(SMALL);
        get(url, email).andExpect(status().isOk());
        Measure small = measure(url, email, itemsPath);

        seeder.add(LARGE - SMALL);
        Measure large = measure(url, email, itemsPath);

        assertThat(large.items() - small.items()).as("%s: 응답 건수가 추가한 만큼 이상 늘었다", url)
                .isGreaterThanOrEqualTo(LARGE - SMALL);
        assertThat(small.queries()).as("%s: 카운터가 SQL을 센다", url).isPositive();
        assertThat(large.queries())
                .as("%s: %d건 추가 후 SQL %d회, %d건 추가 후 SQL %d회", url, SMALL, small.queries(), LARGE, large.queries())
                .isEqualTo(small.queries());
    }

    private record Measure(int queries, int items) {
    }

    private Measure measure(String url, String email, String itemsPath) throws Exception {
        queryCounter.reset();
        String body = get(url, email).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        int queries = queryCounter.count();
        List<?> items = JsonPath.read(body, itemsPath);
        return new Measure(queries, items.size());
    }

    @FunctionalInterface
    private interface IndexedAction {
        void run(int index) throws Exception;
    }

    private static void repeat(int count, IndexedAction action) throws Exception {
        for (int i = 0; i < count; i++) {
            action.run(i);
        }
    }

    private long newUser(String prefix, String workPhone) {
        return createUser(prefix + sequence++ + "@example.com", prefix, workPhone);
    }
}
