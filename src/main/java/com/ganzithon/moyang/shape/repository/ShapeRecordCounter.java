package com.ganzithon.moyang.shape.repository;

import com.ganzithon.moyang.record.domain.ReflectionRecord;
import com.ganzithon.moyang.record.domain.ReflectionRecord.Status;
import jakarta.persistence.EntityManager;
import java.time.LocalDate;
import java.time.LocalDateTime;
import org.springframework.stereotype.Repository;

/**
 * 나의 모양이 기록(record) 도메인을 읽기만 하는 곳. 기록 쪽 코드는 수정하지 않고 엔티티만 조회한다.
 * completedAt · recordDate는 기록 서비스가 서울 시간으로 저장하므로 since도 서울 시간으로 넘겨야 한다.
 */
@Repository
public class ShapeRecordCounter {

    private final EntityManager em;

    public ShapeRecordCounter(EntityManager em) {
        this.em = em;
    }

    /** since 이후(같은 시각 포함)에 저장 완료된, 삭제되지 않은 내 기록 수. */
    public long countCompletedSince(long userId, LocalDateTime since) {
        return em.createQuery("select count(r) from ReflectionRecord r "
                        + "where r.userId = :uid and r.status = :status and r.completedAt >= :since", Long.class)
                .setParameter("uid", userId)
                .setParameter("status", Status.COMPLETED)
                .setParameter("since", since)
                .getSingleResult();
    }

    /** since 이후에 저장된 기록 중 date 날짜에 저장한 것이 있는지. */
    public boolean existsCompletedOn(long userId, LocalDate date, LocalDateTime since) {
        Long count = em.createQuery("select count(r) from ReflectionRecord r "
                        + "where r.userId = :uid and r.status = :status and r.recordDate = :date "
                        + "and r.completedAt >= :since", Long.class)
                .setParameter("uid", userId)
                .setParameter("status", Status.COMPLETED)
                .setParameter("date", date)
                .setParameter("since", since)
                .getSingleResult();
        return count > 0;
    }
}
