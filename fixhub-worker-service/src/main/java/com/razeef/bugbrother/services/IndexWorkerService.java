package com.razeef.bugbrother.services;

import com.razeef.bugbrother.models.IndexRepoTask;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;

@Service
public class IndexWorkerService {

    @Autowired
    private VectorSearchService vectorSearchService;

    @KafkaListener(topics = "code-guardian-index-tasks", groupId = "code-guardian-group")
    public void consumeIndexTask(IndexRepoTask task) {
        System.out.println("Indexing repository: " + task.getOwner() + "/" + task.getRepo());
        vectorSearchService.indexRepoFiles(task.getOwner(), task.getRepo(), task.getToken());
        System.out.println("Finished indexing " + task.getOwner() + "/" + task.getRepo());
    }
}
