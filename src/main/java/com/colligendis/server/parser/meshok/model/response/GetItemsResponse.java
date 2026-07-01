package com.colligendis.server.parser.meshok.model.response;

import com.colligendis.server.parser.meshok.model.Result;

import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class GetItemsResponse {
	String correlationId;
	Result result;

}
