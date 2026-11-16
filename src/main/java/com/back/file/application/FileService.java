package com.back.file.application;

import com.back.file.api.FileDto;

/** 폴더 생성, 이름 변경, 이동, 휴지통으로 삭제, 내려받기 주소 발급 (정책 8.1, 8.3, 8.5, 8.6). */
public interface FileService {

    FileDto.ItemRes createFolder(long userId, long spaceId, FileDto.FolderReq req);

    FileDto.ItemRes rename(long userId, long spaceId, long itemId, FileDto.RenameReq req);

    FileDto.ItemRes move(long userId, long spaceId, long itemId, FileDto.MoveReq req);

    FileDto.TrashedRes trash(long userId, long spaceId, long itemId);

    FileDto.DownloadUrlRes downloadUrl(long userId, long spaceId, long itemId, FileDto.DownloadUrlReq req);
}
