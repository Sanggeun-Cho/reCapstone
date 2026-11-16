package com.back.file.api;

import com.back.common.security.AuthUser;
import com.back.file.application.FileQueryService;
import com.back.file.application.FileService;
import com.back.file.application.UploadService;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * 스페이스의 파일과 폴더 (정책 제8장). 읽기는 관리자·담당자·인수자, 쓰기는 담당자만 한다 (정책 4.2).
 */
@RestController
@RequestMapping("/api/v1/spaces/{spaceId}")
@RequiredArgsConstructor
public class FileController {

    private final FileService fileService;
    private final FileQueryService fileQueryService;
    private final UploadService uploadService;

    /** 폴더 내용. 폴더 먼저, 이름순. 페이징하지 않는다. {@code folderId}를 생략하면 최상위 */
    @GetMapping("/items")
    public List<FileDto.ItemRes> list(@AuthenticationPrincipal AuthUser user, @PathVariable long spaceId,
            @RequestParam(required = false) Long folderId) {
        return fileQueryService.list(user.userId(), spaceId, folderId);
    }

    @GetMapping("/items/{itemId}")
    public FileDto.DetailRes detail(@AuthenticationPrincipal AuthUser user, @PathVariable long spaceId,
            @PathVariable long itemId) {
        return fileQueryService.detail(user.userId(), spaceId, itemId);
    }

    /**
     * 파일 업로드 (multipart {@code files}). 파일마다 따로 처리해 성공한 파일만 저장한다. 같은 이름은 {@code onConflict}를 따른다.
     */
    @PostMapping(path = "/files", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    public FileDto.UploadRes upload(@AuthenticationPrincipal AuthUser user, @PathVariable long spaceId,
            @RequestPart("files") List<MultipartFile> files,
            @RequestParam(required = false) Long folderId,
            @RequestParam(required = false) FileDto.OnConflict onConflict) {
        return uploadService.upload(user.userId(), spaceId, folderId, onConflict, files);
    }

    @PostMapping("/folders")
    @ResponseStatus(HttpStatus.CREATED)
    public FileDto.ItemRes createFolder(@AuthenticationPrincipal AuthUser user, @PathVariable long spaceId,
            @Valid @RequestBody FileDto.FolderReq req) {
        long id = fileService.createFolder(user.userId(), spaceId, req);
        return fileQueryService.item(user.userId(), spaceId, id);
    }

    @PatchMapping("/items/{itemId}")
    public FileDto.ItemRes rename(@AuthenticationPrincipal AuthUser user, @PathVariable long spaceId,
            @PathVariable long itemId, @Valid @RequestBody FileDto.RenameReq req) {
        fileService.rename(user.userId(), spaceId, itemId, req);
        return fileQueryService.item(user.userId(), spaceId, itemId);
    }

    @PostMapping("/items/{itemId}/move")
    public FileDto.ItemRes move(@AuthenticationPrincipal AuthUser user, @PathVariable long spaceId,
            @PathVariable long itemId, @Valid @RequestBody FileDto.MoveReq req) {
        fileService.move(user.userId(), spaceId, itemId, req);
        return fileQueryService.item(user.userId(), spaceId, itemId);
    }

    /** 휴지통으로 옮긴다. 폴더면 하위 항목도 함께 옮기고 옮긴 개수를 돌려준다 */
    @DeleteMapping("/items/{itemId}")
    public FileDto.TrashedRes trash(@AuthenticationPrincipal AuthUser user, @PathVariable long spaceId,
            @PathVariable long itemId) {
        return fileService.trash(user.userId(), spaceId, itemId);
    }

    /** 5분 동안 유효한 내려받기 주소. 바로 열기({@code INLINE})는 PDF·이미지만 */
    @PostMapping("/files/{itemId}/download-url")
    public FileDto.DownloadUrlRes downloadUrl(@AuthenticationPrincipal AuthUser user, @PathVariable long spaceId,
            @PathVariable long itemId, @RequestBody(required = false) FileDto.DownloadUrlReq req) {
        return fileService.downloadUrl(user.userId(), spaceId, itemId, req);
    }
}
