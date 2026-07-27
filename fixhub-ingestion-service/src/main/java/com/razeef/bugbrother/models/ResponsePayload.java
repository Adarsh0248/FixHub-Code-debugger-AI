package com.razeef.bugbrother.models;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class ResponsePayload {

    private List<FixedFile> files;
    private String userQ;
}
