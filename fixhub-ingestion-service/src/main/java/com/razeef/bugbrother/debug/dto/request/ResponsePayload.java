package com.razeef.bugbrother.debug.dto.request;

import com.razeef.bugbrother.debug.model.DebugMode;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class ResponsePayload {

    private String userQ;
    private String branch;
    private DebugMode mode;
}
