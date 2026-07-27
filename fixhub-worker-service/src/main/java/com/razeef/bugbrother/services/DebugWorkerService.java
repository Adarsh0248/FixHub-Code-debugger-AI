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

    @KafkaListener(topics = "code-guardian-tasks", groupId = "code-guardian-group")
    public void consumeTask(CodeGuardianTask task) {
        System.out.println("Received task for repository: " + task.getOwner() + "/" + task.getRepo());
        try {
            // 1. Ask AI for fixes
            String aiResponse = gitAiLayer.askAiDebug(task.getPayload().getFiles(), task.getPayload().getUserQ());

            if (aiResponse == null || aiResponse.trim().isEmpty()) {
                System.out.println("No debug response generated from AI.");
                return;
            }

            // 2. Parse fixed files
            List<CommitService.FixedFile> files = parser.parseFixedFiles(aiResponse);
            if (files.isEmpty()) {
                System.out.println("No valid files found in AI response.");
                return;
            }

            // 3. Commit fixes using token from task
            commitService.createFixBranchAndCommitWithLogging(task.getOwner(), task.getRepo(), files, task.getToken());
            System.out.println("Successfully processed and committed fixes for " + task.getOwner() + "/" + task.getRepo());

        } catch (Exception e) {
            System.err.println("Error processing task for " + task.getOwner() + "/" + task.getRepo() + ": " + e.getMessage());
            e.printStackTrace();
        }
    }
}
