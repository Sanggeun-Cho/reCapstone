package com.back.group.api;

import com.back.common.api.PageDto;
import com.back.common.security.AuthUser;
import com.back.group.application.GroupQueryService;
import com.back.group.application.GroupService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * 그룹과 멤버 (정책 제2장). 멤버를 직접 추가하는 API는 두지 않는다. 가입은 초대 수락으로만 한다 (정책 2.4).
 */
@RestController
@RequestMapping("/api/v1/groups")
@RequiredArgsConstructor
public class GroupController {

    private final GroupService groupService;
    private final GroupQueryService groupQueryService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public GroupDto.Res create(@AuthenticationPrincipal AuthUser user, @Valid @RequestBody GroupDto.NameReq req) {
        return groupService.create(user.userId(), req);
    }

    /** 내가 멤버인 그룹. 최근에 만든 그룹부터, 커서로 이어서 읽는다. */
    @GetMapping
    public PageDto.CursorRes<GroupDto.SummaryRes> myGroups(@AuthenticationPrincipal AuthUser user,
            @RequestParam(required = false) Long cursor,
            @RequestParam(defaultValue = "20") @Min(1) @Max(GroupQueryService.MAX_PAGE_SIZE) int size) {
        return groupQueryService.myGroups(user.userId(), cursor, size);
    }

    @GetMapping("/{groupId}")
    public GroupDto.DetailRes detail(@AuthenticationPrincipal AuthUser user, @PathVariable long groupId) {
        return groupQueryService.detail(user.userId(), groupId);
    }

    @PatchMapping("/{groupId}")
    public GroupDto.Res rename(@AuthenticationPrincipal AuthUser user, @PathVariable long groupId,
            @Valid @RequestBody GroupDto.NameReq req) {
        return groupService.rename(user.userId(), groupId, req);
    }

    @PostMapping("/{groupId}/deletion")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@AuthenticationPrincipal AuthUser user, @PathVariable long groupId,
            @Valid @RequestBody GroupDto.DeleteReq req) {
        groupService.delete(user.userId(), groupId, req);
    }

    @PostMapping("/{groupId}/owner-transfer")
    public GroupDto.Res transferOwner(@AuthenticationPrincipal AuthUser user, @PathVariable long groupId,
            @Valid @RequestBody GroupDto.TransferReq req) {
        return groupService.transferOwner(user.userId(), groupId, req);
    }

    @GetMapping("/{groupId}/members")
    public List<GroupDto.MemberRes> members(@AuthenticationPrincipal AuthUser user, @PathVariable long groupId) {
        return groupQueryService.members(user.userId(), groupId);
    }

    /** 그룹을 떠난다. */
    @DeleteMapping("/{groupId}/members/me")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void leave(@AuthenticationPrincipal AuthUser user, @PathVariable long groupId) {
        groupService.leave(user.userId(), groupId);
    }

    @DeleteMapping("/{groupId}/members/{userId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void removeMember(@AuthenticationPrincipal AuthUser user, @PathVariable long groupId,
            @PathVariable long userId) {
        groupService.removeMember(user.userId(), groupId, userId);
    }

    @GetMapping("/{groupId}/member-history")
    public List<GroupDto.HistoryRes> history(@AuthenticationPrincipal AuthUser user, @PathVariable long groupId) {
        return groupQueryService.history(user.userId(), groupId);
    }
}
