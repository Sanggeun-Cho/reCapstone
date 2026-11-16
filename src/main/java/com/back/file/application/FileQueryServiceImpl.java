package com.back.file.application;

import com.back.common.error.BusinessException;
import com.back.common.error.ErrorCode;
import com.back.file.FileProperties;
import com.back.file.api.FileDto;
import com.back.file.domain.DriveItem;
import com.back.file.query.FileQueryMapper;
import com.back.space.application.SpaceAccessPolicy;
import com.back.space.application.SpaceAccessPolicy.Action;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class FileQueryServiceImpl implements FileQueryService {

    private final FileQueryMapper mapper;
    private final SpaceAccessPolicy accessPolicy;
    private final DriveItems items;
    private final FileProperties properties;

    @Override
    public List<FileDto.ItemRes> list(long userId, long spaceId, Long folderId) {
        accessPolicy.checkAndGet(userId, spaceId, Action.READ);
        if (folderId != null) {
            DriveItem folder = items.active(spaceId, folderId);
            if (!folder.isFolder()) {
                throw new BusinessException(ErrorCode.INVALID_INPUT, "폴더가 아닙니다.");
            }
        }
        return mapper.findChildren(spaceId, folderId).stream().map(FileQueries::toRes).toList();
    }

    @Override
    public FileDto.DetailRes detail(long userId, long spaceId, long itemId) {
        accessPolicy.checkAndGet(userId, spaceId, Action.READ);
        items.active(spaceId, itemId);
        FileQueryMapper.ItemRow row = mapper.findItem(itemId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND));
        List<FileDto.PathRef> path = mapper.findPath(itemId).stream()
                .map(p -> new FileDto.PathRef(p.id(), p.name()))
                .toList();
        return new FileDto.DetailRes(FileQueries.toRes(row), row.parentId(), path);
    }

    @Override
    public FileDto.ItemRes item(long userId, long spaceId, long itemId) {
        return detail(userId, spaceId, itemId).item();
    }

    @Override
    public List<FileDto.TrashItemRes> trash(long userId, long spaceId) {
        accessPolicy.checkAndGet(userId, spaceId, Action.WRITE);
        items.requireNoHandover(spaceId);
        return mapper.findTrash(spaceId).stream()
                .map(row -> new FileDto.TrashItemRes(row.id(), row.type(), row.name(), row.originalPath(),
                        row.trashedAt(), row.trashedAt().plus(properties.trashRetention())))
                .toList();
    }
}
