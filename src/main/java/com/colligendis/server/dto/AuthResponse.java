package com.colligendis.server.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class AuthResponse {
    @JsonProperty("token")
    private String token;

    @JsonProperty("refreshToken")
    private String refreshToken;

    @JsonProperty("type")
    private String type = "Bearer";

    @JsonProperty("username")
    private String username;

    public AuthResponse(String token, String refreshToken, String username) {
        this.token = token;
        this.refreshToken = refreshToken;
        this.username = username;
        this.type = "Bearer";
    }
}
