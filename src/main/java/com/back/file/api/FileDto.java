package com.back.file.api;

import com.back.common.error.ErrorCode;
import com.back.file.domain.DriveItem;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import java.util.List;

/** 파일·폴더 요청과 응답 (정책 제8장). 저장소 키와 주소는 응답에 넣지 않는다. */
public final class FileDto {

    private FileDto() {
    }

    /** 같은 이름의 파일을 올릴 때 (정책 8.4 제1항) */
    public enum OnConflict {
        /** 둘 다 유지: 올린 파일에 번호를 붙인다 (기본) */
        RENAME,
        /** 덮어쓰기: 기존 파일의 내용을 바꾸고 이전 내용은 휴지통으로 */
        REPLACE
    }

    public enum Disposition {
        /** 내려받기 */
        ATTACHMENT,
        /** 브라우저에서 바로 열기 (PDF·이미지, 정책 8.6) */
        INLINE
    }

    /**
     * 폴더 내용의 한 항목. 업로더가 탈퇴했으면 이름과 함께 {@code uploaderWithdrawn=true}를 주고, "(탈퇴)" 표기는 프론트가 붙인다
     * (정책 7.8 제3항).
     */
    public record ItemRes(long id, DriveItem.Type type, String name, Long size, String contentType,
            boolean inlineViewable, String uploaderName, boolean uploaderWithdrawn, Instant createdAt) {
    }

    /** 경로의 한 폴더 */
    public record PathRef(long id, String name) {
    }

    /** 항목 상세. {@code path}는 최상위부터 부모 폴더까지 */
    public record DetailRes(ItemRes item, Long parentId, List<PathRef> path) {
    }

    public record FolderReq(Long parentId, @NotNull String name) {
    }

    public record RenameReq(@NotNull String name) {
    }

    /** {@code targetFolderId}가 null이면 최상위로 옮긴다 */
    public record MoveReq(Long targetFolderId) {
    }

    /** 휴지통으로 옮긴 항목 수 (폴더면 하위 포함) */
    public record TrashedRes(int movedCount) {
    }

    public record CreatedRes(long id, String name, boolean replaced) {
    }

    public record FailedRes(String name, ErrorCode code) {
    }

    /** 업로드 결과. 크기가 0이라 건너뛴 파일은 {@code skipped}, 실패한 파일은 {@code failed}에 담는다 (정책 8.2) */
    public record UploadRes(List<CreatedRes> created, List<String> skipped, List<FailedRes> failed) {
    }

    public record DownloadUrlReq(Disposition disposition) {

        public Disposition dispositionOrDefault() {
            return disposition == null ? Disposition.ATTACHMENT : disposition;
        }
    }

    public record DownloadUrlRes(String url, Instant expiresAt) {
    }

    /** 휴지통 항목. {@code originalPath}는 원래 폴더의 경로 ({@code /}는 최상위) */
    public record TrashItemRes(long id, DriveItem.Type type, String name, String originalPath, Instant trashedAt,
            Instant purgeAt) {
    }

    /** 복구 결과. 번호가 붙거나 최상위로 돌아갔으면 바뀐 값이다 */
    public record RestoreRes(long id, String name, Long parentId) {
    }
}
