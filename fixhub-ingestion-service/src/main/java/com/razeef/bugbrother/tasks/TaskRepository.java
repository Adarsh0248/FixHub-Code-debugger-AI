package com.razeef.bugbrother.tasks;

import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface TaskRepository extends JpaRepository<TaskEntity, UUID> {

    Optional<TaskEntity> findByTaskIdAndUserId(
            UUID taskId,
            String userId
    );

    List<TaskEntity> findByUserIdOrderByUpdatedAtDesc(
            String userId,
            Pageable pageable
    );

    List<TaskEntity> findByUserIdAndOwnerAndRepoOrderByUpdatedAtDesc(
            String userId,
            String owner,
            String repo,
            Pageable pageable
    );

    List<TaskEntity> findByStatusNotInAndUpdatedAtBefore(
            Collection<TaskStatus> statuses,
            Instant cutoff
    );

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            SELECT task
            FROM TaskEntity task
            WHERE task.taskId = :taskId
            """)
    Optional<TaskEntity> findLockedByTaskId(
            @Param("taskId") UUID taskId
    );
}