package com.back.file.api;

import com.back.common.security.AuthUser;
import com.back.file.application.FileQueryService;
import com.back.file.application.TrashService;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * 스페이스의 휴지통 (정책 8.5). 담당자만 쓰고, 인계가 진행되는 동안에는 조회까지 409다.
 */
@RestController
@RequestMapping("/api/v1/spaces/{spaceId}/trash")
@RequiredArgsConstructor
public class TrashController {

    private final TrashService trashService;
    private final FileQueryService fileQueryService;

    @GetMapping
    public List<FileDto.TrashItemRes> list(@AuthenticationPrincipal AuthUser user, @PathVariable long spaceId) {
        return fileQueryService.trash(user.userId(), spaceId);
    }

    @PostMapping("/{itemId}/restore")
    public FileDto.RestoreRes restore(@AuthenticationPrincipal AuthUser user, @PathVariable long spaceId,
            @PathVariable long itemId) {
        return trashService.restore(user.userId(), spaceId, itemId);
    }

    @DeleteMapping("/{itemId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void purge(@AuthenticationPrincipal AuthUser user, @PathVariable long spaceId, @PathVariable long itemId) {
        trashService.purge(user.userId(), spaceId, itemId);
    }

    @DeleteMapping
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void empty(@AuthenticationPrincipal AuthUser user, @PathVariable long spaceId) {
        trashService.empty(user.userId(), spaceId);
    }
}
