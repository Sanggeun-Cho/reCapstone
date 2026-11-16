package com.back.space;

import static org.assertj.core.api.Assertions.assertThat;

import com.back.auth.api.AuthDto;
import com.back.common.error.BusinessException;
import com.back.common.error.ErrorCode;
import com.back.group.api.GroupDto;
import com.back.space.api.SpaceDto;
import com.back.space.application.SpaceDeletionService;
import com.back.support.GroupTestSupport;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * 그룹 쓰기와 스페이스 쓰기가 같은 순서(그룹 행 → 스페이스 행)로 잠가 서로를 기다리는지.
 * 경합은 매번 일어나지 않으므로 여러 번 반복한다.
 */
class LockOrderConcurrencyTest extends GroupTestSupport {

    private static final int ROUNDS = 5;
    private static final AuthDto.ReauthReq REAUTH_REQ = new AuthDto.ReauthReq(PASSWORD, null);

    @Autowired
    SpaceDeletionService deletionService;

    @Test
    @DisplayName("같은 멤버의 제거와 담당자 지정이 동시에 오면 한쪽만 성공하고 다른 쪽은 업무 오류(500 아님)로 끝난다")
    void removeVersusAssign() throws Exception {
        for (int round = 0; round < ROUNDS; round++) {
            long member = createUser("member" + round + "@example.com", "멤버" + round, null);
            join(g, alice, member);
            long space = createSpace(alice, g, "R" + round);

            List<Outcome> outcomes = race(
                    () -> as(alice, () -> {
                        groupService.removeMember(alice, g, member);
                        return null;
                    }),
                    () -> as(alice, () -> spaceService.assign(alice, space, new SpaceDto.AssigneeReq(member))));

            assertThat(outcomes).filteredOn(Outcome::succeeded).as("round %d", round).hasSize(1);
            // 제거가 먼저면 지정은 NOT_GROUP_MEMBER(400), 지정이 먼저면 제거는 MEMBER_HAS_RESPONSIBILITY(409)
            assertThat(outcomes).filteredOn(outcome -> !outcome.succeeded())
                    .extracting(Outcome::error)
                    .as("round %d", round)
                    .singleElement()
                    .isIn(ErrorCode.MEMBER_HAS_RESPONSIBILITY, ErrorCode.NOT_GROUP_MEMBER);
        }
    }

    @Test
    @DisplayName("OWNER 양도와 이전 OWNER의 스페이스 삭제 요청이 겹쳐도 대기 중인 삭제 요청이 남지 않는다 (정책 3.4 제6항)")
    void transferVersusDeletionRequest() throws Exception {
        for (int round = 0; round < ROUNDS; round++) {
            long group = createGroup(alice, "T" + round);
            join(group, alice, bob);
            join(group, alice, dave);
            long space = createSpace(alice, group, "S");
            assign(alice, space, bob);

            race(
                    () -> as(alice, () -> groupService.transferOwner(alice, group,
                            new GroupDto.TransferReq(dave, REAUTH_REQ))),
                    () -> as(alice, () -> deletionService.request(alice, space, new SpaceDto.DeletionReq(REAUTH_REQ))));

            assertThat(count("SELECT owner_id FROM groups WHERE id = ?", group)).isEqualTo(dave);
            assertThat(count(
                    "SELECT count(*) FROM space_deletion_request WHERE space_id = ? AND status = 'PENDING'", space))
                    .as("round %d", round)
                    .isZero();
        }
    }

    private record Outcome(boolean succeeded, ErrorCode error) {
    }

    /** 두 작업을 동시에 실행한다. 업무 오류가 아닌 예외(예: FK 위반으로 인한 500)는 그대로 실패시킨다. */
    private static List<Outcome> race(Callable<?> first, Callable<?> second) throws Exception {
        List<Outcome> outcomes = new ArrayList<>();
        try (ExecutorService executor = Executors.newFixedThreadPool(2)) {
            List<Future<Object>> futures = executor.invokeAll(List.of(first::call, second::call));
            for (Future<Object> future : futures) {
                try {
                    future.get();
                    outcomes.add(new Outcome(true, null));
                } catch (ExecutionException e) {
                    if (!(e.getCause() instanceof BusinessException business)) {
                        throw new AssertionError("업무 오류가 아닌 예외", e.getCause());
                    }
                    outcomes.add(new Outcome(false, business.getErrorCode()));
                }
            }
        }
        return outcomes;
    }
}
