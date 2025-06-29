package com.razeef.BugBrother.models;

import com.fasterxml.jackson.annotation.JsonProperty;

public class GitTokenResponse {

    @JsonProperty("access_token")
    private String accessToken;

    @JsonProperty("token_type")
    private String tokenType;

    private String scope;
}
