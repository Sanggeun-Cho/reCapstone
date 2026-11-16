package com.back.space.application;

import com.back.auth.application.ReauthService;
import com.back.common.error.BusinessException;
import com.back.common.error.ErrorCode;
import com.back.group.GroupProperties;
import com.back.group.api.GroupDto;
import com.back.handover.application.HandoverStatus;
import com.back.space.api.SpaceDto;
import com.back.space.application.SpaceAccessPolicy.Action;
import com.back.space.application.SpaceAccessPolicy.Target;
import com.back.space.domain.Space;
import com.back.space.domain.SpaceDeletionRequest;
import com.back.space.domain.SpaceDeletionRequestRepository;
import com.back.user.domain.UserRepository;
import java.time.Clock;
import java.time.Instant;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class SpaceDeletionServiceImpl implements SpaceDeletionService {

    private final SpaceDeletionRequestRepository requestRepository;
    private final UserRepository userRepository;
    private final SpaceAccessPolicy accessPolicy;
    private final HandoverStatus handoverStatus;
    private final ReauthService reauthService;
    private final GroupProperties properties;
    private final Clock clock;

    @Override
    @Transactional
    public SpaceDto.DeletionResultRes request(long userId, long spaceId, SpaceDto.DeletionReq req) {
        // 스페이스 행을 잠가 대기 중인 요청이 동시에 두 건 생기지 않게 한다
        Target target = accessPolicy.checkAndLock(userId, spaceId, Action.DELETE_REQUEST);
        if (handoverStatus.isInProgress(spaceId)) {
            throw new BusinessException(ErrorCode.HANDOVER_IN_PROGRESS);
        }
        Instant now = clock.instant();
        if (findPending(spaceId, now).isPresent()) {
            throw new BusinessException(ErrorCode.DELETION_REQUEST_EXISTS);
        }
        reauthService.verify(userId, req.reauth());

        Space space = target.space();
        boolean alone = !space.hasAssignee() || space.isAssignee(target.group().getOwnerId());
        if (alone) {
            requestRepository.save(SpaceDeletionRequest.approvedAlone(spaceId, userId, now));
            space.delete(now);
            return new SpaceDto.DeletionResultRes(true, null);
        }
        SpaceDeletionRequest request = requestRepository.save(
                SpaceDeletionRequest.pending(spaceId, userId, now.plus(properties.deletionRequestTtl())));
        return new SpaceDto.DeletionResultRes(false, toRes(request));
    }

    @Override
    public SpaceDto.DeletionRequestRes pending(long userId, long spaceId) {
        accessPolicy.checkAndGet(userId, spaceId, Action.DELETE_REQUEST);
        return requestRepository.findBySpaceIdAndStatus(spaceId, SpaceDeletionRequest.Status.PENDING)
                .filter(request -> request.isPending(clock.instant()))
                .map(this::toRes)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND));
    }

    @Override
    @Transactional
    public void approve(long userId, long spaceId, SpaceDto.DeletionReq req) {
        Target target = accessPolicy.checkAndLock(userId, spaceId, Action.DELETE_REQUEST);
        Instant now = clock.instant();
        SpaceDeletionRequest request = requirePendingOfCounterpart(userId, spaceId, now);
        reauthService.verify(userId, req.reauth());
        request.approve(userId, now);
        target.space().delete(now);
    }

    @Override
    @Transactional
    public void reject(long userId, long spaceId, SpaceDto.RejectReq req) {
        accessPolicy.checkAndLock(userId, spaceId, Action.DELETE_REQUEST);
        Instant now = clock.instant();
        SpaceDeletionRequest request = requirePendingOfCounterpart(userId, spaceId, now);
        String reason = req == null || req.reason() == null || req.reason().isBlank() ? null : req.reason().strip();
        request.reject(userId, reason, now);
    }

    @Override
    @Transactional
    public void withdraw(long userId, long spaceId) {
        accessPolicy.checkAndLock(userId, spaceId, Action.DELETE_REQUEST);
        Instant now = clock.instant();
        SpaceDeletionRequest request = findPending(spaceId, now)
                .orElseThrow(() -> new BusinessException(ErrorCode.DELETION_REQUEST_NOT_PENDING));
        if (!request.isRequestedBy(userId)) {
            throw new BusinessException(ErrorCode.ACCESS_DENIED);
        }
        request.withdraw(now);
    }

    /** 승인·거절은 요청한 사람의 상대방만 한다. */
    private SpaceDeletionRequest requirePendingOfCounterpart(long userId, long spaceId, Instant now) {
        SpaceDeletionRequest request = findPending(spaceId, now)
                .orElseThrow(() -> new BusinessException(ErrorCode.DELETION_REQUEST_NOT_PENDING));
        if (request.isRequestedBy(userId)) {
            throw new BusinessException(ErrorCode.SELF_APPROVAL_NOT_ALLOWED);
        }
        return request;
    }

    /**
     * 기한 안의 대기 요청. 기한이 지난 대기 요청은 만료 처리해 새 요청이 들어올 수 있게 한다 (부분 유니크).
     * 만료 처리는 쓰기 트랜잭션에서만 반영된다.
     */
    private Optional<SpaceDeletionRequest> findPending(long spaceId, Instant now) {
        return requestRepository.findBySpaceIdAndStatus(spaceId, SpaceDeletionRequest.Status.PENDING)
                .filter(request -> {
                    if (request.isPending(now)) {
                        return true;
                    }
                    request.expire();
                    requestRepository.flush();
                    return false;
                });
    }

    private SpaceDto.DeletionRequestRes toRes(SpaceDeletionRequest request) {
        GroupDto.UserRef requester = userRepository.findById(request.getRequesterId())
                .map(user -> new GroupDto.UserRef(user.getId(), user.getName()))
                .orElse(null);
        return new SpaceDto.DeletionRequestRes(requester, request.getCreatedAt(), request.getExpiresAt());
    }
}
