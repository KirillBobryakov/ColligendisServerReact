package com.colligendis.server.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class NumistaCookieRequest {

    @NotBlank(message = "Cookie is required")
    private String cookie;
}
