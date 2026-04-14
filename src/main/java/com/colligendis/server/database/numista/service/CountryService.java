package com.colligendis.server.database.numista.service;

import org.springframework.stereotype.Service;

import com.colligendis.server.database.AbstractService;
import com.colligendis.server.database.ColligendisUser;
import com.colligendis.server.database.numista.model.Country;
import com.colligendis.server.database.result.CreateNodeExecutionStatus;
import com.colligendis.server.database.result.ExecutionResult;
import com.colligendis.server.database.result.FindExecutionStatus;
import com.colligendis.server.logger.BaseLogger;

import reactor.core.publisher.Mono;

@Service
public class CountryService extends AbstractService {

	public Mono<ExecutionResult<Country, CreateNodeExecutionStatus>> create(Country country,
			Mono<ColligendisUser> colligendisUserMono,
			BaseLogger baseLogger) {
		return colligendisUserMono
				.flatMap(colligendisUser -> super.createNode(country, colligendisUser, Country.class, baseLogger));
	}

	public Mono<ExecutionResult<Country, FindExecutionStatus>> findByNumistaCode(String numistaCode,
			BaseLogger baseLogger) {
		return super.findNodeByUniquePropertyValue("numistaCode", numistaCode, Country.LABEL, Country.class,
				baseLogger);
	}

}
