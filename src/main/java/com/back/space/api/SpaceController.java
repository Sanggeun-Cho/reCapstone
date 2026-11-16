package com.back.space.api;

import com.back.common.api.PageDto;
import com.back.common.security.AuthUser;
import com.back.space.application.SpaceQueryService;
import com.back.space.application.SpaceService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * 스페이스와 담당자 (정책 제3장). 삭제는 {@link SpaceDeletionController}의 양쪽 동의 절차를 거친다.
 */
@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class SpaceController {

    private final SpaceService spaceService;
    private final SpaceQueryService spaceQueryService;

    @PostMapping("/groups/{groupId}/spaces")
    @ResponseStatus(HttpStatus.CREATED)
    public SpaceDto.Res create(@AuthenticationPrincipal AuthUser user, @PathVariable long groupId,
            @Valid @RequestBody SpaceDto.NameReq req) {
        return spaceService.create(user.userId(), groupId, req);
    }

    /** 그룹의 스페이스 목록 (OWNER). 표 형태 화면이라 페이지 번호를 쓴다. */
    @GetMapping("/groups/{groupId}/spaces")
    public PageDto.PageRes<SpaceDto.SummaryRes> groupSpaces(@AuthenticationPrincipal AuthUser user,
            @PathVariable long groupId,
            @RequestParam(defaultValue = "1") @Min(1) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(SpaceQueryService.MAX_PAGE_SIZE) int size) {
        return spaceQueryService.groupSpaces(user.userId(), groupId, page, size);
    }

    @GetMapping("/spaces/{spaceId}")
    public SpaceDto.DetailRes detail(@AuthenticationPrincipal AuthUser user, @PathVariable long spaceId) {
        return spaceService.detail(user.userId(), spaceId);
    }

    @PatchMapping("/spaces/{spaceId}")
    public SpaceDto.Res rename(@AuthenticationPrincipal AuthUser user, @PathVariable long spaceId,
            @Valid @RequestBody SpaceDto.NameReq req) {
        return spaceService.rename(user.userId(), spaceId, req);
    }

    /** 첫 지정·공석 충원. 담당자가 있으면 409 (바꾸려면 인계나 긴급 재지정). */
    @PutMapping("/spaces/{spaceId}/assignee")
    public SpaceDto.Res assign(@AuthenticationPrincipal AuthUser user, @PathVariable long spaceId,
            @Valid @RequestBody SpaceDto.AssigneeReq req) {
        return spaceService.assign(user.userId(), spaceId, req);
    }

    @PostMapping("/spaces/{spaceId}/forced-reassignment")
    public SpaceDto.Res forceReassign(@AuthenticationPrincipal AuthUser user, @PathVariable long spaceId,
            @Valid @RequestBody SpaceDto.ForcedReassignmentReq req) {
        return spaceService.forceReassign(user.userId(), spaceId, req);
    }

    @GetMapping("/spaces/{spaceId}/assignment-changes")
    public List<SpaceDto.AssignmentChangeRes> assignmentChanges(@AuthenticationPrincipal AuthUser user,
            @PathVariable long spaceId) {
        return spaceQueryService.assignmentChanges(user.userId(), spaceId);
    }
}
