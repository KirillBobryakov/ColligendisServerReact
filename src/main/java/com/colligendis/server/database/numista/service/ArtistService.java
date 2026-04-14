package com.colligendis.server.database.numista.service;

import org.springframework.stereotype.Service;

import com.colligendis.server.database.AbstractService;
import com.colligendis.server.database.ColligendisUser;
import com.colligendis.server.database.numista.model.Artist;
import com.colligendis.server.database.result.CreateNodeExecutionStatus;
import com.colligendis.server.database.result.ExecutionResult;
import com.colligendis.server.database.result.FindExecutionStatus;
import com.colligendis.server.logger.BaseLogger;

import reactor.core.publisher.Mono;

@Service
public class ArtistService extends AbstractService {

	public Mono<ExecutionResult<Artist, CreateNodeExecutionStatus>> create(Artist artist,
			Mono<ColligendisUser> colligendisUserMono,
			BaseLogger baseLogger) {
		return colligendisUserMono
				.flatMap(colligendisUser -> super.createNode(artist, colligendisUser, Artist.class, baseLogger));
	}

	public Mono<ExecutionResult<Artist, FindExecutionStatus>> findByName(String name, BaseLogger baseLogger) {
		return super.findNodeByUniquePropertyValue("name", name, Artist.LABEL, Artist.class, baseLogger);
	}

}
