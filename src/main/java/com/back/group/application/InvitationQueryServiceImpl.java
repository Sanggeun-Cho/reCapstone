package com.back.group.application;

import com.back.common.error.BusinessException;
import com.back.common.error.ErrorCode;
import com.back.group.api.InvitationDto;
import com.back.group.application.GroupAccessPolicy.Action;
import com.back.group.query.GroupQueryMapper;
import com.back.user.domain.UserRepository;
import java.time.Clock;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class InvitationQueryServiceImpl implements InvitationQueryService {

    private final GroupQueryMapper mapper;
    private final GroupAccessPolicy accessPolicy;
    private final UserRepository userRepository;
    private final Clock clock;

    @Override
    public List<InvitationDto.Res> pendingOfGroup(long userId, long groupId) {
        accessPolicy.checkAndGet(userId, groupId, Action.MANAGE);
        return mapper.findPendingInvitations(groupId, clock.instant()).stream()
                .map(row -> new InvitationDto.Res(row.id(), row.email(), row.expiresAt()))
                .toList();
    }

    @Override
    public List<InvitationDto.ReceivedRes> received(long userId) {
        String email = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.UNAUTHENTICATED))
                .getEmail();
        return mapper.findReceivedInvitations(email, clock.instant()).stream()
                .map(row -> new InvitationDto.ReceivedRes(row.id(), row.groupId(), row.groupName(),
                        row.inviterName(), row.expiresAt()))
                .toList();
    }
}
