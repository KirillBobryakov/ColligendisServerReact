package com.colligendis.server.database.numista.service;

import java.util.List;

import org.springframework.stereotype.Service;

import com.colligendis.server.database.AbstractService;
import com.colligendis.server.database.ColligendisUser;
import com.colligendis.server.database.exception.DatabaseException;
import com.colligendis.server.database.exception.NotFoundError;
import com.colligendis.server.database.numista.model.Author;
import com.colligendis.server.database.numista.model.Catalogue;
import com.colligendis.server.util.Either;

import reactor.core.publisher.Mono;

@Service
public class CatalogueService extends AbstractService {

	public Mono<Either<DatabaseException, Catalogue>> save(Catalogue catalogue, ColligendisUser colligendisUser) {
		return super.createNode(catalogue, colligendisUser, Catalogue.class);
	}

	public Mono<Either<DatabaseException, Catalogue>> findByNid(String nid) {
		return super.findNodeByUniquePropertyValue("nid", nid, Catalogue.LABEL, Catalogue.class);
	}

	public Mono<Either<DatabaseException, Catalogue>> findByCode(String code) {
		return super.findNodeByUniquePropertyValue("code", code, Catalogue.LABEL, Catalogue.class);
	}

	public Mono<Either<DatabaseException, Catalogue>> findByNidWithSave(String nid, String code,
			ColligendisUser colligendisUser) {
		return findByNid(nid).flatMap(foundCatalogue -> {
			return foundCatalogue.fold(
					error -> {
						if (error instanceof NotFoundError) {
							return save(new Catalogue(nid, code), colligendisUser);
						}
						return Mono.just(Either.left(error));
					},
					catalogue -> Mono.just(Either.right(catalogue)));
		});
	}

	public Mono<Either<DatabaseException, Boolean>> setAuthors(Catalogue catalogue, List<Author> authors,
			ColligendisUser colligendisUser) {
		return super.createUniqueOutgoingRelationships(catalogue, authors, Author.class,
				Catalogue.WRITTEN_BY,
				colligendisUser);
	}
}
