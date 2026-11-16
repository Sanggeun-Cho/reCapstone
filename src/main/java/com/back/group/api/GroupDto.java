package com.back.group.api;

import com.back.auth.api.AuthDto;
import com.back.group.domain.Group;
import com.back.group.domain.GroupMemberHistory;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import java.time.Instant;

public final class GroupDto {

    /**
     * 앞뒤 공백을 뺀 길이가 1~50자인 이름. 서비스는 {@code strip()}으로 유니코드 공백(전각 공백 등)까지 지우므로
     * 패턴도 {@code (?U)}로 유니코드 공백을 공백으로 본다. 그래야 검증을 통과한 이름이 공백을 지운 뒤 비거나 길이 판정이 어긋나지 않는다.
     */
    public static final String NAME_PATTERN = "(?sU)\\s*\\S(?:.{0,48}\\S)?\\s*";
    public static final String NAME_MESSAGE = "앞뒤 공백을 뺀 1~50자여야 합니다.";

    /** 탈퇴한 사용자의 이름 대신 보여 주는 표시 (정책 7.8 제3항). */
    public static final String WITHDRAWN_USER_NAME = "탈퇴한 사용자";

    private GroupDto() {
    }

    public record NameReq(@NotBlank @Pattern(regexp = NAME_PATTERN, message = NAME_MESSAGE) String name) {
    }

    public record Res(long id, String name) {

        public static Res from(Group group) {
            return new Res(group.getId(), group.getName());
        }
    }

    /** 내 그룹 목록의 한 줄. {@code owner}는 내가 OWNER인지. */
    public record SummaryRes(long id, String name, boolean owner) {
    }

    public record UserRef(long id, String name) {
    }

    public record DetailRes(long id, String name, UserRef owner, long memberCount) {
    }

    public record MemberRes(long userId, String name, String email, String workPhone, boolean owner,
            Instant joinedAt) {
    }

    public record HistoryRes(UserRef user, GroupMemberHistory.Type type, UserRef actor, Instant occurredAt) {
    }

    /** 그룹 삭제. 그룹 이름을 그대로 입력하고 본인 재확인을 거친다 (정책 2.5 제3항). */
    public record DeleteReq(@NotNull String confirmName, AuthDto.ReauthReq reauth) {
    }

    public record TransferReq(@NotNull Long userId, AuthDto.ReauthReq reauth) {
    }
}
