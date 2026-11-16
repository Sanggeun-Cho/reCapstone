package com.back.group.api;

import com.back.group.domain.Invitation;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.Instant;

public final class InvitationDto {

    private InvitationDto() {
    }

    public record CreateReq(@NotBlank @Email @Size(max = 254) String email) {
    }

    /** 초대한 쪽이 보는 초대. 가입 여부는 드러내지 않는다. */
    public record Res(long id, String email, Instant expiresAt) {

        public static Res from(Invitation invitation) {
            return new Res(invitation.getId(), invitation.getEmail(), invitation.getExpiresAt());
        }
    }

    /** 받은 초대. */
    public record ReceivedRes(long id, long groupId, String groupName, String inviterName, Instant expiresAt) {
    }
}
