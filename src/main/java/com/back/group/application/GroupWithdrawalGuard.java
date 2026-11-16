package com.back.group.application;

import com.back.common.error.BusinessException;
import com.back.common.error.ErrorCode;
import com.back.group.domain.GroupRepository;
import com.back.space.domain.SpaceRepository;
import com.back.user.application.WithdrawalGuard;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 그룹 OWNER와 스페이스 담당자는 탈퇴할 수 없다 (정책 7.8 제1항). 보관 중인 그룹·스페이스의 지위는 막지 않는다.
 * 진행 중인 인계의 당사자 조건은 S5에서 따로 붙인다.
 */
@Component
@RequiredArgsConstructor
public class GroupWithdrawalGuard implements WithdrawalGuard {

    public static final String REASONS = "reasons";

    public enum Reason {
        OWNER, ASSIGNEE
    }

    private final GroupRepository groupRepository;
    private final SpaceRepository spaceRepository;

    @Override
    public void check(long userId) {
        List<Reason> reasons = new ArrayList<>();
        if (groupRepository.existsByOwnerId(userId)) {
            reasons.add(Reason.OWNER);
        }
        if (spaceRepository.existsActiveAssignment(userId)) {
            reasons.add(Reason.ASSIGNEE);
        }
        if (!reasons.isEmpty()) {
            String detail = reasons.contains(Reason.OWNER)
                    ? "OWNER인 그룹이 있습니다. 먼저 다른 멤버에게 양도하세요."
                    : "담당 중인 스페이스가 있습니다. 먼저 인계하거나 긴급 재지정으로 담당에서 벗어나세요.";
            throw new BusinessException(ErrorCode.WITHDRAWAL_BLOCKED, detail, Map.of(REASONS, reasons));
        }
    }
}
