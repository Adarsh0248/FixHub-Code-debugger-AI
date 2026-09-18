package com.razeef.bugbrother.debug.model;

import com.razeef.bugbrother.debug.dto.request.ResponsePayload;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class CodeGuardianTask {
    private String owner;
    private String repo;
    private ResponsePayload payload;
    private String token;
}
