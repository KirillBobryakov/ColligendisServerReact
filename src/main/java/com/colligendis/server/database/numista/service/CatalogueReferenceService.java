package com.colligendis.server.database.numista.service;

import java.util.Map;

import org.springframework.stereotype.Service;

import com.colligendis.server.database.AbstractService;
import com.colligendis.server.database.ColligendisUser;
import com.colligendis.server.database.exception.DatabaseException;
import com.colligendis.server.database.numista.model.Catalogue;
import com.colligendis.server.database.numista.model.CatalogueReference;
import com.colligendis.server.util.Either;

import reactor.core.publisher.Mono;

@Service
public class CatalogueReferenceService extends AbstractService {

	public Mono<Either<DatabaseException, CatalogueReference>> create(String number, Catalogue catalogue,
			ColligendisUser colligendisUser) {
		return super.createNode(new CatalogueReference(number), colligendisUser, CatalogueReference.class)
				.flatMap(catalogueReference -> catalogueReference.fold(
						error -> Mono.just(Either.left(error)),
						cr -> {
							return createReferenceToCatalogue(cr, catalogue, colligendisUser)
									.map(result -> result.fold(
											error -> Either.left(error),
											success -> Either.right(cr)));
						}));
	}

	public Mono<Either<DatabaseException, CatalogueReference>> findByNumberAndCatalogueCode(String number,
			String catalogueCode) {
		String query = "MATCH (n:CATALOGUE_REFERENCE)-[:REFERENCE_FROM]->(c:CATALOGUE) WHERE n.number = $number AND c.code = $catalogueCode RETURN n AS result";
		Map<String, Object> parameters = Map.of("number", number, "catalogueCode", catalogueCode);

		return super.readMonoQuery(query, parameters, CatalogueReference.class, "CatalogueReference not found",
				"Error while finding CatalogueReference by number and catalogueCode");
	}

	public Mono<Either<DatabaseException, Boolean>> createReferenceToCatalogue(
			CatalogueReference catalogueReference, Catalogue catalogue, ColligendisUser colligendisUser) {
		return super.createSingleRelationship(catalogueReference, catalogue,
				CatalogueReference.REFERENCE_FROM, colligendisUser);
	}
}
