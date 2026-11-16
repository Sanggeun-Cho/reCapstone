package com.back.space.api;

import com.back.auth.api.AuthDto;
import com.back.group.api.GroupDto;
import com.back.space.domain.AssignmentChange;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.Instant;

public final class SpaceDto {

    public static final int REASON_MAX = 500;

    private SpaceDto() {
    }

    public record NameReq(
            @NotBlank @Pattern(regexp = GroupDto.NAME_PATTERN, message = GroupDto.NAME_MESSAGE) String name) {
    }

    public record Res(long id, String name, GroupDto.UserRef assignee) {
    }

    public record DetailRes(long id, String name, GroupDto.Res group, GroupDto.UserRef assignee) {
    }

    /** OWNER가 보는 그룹의 스페이스 목록 한 줄. */
    public record SummaryRes(long id, String name, GroupDto.UserRef assignee, Long ongoingHandoverId,
            boolean deletionPending) {
    }

    /** 첫 지정·공석 충원. */
    public record AssigneeReq(@NotNull Long userId) {
    }

    /** 긴급 재지정. {@code userId}가 없으면 담당자를 비운다 (정책 3.3 제2항). */
    public record ForcedReassignmentReq(Long userId, @NotBlank @Size(max = REASON_MAX) String reason) {
    }

    public record AssignmentChangeRes(long id, AssignmentChange.Type type, GroupDto.UserRef from,
            GroupDto.UserRef to, GroupDto.UserRef actor, String reason, Instant occurredAt) {
    }

    // --- 삭제 요청 (정책 3.4) ---

    public record DeletionReq(AuthDto.ReauthReq reauth) {
    }

    public record RejectReq(@Size(max = REASON_MAX) String reason) {
    }

    public record DeletionRequestRes(GroupDto.UserRef requestedBy, Instant requestedAt, Instant expiresAt) {
    }

    /** 삭제 요청 결과. 동의할 상대가 없으면 바로 삭제되고({@code deleted: true}), 아니면 대기 요청을 돌려준다. */
    public record DeletionResultRes(boolean deleted, DeletionRequestRes request) {
    }
}
