package com.back.file.application;

import com.back.file.api.FileDto;
import java.util.List;
import org.springframework.web.multipart.MultipartFile;

/** 파일 업로드 (정책 8.2, 8.4). */
public interface UploadService {

    /**
     * 파일마다 따로 처리해 성공한 파일만 저장한다. 저장된 것이 없고 실패가 있으면 첫 실패의 코드로 응답한다.
     *
     * @param folderId null이면 최상위
     */
    FileDto.UploadRes upload(long userId, long spaceId, Long folderId, FileDto.OnConflict onConflict,
            List<MultipartFile> files);
}
