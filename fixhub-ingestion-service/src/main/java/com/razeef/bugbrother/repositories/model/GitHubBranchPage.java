package com.razeef.bugbrother.repositories.model;

import java.util.List;

public record GitHubBranchPage(
        List<GitHubBranchData> branches,
        int page,
        int pageSize,
        boolean hasNext
) {

    public GitHubBranchPage {
        branches = List.copyOf(branches);
    }
}