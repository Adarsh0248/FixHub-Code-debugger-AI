package com.razeef.bugbrother.tasks.repository;

import com.razeef.bugbrother.tasks.model.TaskCommandOutboxEntity;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface TaskCommandOutboxRepository
        extends JpaRepository<TaskCommandOutboxEntity, UUID> {

    @Query("""
            select entry.outboxId from TaskCommandOutboxEntity entry
            where entry.publishedAt is null and entry.nextAttemptAt <= :now
            order by entry.createdAt asc
            """)
    List<UUID> findDueIds(@Param("now") Instant now, Pageable pageable);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select entry from TaskCommandOutboxEntity entry
            where entry.outboxId = :outboxId
            """)
    Optional<TaskCommandOutboxEntity> findLockedById(
            @Param("outboxId") UUID outboxId);
}
