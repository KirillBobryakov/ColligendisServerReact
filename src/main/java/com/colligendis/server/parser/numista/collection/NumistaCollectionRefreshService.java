package com.colligendis.server.parser.numista.collection;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import com.colligendis.server.database.ColligendisUser;
import com.colligendis.server.database.numista.model.NType;
import com.colligendis.server.database.numista.model.NumistaCollectionItem;
import com.colligendis.server.database.numista.service.NTypeService;
import com.colligendis.server.database.result.ExecutionResult;
import com.colligendis.server.database.result.ExecutionStatuses;
import com.colligendis.server.database.result.ExecutionStatusCoercion;
import com.colligendis.server.database.result.FindExecutionStatus;
import com.colligendis.server.logger.BaseLogger;
import com.colligendis.server.parser.numista.CloudflareBlockException;
import com.colligendis.server.parser.numista.NumistaPipeline;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Slf4j
@Service
@RequiredArgsConstructor
public class NumistaCollectionRefreshService {

	private static final int REPARSE_THRESHOLD_DAYS = 30;

	private final NumistaCollectionClient collectionClient;
	private final NumistaCollectionPageParser pageParser;
	private final NumistaCollectionSaveService collectionSaveService;
	private final NTypeService nTypeService;
	private final NumistaPipeline numistaPipeline;

	public Mono<List<NumistaCollectionItem>> refreshFromNumistaPage(
			String issuerNumistaCode,
			Mono<ColligendisUser> colligendisUserMono,
			BaseLogger baseLogger) {
		String issuer = issuerNumistaCode == null ? "" : issuerNumistaCode.trim();
		if (!StringUtils.hasText(issuer)) {
			return Mono.error(new IllegalArgumentException("issuerNumistaCode is required"));
		}

		log.info(
				"Numista collection refresh: fetching https://en.numista.com/vous/vos_pieces.php?issuer={}",
				issuer);

		return colligendisUserMono.flatMap(user -> collectionClient.fetchCollectionPage(issuer, user)
				.flatMapMany(html -> {
					List<NumistaCollectionSaveResponse> parsed = pageParser.parse(html, issuer);
					if (parsed.isEmpty()) {
						log.info("Numista collection refresh: no items parsed for issuer={}", issuer);
					}
					return Flux.fromIterable(parsed);
				})
				.concatMap(response -> ensureNTypeParsed(response.getCoinId(), baseLogger)
						.then(collectionSaveService.persistParsedResponse(response, Mono.just(user), baseLogger))
						.flatMap(this::extractSavedItem))
				.collectList());
	}

	/**
	 * Fetches ALL pages of the user's full collection (all issuers combined) from
	 * {@code vos_pieces.php?issuer=&swap=3&type=&page=N}, checks / re-parses each
	 * referenced NType, and persists all collection items to the database.
	 */
	public Mono<List<NumistaCollectionItem>> refreshFullCollectionFromNumista(
			Mono<ColligendisUser> colligendisUserMono,
			BaseLogger baseLogger) {
		return colligendisUserMono.flatMap(user -> collectionClient.fetchAllCollectionPage(1, user)
				.flatMap(firstPageHtml -> {
					int maxPage = pageParser.extractMaxPageCount(firstPageHtml);
					log.info("Numista full collection refresh: total pages={}", maxPage);

					List<NumistaCollectionSaveResponse> page1Items = pageParser.parse(firstPageHtml, null);

					Flux<NumistaCollectionSaveResponse> page1Flux = Flux.fromIterable(page1Items);

					Flux<NumistaCollectionSaveResponse> remainingFlux = maxPage > 1
							? Flux.range(2, maxPage - 1)
									.concatMap(page -> collectionClient.fetchAllCollectionPage(page, user)
											.flatMapMany(html -> Flux
													.fromIterable(pageParser.parse(html, null))))
							: Flux.empty();

					return Flux.concat(page1Flux, remainingFlux)
							.concatMap(response -> ensureNTypeParsed(response.getCoinId(), baseLogger)
									.then(collectionSaveService.persistParsedResponse(
											response, Mono.just(user), baseLogger))
									.flatMap(this::extractSavedItem))
							.collectList();
				}));
	}

	/**
	 * Ensures the NType for {@code coinId} exists in the database and was parsed
	 * within the last {@value #REPARSE_THRESHOLD_DAYS} days. If not found or
	 * stale, re-parses it from Numista before continuing.
	 * <p>
	 * If Numista returns a Cloudflare challenge, or if the pipeline fails for any
	 * reason, the error is logged and swallowed so that the collection-item persist
	 * step is still attempted with whatever NType data already exists in the DB.
	 */
	private Mono<Void> ensureNTypeParsed(String coinId, BaseLogger baseLogger) {
		if (!StringUtils.hasText(coinId)) {
			return Mono.empty();
		}
		return nTypeService.findByNid(coinId, baseLogger)
				.flatMap(result -> {
					if (result.getStatus() != FindExecutionStatus.FOUND || result.getNode() == null) {
						log.info("Collection refresh: ntype not found, parsing nid={}", coinId);
						return numistaPipeline.pipeline(coinId).then();
					}
					if (isParsingDateStale(result.getNode())) {
						log.info("Collection refresh: ntype parsingDate stale, reparsing nid={} parsingDate={}",
								coinId, result.getNode().getParsingDate());
						return numistaPipeline.pipeline(coinId).then();
					}
					return Mono.empty();
				})
				.onErrorResume(CloudflareBlockException.class, e -> {
					log.warn("Collection refresh: Cloudflare blocked ntype parse for nid={} — " +
							"skipping reparse, will persist collection item with existing DB data. " +
							"Consider refreshing your Numista cookie (cf_clearance).", coinId);
					return Mono.empty();
				})
				.onErrorResume(e -> {
					log.warn("Collection refresh: ntype parse failed for nid={} ({}), " +
							"skipping and continuing with remaining items.", coinId, e.getMessage());
					return Mono.empty();
				});
	}

	private static boolean isParsingDateStale(NType ntype) {
		String parsingDate = ntype.getParsingDate();
		if (!StringUtils.hasText(parsingDate)) {
			return true;
		}
		try {
			LocalDate parsed = LocalDate.parse(parsingDate);
			return ChronoUnit.DAYS.between(parsed, LocalDate.now()) > REPARSE_THRESHOLD_DAYS;
		} catch (Exception e) {
			return true;
		}
	}

	private Mono<NumistaCollectionItem> extractSavedItem(
			ExecutionResult<NumistaCollectionItem, ExecutionStatuses> result) {
		if (ExecutionStatusCoercion.isCollectionItemPersistSuccess(result.statusEnum())) {
			NumistaCollectionItem node = result.getNode();
			if (node != null) {
				return Mono.just(node);
			}
		}
		result.logError(new BaseLogger());
		return Mono.empty();
	}
}
