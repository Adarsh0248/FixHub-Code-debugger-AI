package com.razeef.bugbrother.tasks.repository;

import com.razeef.bugbrother.tasks.model.ProcessedTaskEvent;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface ProcessedTaskEventRepository
        extends JpaRepository<ProcessedTaskEvent, UUID> {
}