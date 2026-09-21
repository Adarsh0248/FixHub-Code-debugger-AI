package com.razeef.bugbrother.tasks.repository;

import com.razeef.bugbrother.tasks.model.TaskEntity;
import com.razeef.bugbrother.tasks.model.TaskStatus;

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

    boolean existsByGenerationIdAndStatusNotIn(
            UUID generationId,
            Collection<TaskStatus> terminalStatuses
    );

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

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select task
            from TaskEntity task
            where task.generationId = :generationId
              and task.status not in :terminalStatuses
            order by task.createdAt asc
            """)
    List<TaskEntity> findLockedByGenerationIdAndStatusNotIn(
            @Param("generationId") UUID generationId,
            @Param("terminalStatuses")
            Collection<TaskStatus> terminalStatuses
    );

    @Query("""
            select (count(task) > 0)
            from TaskEntity task
            where task.generationId = :generationId
              and task.taskId <> :taskId
              and task.status not in :terminalStatuses
              and task.updatedAt >= :cutoff
            """)
    boolean existsCurrentTaskForGeneration(
            @Param("generationId") UUID generationId,
            @Param("taskId") UUID taskId,
            @Param("terminalStatuses")
            Collection<TaskStatus> terminalStatuses,
            @Param("cutoff") Instant cutoff
    );
}
