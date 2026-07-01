package com.colligendis.server.dto;

import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class MeResponse {
	private String username;
	private String email;
	private List<String> roles;
}
