package com.back.file.application;

import com.back.file.api.FileDto;
import com.back.file.query.FileQueryMapper;

final class FileQueries {

    private FileQueries() {
    }

    static FileDto.ItemRes toRes(FileQueryMapper.ItemRow row) {
        return new FileDto.ItemRes(row.id(), row.type(), row.name(), row.size(), row.contentType(),
                row.inlineViewable(), row.uploaderName(), row.uploaderWithdrawn(), row.createdAt());
    }
}
