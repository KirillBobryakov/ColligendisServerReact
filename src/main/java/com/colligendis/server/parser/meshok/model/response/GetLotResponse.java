package com.colligendis.server.parser.meshok.model.response;

import com.colligendis.server.parser.meshok.model.Lot;

import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class GetLotResponse {
	String correlationId;
	Lot result;

}
