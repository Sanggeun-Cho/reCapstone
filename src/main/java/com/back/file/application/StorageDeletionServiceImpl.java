package com.back.file.application;

import com.back.file.domain.StorageDeletion;
import com.back.file.domain.StorageDeletionRepository;
import com.back.file.storage.FileStorage;
import java.time.Clock;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.transaction.support.TransactionTemplate;

@Slf4j
@Service
@Transactional(readOnly = true)
public class StorageDeletionServiceImpl implements StorageDeletionService {

    /** 대기열에 넣은 행. 커밋 후에 바로 처리한다. */
    public record Queued(List<Long> ids) {
    }

    private final StorageDeletionRepository repository;
    private final FileStorage storage;
    private final ApplicationEventPublisher events;
    private final Clock clock;
    private final TransactionTemplate newTransaction;

    public StorageDeletionServiceImpl(StorageDeletionRepository repository, FileStorage storage,
            ApplicationEventPublisher events, Clock clock, PlatformTransactionManager transactionManager) {
        this.repository = repository;
        this.storage = storage;
        this.events = events;
        this.clock = clock;
        this.newTransaction = new TransactionTemplate(transactionManager);
        this.newTransaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void enqueueKeys(Collection<String> storageKeys) {
        Instant now = clock.instant();
        save(storageKeys.stream().map(key -> StorageDeletion.ofKey(key, now)).toList());
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void enqueueSpaces(Collection<Long> spaceIds) {
        Instant now = clock.instant();
        save(spaceIds.stream().map(id -> StorageDeletion.ofPrefix(StorageDeletionService.spacePrefix(id), now)).toList());
    }

    private void save(List<StorageDeletion> deletions) {
        if (deletions.isEmpty()) {
            return;
        }
        List<Long> ids = repository.saveAll(deletions).stream().map(StorageDeletion::getId).toList();
        events.publishEvent(new Queued(ids));
    }

    /** 커밋된 뒤 요청 스레드에서 바로 지운다. 실패해도 요청은 성공으로 끝나고 예약 작업이 다시 시도한다. */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public void afterCommit(Queued queued) {
        queued.ids().forEach(this::process);
    }

    @Override
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public int retryPending() {
        List<Long> ids = repository.findAllIds();
        ids.forEach(this::process);
        return ids.size();
    }

    private void process(long id) {
        StorageDeletion deletion = newTransaction.execute(status -> repository.findById(id).orElse(null));
        if (deletion == null) {
            return;
        }
        try {
            if (deletion.getStorageKey() != null) {
                storage.delete(deletion.getStorageKey());
            } else {
                storage.deleteByPrefix(deletion.getKeyPrefix());
            }
            newTransaction.executeWithoutResult(status -> repository.deleteById(id));
        } catch (RuntimeException e) {
            log.warn("Storage deletion {} failed; will retry", id, e);
            newTransaction.executeWithoutResult(status -> repository.findById(id)
                    .ifPresent(row -> row.failed(e.getClass().getSimpleName() + ": " + e.getMessage())));
        }
    }
}
