package com.back.group.application;

import com.back.group.GroupProperties;
import com.back.group.api.GroupDto;
import jakarta.persistence.EntityManager;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 영구 삭제는 여러 테이블에 걸친 일괄 삭제라 네이티브 SQL로 한다. 추가 전용 이력 테이블의 DELETE는
 * 이 트랜잭션에서만 허용된다 ({@code app.purge}, V3 트리거 함수).
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PurgeServiceImpl implements PurgeService {

    private final EntityManager em;
    private final GroupProperties properties;
    private final Clock clock;

    @Override
    @Transactional
    public Result purgeExpired() {
        Instant cutoff = clock.instant().minus(properties.retention());
        em.createNativeQuery("SELECT set_config('app.purge', 'on', true)").getSingleResult();

        List<Long> groupIds = ids("SELECT id FROM groups WHERE deleted_at <= :cutoff", cutoff);
        // 그룹을 지우면 그 그룹의 스페이스도 함께 지운다. 따로 삭제된 스페이스는 자기 보관 기간을 따른다 (정책 2.6 제3항)
        List<Long> spaceIds = ids("""
                SELECT id FROM space
                WHERE deleted_at <= :cutoff OR group_id IN (SELECT id FROM groups WHERE deleted_at <= :cutoff)
                """, cutoff);

        if (!spaceIds.isEmpty()) {
            delete("DELETE FROM space_deletion_request WHERE space_id IN (:ids)", spaceIds);
            delete("DELETE FROM assignment_change WHERE space_id IN (:ids)", spaceIds);
            delete("DELETE FROM space WHERE id IN (:ids)", spaceIds);
        }
        if (!groupIds.isEmpty()) {
            delete("DELETE FROM invitation WHERE group_id IN (:ids)", groupIds);
            delete("DELETE FROM group_member_history WHERE group_id IN (:ids)", groupIds);
            delete("DELETE FROM group_member WHERE group_id IN (:ids)", groupIds);
            delete("DELETE FROM groups WHERE id IN (:ids)", groupIds);
        }
        int anonymized = anonymizeWithdrawnUsers();

        Result result = new Result(groupIds.size(), spaceIds.size(), anonymized);
        log.info("Purged archived data: {}", result);
        return result;
    }

    /**
     * 기록이 남은 그룹이 모두 영구 삭제된 탈퇴자의 이름과 이메일을 지운다 (정책 7.9).
     * 기록은 멤버 이력, 담당자 변경 이력, 그룹 생성·OWNER 지위다.
     */
    private int anonymizeWithdrawnUsers() {
        return em.createNativeQuery("""
                        UPDATE users u
                        SET name = :name, email = 'withdrawn-' || u.id || '@invalid', work_phone = NULL
                        WHERE u.deleted_at IS NOT NULL
                          AND u.email NOT LIKE 'withdrawn-%@invalid'
                          AND NOT EXISTS (SELECT 1 FROM group_member_history h WHERE u.id IN (h.user_id, h.actor_id))
                          AND NOT EXISTS (SELECT 1 FROM assignment_change c
                                          WHERE u.id IN (c.from_user_id, c.to_user_id, c.actor_id))
                          AND NOT EXISTS (SELECT 1 FROM groups g WHERE u.id IN (g.owner_id, g.created_by))
                        """)
                .setParameter("name", GroupDto.WITHDRAWN_USER_NAME)
                .executeUpdate();
    }

    @SuppressWarnings("unchecked")
    private List<Long> ids(String sql, Instant cutoff) {
        return ((List<Number>) em.createNativeQuery(sql).setParameter("cutoff", cutoff).getResultList())
                .stream()
                .map(Number::longValue)
                .toList();
    }

    private void delete(String sql, List<Long> ids) {
        em.createNativeQuery(sql).setParameter("ids", ids).executeUpdate();
    }
}
