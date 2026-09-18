package com.razeef.bugbrother.tasks;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface ProcessedTaskEventRepository
        extends JpaRepository<ProcessedTaskEvent, UUID> {
}