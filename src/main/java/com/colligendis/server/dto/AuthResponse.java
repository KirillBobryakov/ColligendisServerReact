package com.colligendis.server.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class AuthResponse {
    private String token;
    private String refreshToken;
    private String type = "Bearer";
    private String username;
    
    public AuthResponse(String token, String refreshToken, String username) {
        this.token = token;
        this.refreshToken = refreshToken;
        this.username = username;
    }
}
