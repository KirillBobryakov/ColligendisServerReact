package com.colligendis.server.parser.numista;

import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.UnaryOperator;
import java.util.stream.Stream;

import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;

import com.colligendis.server.database.ColligendisUser;
import com.colligendis.server.database.N4JUtil;
import com.colligendis.server.database.exception.DatabaseException;
import com.colligendis.server.database.numista.model.CollectibleType;
import com.colligendis.server.database.numista.model.Currency;
import com.colligendis.server.database.numista.model.Denomination;
import com.colligendis.server.database.numista.model.Issuer;
import com.colligendis.server.database.numista.model.NType;
import com.colligendis.server.parser.AbstractPageParser;
import com.colligendis.server.parser.ParsingStatus;
import com.colligendis.server.util.Either;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Mono;

@Slf4j
@Data
@EqualsAndHashCode(callSuper = false)
public class NumistaPage {

	public static final String TYPE_PAGE_PREFIX = "https://en.numista.com/catalogue/contributions/modifier.php?id=";

	public String nid;

	public String url;
	public Document page;

	public ParsingStatus parsingStatus = ParsingStatus.NOT_CHANGED;

	public NType nType = null;
	public CollectibleType collectibleType = null;
	public Mono<Either<DatabaseException, CollectibleType>> collectibleTypeMono = null;

	public Issuer issuer = null;
	public Mono<Either<DatabaseException, Issuer>> issuerMono = null;

	public ColligendisUser colligendisUser = null;

	public Mono<ColligendisUser> colligendisUserM = null;

	public Mono<Either<DatabaseException, ColligendisUser>> colligendisUserMono = null;

	public Currency currency = null;
	public Denomination denomination = null;

	public NumistaPage(String nid) {
		this.nid = nid;
		this.url = TYPE_PAGE_PREFIX + nid;

		this.colligendisUserMono = Mono.defer(() -> N4JUtil
				.getInstance().numistaServices.colligendisUserService
				.findByUuid("b4205781-58b8-488c-9c5a-0e891483bca1"))
				.cache();

		this.colligendisUserM = colligendisUserMono.map(either -> either.fold(
				(error) -> {
					log.error("Error finding colligendis user: {}", error.message());
					return null;
				},
				(colligendisUser) -> {
					return colligendisUser;
				}));

		this.colligendisUser = colligendisUserMono.block().fold(
				(error) -> {
					log.error("Error finding colligendis user: {}", error.message());
					return null;
				},
				(colligendisUser) -> {
					return colligendisUser;
				});

	}

	public static Mono<NumistaPage> create(String nid) {
		return Mono.defer(() -> Mono.just(new NumistaPage(nid)));
	}

	// .andThen(pageParser.mintageParser)
	// .andThen(pageParser.technicalDataParser)
	// .andThen(pageParser.obverseParser)
	// .andThen(pageParser.reverseParser)
	// .andThen(pageParser.edgeParser)
	// .andThen(pageParser.watermarkParser)
	// .andThen(pageParser.mintsParser)
	// .andThen(pageParser.printerParser);

}
