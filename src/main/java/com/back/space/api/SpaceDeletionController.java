package com.back.space.api;

import com.back.common.security.AuthUser;
import com.back.space.application.SpaceDeletionService;
import jakarta.validation.Valid;
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
 * 스페이스 삭제 요청 (정책 3.4). OWNER와 담당자 중 한 사람이 요청하고 다른 사람이 승인한다.
 */
@RestController
@RequestMapping("/api/v1/spaces/{spaceId}/deletion-request")
@RequiredArgsConstructor
public class SpaceDeletionController {

    private final SpaceDeletionService deletionService;

    @PostMapping
    public SpaceDto.DeletionResultRes request(@AuthenticationPrincipal AuthUser user, @PathVariable long spaceId,
            @Valid @RequestBody SpaceDto.DeletionReq req) {
        return deletionService.request(user.userId(), spaceId, req);
    }

    @GetMapping
    public SpaceDto.DeletionRequestRes pending(@AuthenticationPrincipal AuthUser user, @PathVariable long spaceId) {
        return deletionService.pending(user.userId(), spaceId);
    }

    @PostMapping("/approve")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void approve(@AuthenticationPrincipal AuthUser user, @PathVariable long spaceId,
            @Valid @RequestBody SpaceDto.DeletionReq req) {
        deletionService.approve(user.userId(), spaceId, req);
    }

    @PostMapping("/reject")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void reject(@AuthenticationPrincipal AuthUser user, @PathVariable long spaceId,
            @Valid @RequestBody(required = false) SpaceDto.RejectReq req) {
        deletionService.reject(user.userId(), spaceId, req);
    }

    /** 요청한 사람의 철회. */
    @DeleteMapping
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void withdraw(@AuthenticationPrincipal AuthUser user, @PathVariable long spaceId) {
        deletionService.withdraw(user.userId(), spaceId);
    }
}
