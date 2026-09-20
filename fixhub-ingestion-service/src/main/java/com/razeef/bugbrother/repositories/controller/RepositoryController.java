package com.razeef.bugbrother.repositories.controller;

import com.razeef.bugbrother.repositories.dto.response.RepositoryResponse;
import com.razeef.bugbrother.repositories.model.GitHubBranchPage;
import com.razeef.bugbrother.repositories.service.RepositorySelectionService;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/repositories")
public class RepositoryController {

    private final RepositorySelectionService selectionService;

    public RepositoryController(
            RepositorySelectionService selectionService
    ) {
        this.selectionService = selectionService;
    }

    @PostMapping("/{owner}/{repo}/resolve")
    public ResponseEntity<RepositoryResponse> resolveRepository(
            @PathVariable String owner,
            @PathVariable String repo
    ) {
        RepositoryResponse repository =
                selectionService.resolveRepository(
                        owner,
                        repo
                );

        return ResponseEntity.ok(repository);
    }

    @GetMapping("/{owner}/{repo}/branches")
    public ResponseEntity<GitHubBranchPage> listBranches(
            @PathVariable String owner,
            @PathVariable String repo,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "100") int pageSize
    ) {
        return ResponseEntity.ok(selectionService.listBranches(
                owner,
                repo,
                page,
                pageSize
        ));
    }
}
