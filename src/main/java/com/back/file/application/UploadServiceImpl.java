package com.back.file.application;

import com.back.common.error.BusinessException;
import com.back.common.error.ErrorCode;
import com.back.file.FileProperties;
import com.back.file.api.FileDto;
import com.back.file.domain.DriveItem;
import com.back.file.storage.FileStorage;
import com.back.file.storage.StorageException;
import com.back.file.support.ContentTypeResolver;
import com.back.file.support.ItemNames;
import com.back.space.application.SpaceAccessPolicy;
import com.back.space.application.SpaceAccessPolicy.Action;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.SequenceInputStream;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

/**
 * 업로드 (DESIGN_REVIEW.md 6.8 구현 메모).
 * <ol>
 *   <li>트랜잭션 밖에서 권한·인계 여부, 개수·크기, 대상 폴더를 빠르게 확인한다.</li>
 *   <li>파일마다 이름을 검증하고 저장소로 보낸다. 앞부분으로 실제 형식을 판별한다. 실패한 파일은 객체를 지운다.</li>
 *   <li>짧은 트랜잭션({@link UploadRecorder})에서 스페이스 행을 잠그고 다시 확인한 뒤 기록한다.</li>
 *   <li>기록이 실패하면 올린 객체를 지운다. 지우지 못한 객체는 고아 객체 정리 작업이 지운다.</li>
 * </ol>
 * 저장소 전송 중에는 DB 트랜잭션을 열지 않는다. 커넥션과 스페이스 잠금을 몇 분씩 쥐지 않기 위해서다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(propagation = Propagation.NOT_SUPPORTED)
public class UploadServiceImpl implements UploadService {

    public static final String FAILED = "failed";
    public static final String SKIPPED = "skipped";

    private final SpaceAccessPolicy accessPolicy;
    private final DriveItems items;
    private final UploadRecorder recorder;
    private final FileStorage storage;
    private final FileProperties properties;

    @Override
    public FileDto.UploadRes upload(long userId, long spaceId, Long folderId, FileDto.OnConflict onConflict,
            List<MultipartFile> files) {
        List<MultipartFile> parts = files == null ? List.of() : files;
        checkLimits(parts);
        accessPolicy.checkAndGet(userId, spaceId, Action.WRITE);
        items.requireNoHandover(spaceId);
        items.folderOrRoot(spaceId, folderId);
        FileDto.OnConflict mode = onConflict == null ? FileDto.OnConflict.RENAME : onConflict;

        List<String> skipped = new ArrayList<>();
        List<FileDto.FailedRes> failed = new ArrayList<>();
        List<UploadRecorder.Uploaded> uploads = new ArrayList<>();
        for (MultipartFile part : parts) {
            String requestName = part.getOriginalFilename();
            if (part.isEmpty()) {
                skipped.add(requestName);
                continue;
            }
            if (!ItemNames.isValid(requestName)) {
                failed.add(new FileDto.FailedRes(requestName, ErrorCode.INVALID_ITEM_NAME));
                continue;
            }
            String name = ItemNames.normalize(requestName);
            try {
                uploads.add(send(userId, spaceId, requestName, name, part));
            } catch (StorageException | IOException e) {
                log.warn("Upload of a file to space {} failed", spaceId, e);
                failed.add(new FileDto.FailedRes(requestName, ErrorCode.STORAGE_UNAVAILABLE));
            }
        }

        List<FileDto.CreatedRes> created = List.of();
        if (!uploads.isEmpty()) {
            UploadRecorder.Result result;
            try {
                result = recorder.record(userId, spaceId, folderId, mode, uploads);
            } catch (RuntimeException e) {
                uploads.forEach(upload -> deleteQuietly(upload.content().storageKey()));
                throw e;
            }
            result.rejectedKeys().forEach(this::deleteQuietly);
            failed.addAll(result.failed());
            created = result.created();
        }

        if (created.isEmpty() && !failed.isEmpty()) {
            FileDto.FailedRes first = failed.getFirst();
            Map<String, Object> details = new LinkedHashMap<>();
            details.put(FAILED, failed);
            details.put(SKIPPED, skipped);
            throw new BusinessException(first.code(), first.code().getMessage(), details);
        }
        return new FileDto.UploadRes(created, skipped, failed);
    }

    /** 크기·개수 초과는 파일별 실패가 아니라 요청 전체 거부다. */
    private void checkLimits(List<MultipartFile> parts) {
        if (parts.size() > properties.maxFiles()) {
            throw new BusinessException(ErrorCode.TOO_MANY_FILES);
        }
        long total = 0;
        for (MultipartFile part : parts) {
            if (part.getSize() > properties.maxFileSize().toBytes()) {
                throw new BusinessException(ErrorCode.FILE_TOO_LARGE);
            }
            total += part.getSize();
        }
        if (total > properties.maxRequestSize().toBytes()) {
            throw new BusinessException(ErrorCode.FILE_TOO_LARGE);
        }
    }

    private UploadRecorder.Uploaded send(long userId, long spaceId, String requestName, String name,
            MultipartFile part) throws IOException {
        String key = "spaces/" + spaceId + "/files/" + UUID.randomUUID();
        try (InputStream in = part.getInputStream()) {
            byte[] head = ContentTypeResolver.readHead(in);
            String detected = ContentTypeResolver.detect(head);
            try {
                storage.put(key, new SequenceInputStream(new ByteArrayInputStream(head), in), part.getSize(),
                        ContentTypeResolver.byName(name));
            } catch (StorageException e) {
                deleteQuietly(key);
                throw e;
            }
            return new UploadRecorder.Uploaded(requestName, name,
                    new DriveItem.Content(key, part.getSize(), detected, userId));
        }
    }

    private void deleteQuietly(String key) {
        try {
            storage.delete(key);
        } catch (RuntimeException e) {
            // 남은 객체는 고아 객체 정리 작업이 지운다
            log.warn("Could not delete uploaded object; orphan cleanup will remove it", e);
        }
    }
}
