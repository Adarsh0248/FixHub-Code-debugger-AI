package com.razeef.bugbrother.models;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class IndexRepoTask {
    private String owner;
    private String repo;
    private String token;
}
