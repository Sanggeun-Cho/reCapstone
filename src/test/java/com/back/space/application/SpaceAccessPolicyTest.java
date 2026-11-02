package com.back.space.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.back.group.domain.Group;
import com.back.space.application.SpaceAccessPolicy.Action;
import com.back.space.application.SpaceAccessPolicy.Target;
import com.back.space.domain.Space;
import java.util.EnumSet;
import java.util.Set;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * AUTHZ-U1: 역할 조합 × 행위가 정책 4.2 권한표(평상시)와 같은지. 인수자 열은 S5에서 인계 상태와 함께 검증한다.
 */
class SpaceAccessPolicyTest {

    private static final long OWNER = 1;
    private static final long ASSIGNEE = 2;
    private static final long MEMBER = 3;
    private static final long OUTSIDER = 4;
    private static final long RECEIVER = 5;

    private final SpaceAccessPolicy policy = new SpaceAccessPolicy(null, null, null);

    static Stream<Arguments> matrix() {
        return Stream.of(
                Arguments.of("OWNER", OWNER, ASSIGNEE, false,
                        EnumSet.of(Action.READ, Action.MANAGE, Action.DELETE_REQUEST, Action.VIEW_HISTORY)),
                Arguments.of("담당자", ASSIGNEE, ASSIGNEE, false,
                        EnumSet.of(Action.READ, Action.WRITE, Action.DELETE_REQUEST, Action.VIEW_HISTORY)),
                Arguments.of("OWNER+담당자", OWNER, OWNER, false, EnumSet.allOf(Action.class)),
                Arguments.of("그 외 멤버", MEMBER, ASSIGNEE, false, EnumSet.noneOf(Action.class)),
                Arguments.of("그룹 밖", OUTSIDER, ASSIGNEE, false, EnumSet.noneOf(Action.class)),
                Arguments.of("인수자", RECEIVER, ASSIGNEE, true, EnumSet.of(Action.READ)));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("matrix")
    @DisplayName("AUTHZ-U1: 역할별로 허용되는 행위가 권한표와 일치한다")
    void matchesPolicyTable(String role, long userId, long assigneeId, boolean receiver, Set<Action> allowed) {
        Target target = new Target(space(assigneeId), new Group("G", OWNER), receiver);

        for (Action action : Action.values()) {
            assertThat(policy.can(userId, target, action))
                    .as("%s / %s", role, action)
                    .isEqualTo(allowed.contains(action));
        }
    }

    private static Space space(long assigneeId) {
        Space space = new Space(10, "S");
        ReflectionTestUtils.setField(space, "id", 100L);
        ReflectionTestUtils.setField(space, "assigneeId", assigneeId);
        return space;
    }
}
