package com.back.group.api;

import com.back.common.security.AuthUser;
import com.back.group.application.InvitationQueryService;
import com.back.group.application.InvitationService;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * 그룹 초대 (정책 2.4). 보내기·취소·대기 목록은 OWNER가, 수락·거절은 초대받은 이메일의 사용자가 한다.
 */
@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class InvitationController {

    private final InvitationService invitationService;
    private final InvitationQueryService invitationQueryService;

    @PostMapping("/groups/{groupId}/invitations")
    @ResponseStatus(HttpStatus.CREATED)
    public InvitationDto.Res invite(@AuthenticationPrincipal AuthUser user, @PathVariable long groupId,
            @Valid @RequestBody InvitationDto.CreateReq req) {
        return invitationService.invite(user.userId(), groupId, req);
    }

    @GetMapping("/groups/{groupId}/invitations")
    public List<InvitationDto.Res> pending(@AuthenticationPrincipal AuthUser user, @PathVariable long groupId) {
        return invitationQueryService.pendingOfGroup(user.userId(), groupId);
    }

    @DeleteMapping("/groups/{groupId}/invitations/{invitationId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void cancel(@AuthenticationPrincipal AuthUser user, @PathVariable long groupId,
            @PathVariable long invitationId) {
        invitationService.cancel(user.userId(), groupId, invitationId);
    }

    @GetMapping("/users/me/invitations")
    public List<InvitationDto.ReceivedRes> received(@AuthenticationPrincipal AuthUser user) {
        return invitationQueryService.received(user.userId());
    }

    @PostMapping("/invitations/{invitationId}/accept")
    public GroupDto.Res accept(@AuthenticationPrincipal AuthUser user, @PathVariable long invitationId) {
        return invitationService.accept(user.userId(), invitationId);
    }

    @PostMapping("/invitations/{invitationId}/decline")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void decline(@AuthenticationPrincipal AuthUser user, @PathVariable long invitationId) {
        invitationService.decline(user.userId(), invitationId);
    }
}
