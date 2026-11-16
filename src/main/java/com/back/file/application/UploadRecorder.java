package com.back.file.application;

import com.back.common.error.ErrorCode;
import com.back.file.api.FileDto;
import com.back.file.domain.DriveItem;
import com.back.file.domain.DriveItemRepository;
import com.back.file.event.FileUploaded;
import com.back.file.support.ItemNames;
import com.back.space.application.SpaceAccessPolicy;
import com.back.space.application.SpaceAccessPolicy.Action;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * 업로드의 기록 단계. 저장소 전송이 끝난 뒤 짧은 트랜잭션에서 스페이스 행을 잠그고, 담당자·인계 여부를 다시 확인한 뒤
 * 이름을 정하고 기록한다 (DESIGN_REVIEW.md 6.8 구현 메모).
 */
@Component
@RequiredArgsConstructor
class UploadRecorder {

    /** 저장소에 올린 파일 하나 */
    record Uploaded(String requestName, String name, DriveItem.Content content) {
    }

    /** 기록 결과. {@code rejected}는 기록하지 않은 파일이며, 그 객체는 호출한 쪽이 지운다 */
    record Result(List<FileDto.CreatedRes> created, List<FileDto.FailedRes> failed, List<String> rejectedKeys) {
    }

    private final DriveItemRepository repository;
    private final SpaceAccessPolicy accessPolicy;
    private final DriveItems items;
    private final ApplicationEventPublisher events;
    private final Clock clock;

    @Transactional
    public Result record(long userId, long spaceId, Long folderId, FileDto.OnConflict onConflict,
            List<Uploaded> uploads) {
        // 저장소 전송 중에 담당자가 바뀌었거나 인계가 시작되었으면 저장하지 않는다 (정책 8.2 제5항)
        accessPolicy.checkAndLock(userId, spaceId, Action.WRITE);
        items.requireNoHandover(spaceId);
        DriveItem folder = items.folderOrRoot(spaceId, folderId);
        int depth = DriveItems.depthUnder(folder);

        Instant now = clock.instant();
        Set<String> taken = new HashSet<>(repository.nameKeysIn(spaceId, folderId));
        // 같은 요청 안에서 이름이 겹치는 파일끼리는 onConflict와 관계없이 번호를 붙인다
        Set<String> requestKeys = new HashSet<>();
        List<FileDto.CreatedRes> created = new ArrayList<>();
        List<FileDto.FailedRes> failed = new ArrayList<>();
        List<String> rejected = new ArrayList<>();

        for (Uploaded upload : uploads) {
            String key = ItemNames.key(upload.name());
            boolean firstInRequest = requestKeys.add(key);
            if (onConflict == FileDto.OnConflict.REPLACE && firstInRequest) {
                Optional<DriveItem> existing = repository.findByName(spaceId, folderId, key);
                if (existing.isPresent()) {
                    DriveItem target = existing.get();
                    if (target.isFolder()) {
                        failed.add(new FileDto.FailedRes(upload.requestName(), ErrorCode.NAME_CONFLICT));
                        rejected.add(upload.content().storageKey());
                        continue;
                    }
                    replace(target, upload.content(), userId, now);
                    created.add(new FileDto.CreatedRes(target.getId(), target.getName(), true));
                    events.publishEvent(new FileUploaded(spaceId, target.getId()));
                    continue;
                }
            }
            String name = ItemNames.available(upload.name(), taken);
            DriveItem file = repository.save(DriveItem.file(spaceId, folderId, depth, name, upload.content()));
            taken.add(file.getNameKey());
            created.add(new FileDto.CreatedRes(file.getId(), file.getName(), false));
            events.publishEvent(new FileUploaded(spaceId, file.getId()));
        }
        repository.flush();
        return new Result(created, failed, rejected);
    }

    /** id를 유지하고 내용만 바꾼다. 이전 내용은 같은 이름의 새 휴지통 항목이 된다 (정책 8.4). */
    private void replace(DriveItem target, DriveItem.Content content, long userId, Instant now) {
        DriveItem previous = target.previousVersion(userId, now);
        target.replaceContent(content);
        // 저장소 키는 유일하다. 기존 항목의 키를 먼저 바꾼 뒤 이전 내용을 넣는다
        repository.flush();
        repository.save(previous);
    }
}
