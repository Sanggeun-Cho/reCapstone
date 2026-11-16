package com.back.file.application;

import com.back.file.api.FileDto;
import java.util.List;

/** 폴더 내용, 항목 상세, 휴지통 목록 조회. */
public interface FileQueryService {

    /** 폴더 내용(휴지통 제외). 페이징하지 않는다. {@code folderId}가 null이면 최상위. */
    List<FileDto.ItemRes> list(long userId, long spaceId, Long folderId);

    FileDto.DetailRes detail(long userId, long spaceId, long itemId);

    /** 항목 한 줄 (쓰기 응답용). 휴지통에 있지 않은 항목만. */
    FileDto.ItemRes item(long userId, long spaceId, long itemId);

    /** 휴지통 (담당자만, 인계 중에는 409). */
    List<FileDto.TrashItemRes> trash(long userId, long spaceId);
}
