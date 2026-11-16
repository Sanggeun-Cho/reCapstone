package com.back.file.query;

import com.back.file.domain.DriveItem;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * 파일·폴더 화면 조회 (MyBatis, SELECT 전용). SQL은 같은 이름의 XML에 있다.
 * 업로더가 탈퇴했으면 이름과 탈퇴 여부를 함께 돌려준다 (정책 7.8 제3항).
 */
@Mapper
public interface FileQueryMapper {

    /** 폴더 안의 항목(휴지통 제외). 폴더 먼저, 그다음 파일, 각각 이름순. {@code parentId}가 null이면 최상위. */
    List<ItemRow> findChildren(@Param("spaceId") long spaceId, @Param("parentId") Long parentId);

    Optional<ItemRow> findItem(@Param("itemId") long itemId);

    /** 최상위부터 부모까지의 폴더 */
    List<PathRow> findPath(@Param("itemId") long itemId);

    /** 휴지통 항목. 삭제 최신순 */
    List<TrashRow> findTrash(@Param("spaceId") long spaceId);

    record ItemRow(long id, DriveItem.Type type, String name, Long parentId, Long size, String contentType,
            boolean inlineViewable, String uploaderName, boolean uploaderWithdrawn, Instant createdAt) {
    }

    record PathRow(long id, String name) {
    }

    record TrashRow(long id, DriveItem.Type type, String name, String originalPath, Instant trashedAt) {
    }
}
