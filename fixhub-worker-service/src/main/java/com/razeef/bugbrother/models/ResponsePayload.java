package com.razeef.bugbrother.models;

import com.razeef.bugbrother.services.CommitService;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;


@Data
@AllArgsConstructor
@NoArgsConstructor
public class ResponsePayload {

    private List<CommitService.FixedFile> files;
    private String userQ;
}
