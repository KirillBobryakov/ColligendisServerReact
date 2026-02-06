package com.colligendis.server.database.numista.service;

import org.springframework.stereotype.Service;

import com.colligendis.server.database.AbstractService;
import com.colligendis.server.database.ColligendisUser;
import com.colligendis.server.database.exception.DatabaseException;
import com.colligendis.server.database.exception.NotFoundError;
import com.colligendis.server.database.numista.model.Author;
import com.colligendis.server.util.Either;

import reactor.core.publisher.Mono;

@Service
public class AuthorService extends AbstractService {

	public Mono<Either<DatabaseException, Author>> save(Author author, ColligendisUser user) {
		return super.createNode(author, user, Author.class);
	}

	public Mono<Either<DatabaseException, Author>> findByCode(String code) {
		return super.findNodeByUniquePropertyValue("code", code, Author.LABEL, Author.class);
	}

	public Mono<Either<DatabaseException, Author>> findByCodeWithSave(String code, String name,
			ColligendisUser colligendisUser) {
		return findByCode(code).flatMap(foundAuthor -> {
			return foundAuthor.fold(
					error -> {
						if (error instanceof NotFoundError) {
							return save(new Author(code, name), colligendisUser);
						}
						return Mono.just(Either.left(error));
					},
					author -> Mono.just(Either.right(author)));
		});
	}
}
