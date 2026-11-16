package com.back.file.application;

import com.back.file.api.FileDto;

/**
 * 폴더 생성, 이름 변경, 이동, 휴지통으로 삭제, 내려받기 주소 발급 (정책 8.1, 8.3, 8.5, 8.6).
 * 쓰기 메서드는 바뀐 항목의 id를 돌려준다. 화면 응답은 {@link FileQueryService}가 만든다 (쓰기 서비스는 MyBatis를 쓰지 않는다).
 */
public interface FileService {

    long createFolder(long userId, long spaceId, FileDto.FolderReq req);

    long rename(long userId, long spaceId, long itemId, FileDto.RenameReq req);

    long move(long userId, long spaceId, long itemId, FileDto.MoveReq req);

    FileDto.TrashedRes trash(long userId, long spaceId, long itemId);

    FileDto.DownloadUrlRes downloadUrl(long userId, long spaceId, long itemId, FileDto.DownloadUrlReq req);
}
