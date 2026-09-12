package com.razeef.bugbrother.services;

import com.razeef.bugbrother.Wrappers.GitAiLayer;
import com.razeef.bugbrother.models.CodeGuardianTask;
import com.razeef.bugbrother.parsers.FixedfileParser;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class DebugWorkerService {

    @Autowired
    private GitAiLayer gitAiLayer;

    @Autowired
    private FixedfileParser parser;

    @Autowired
    private CommitService commitService;

    @Autowired
    private VectorSearchService vectorSearchService;

    private static final int RELATED_FILES_LIMIT = 5;

    @KafkaListener(topics = "code-guardian-tasks", groupId = "code-guardian-group")
    public void consumeTask(CodeGuardianTask task) {
        System.out.println("Received task for repository: " + task.getOwner() + "/" + task.getRepo());
        try {

            List<CommitService.FixedFile> relatedFiles = vectorSearchService.searchRelated(
                    task.getOwner(), task.getRepo(), task.getToken(),
                    task.getPayload().getUserQ(), List.of(), RELATED_FILES_LIMIT);

            if (relatedFiles.isEmpty()) {
                System.out.println("No related files found via RAG for " + task.getOwner() + "/" + task.getRepo()
                        + " -- index the repo first (POST /api/repos/{owner}/{repo}/index) or refine the error description.");
                return;
            }

            String aiResponse = gitAiLayer.askAiDebug(relatedFiles, task.getPayload().getUserQ());

            if (aiResponse == null || aiResponse.trim().isEmpty()) {
                System.out.println("No debug response generated from AI.");
                return;
            }


            List<CommitService.FixedFile> files = parser.parseFixedFiles(aiResponse);
            if (files.isEmpty()) {
                System.out.println("No valid files found in AI response.");
                return;
            }


            commitService.createFixBranchAndCommitWithLogging(task.getOwner(), task.getRepo(), files, task.getToken());
            System.out.println("Successfully processed and committed fixes for " + task.getOwner() + "/" + task.getRepo());

            vectorSearchService.indexRepoFiles(task.getOwner(), task.getRepo(), task.getToken());

        } catch (Exception e) {
            System.err.println("Error processing task for " + task.getOwner() + "/" + task.getRepo() + ": " + e.getMessage());
            e.printStackTrace();
        }
    }
}
