package com.colligendis.server.parser.numista;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.springframework.stereotype.Component;

import com.colligendis.server.database.ColligendisUser;
import com.colligendis.server.database.numista.model.Issuer;
import com.colligendis.server.database.numista.model.IssuingEntity;
import com.colligendis.server.database.numista.service.IssuingEntityService;
import com.colligendis.server.database.numista.service.NTypeService;
import com.colligendis.server.database.result.CreateNodeExecutionStatus;
import com.colligendis.server.database.result.CreateRelationshipExecutionStatus;
import com.colligendis.server.database.result.FindExecutionStatus;
import com.colligendis.server.parser.PauseLock;
import com.colligendis.server.parser.numista.exception.ParserException;

import lombok.RequiredArgsConstructor;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

@Component
@RequiredArgsConstructor
public class IssuingEntityParser extends Parser {

	private static final String ISSUING_ENTITIES_URL_PREFIX = "https://en.numista.com/catalogue/get_issuing_entities.php?prefill=&country=";

	private static final PauseLock PAUSE_LOCK = new PauseLock("IssuingEntityParser");

	// Cached services - lazily initialized
	private final IssuingEntityService issuingEntityService;
	private final NTypeService nTypeService;

	@Override
	protected Mono<NumistaPage> parse(NumistaPage numistaPage) {
		return Mono.defer(() -> parseIssuingEntities(numistaPage));
	}

	private Mono<NumistaPage> parseIssuingEntities(NumistaPage numistaPage) {
		List<String> codes = extractIssuingEntityCodes(numistaPage.page);

		if (codes.isEmpty()) {
			numistaPage.getPipelineStepLogger()
					.info("IssuingEntities: not found for nid: {} - Can't find IssuingEntities on the page",
							numistaPage.nid);
			return Mono.just(numistaPage);
		}

		return Flux.fromIterable(codes)
				.flatMap(code -> processIssuingEntityCode(code, numistaPage))
				.collectList()
				.flatMap(entities -> linkEntitiesToNType(entities, numistaPage))
				.thenReturn(numistaPage);
	}

	private List<String> extractIssuingEntityCodes(Document page) {
		Elements scripts = page.select("script");

		for (Element script : scripts) {
			if (script.childNodes().isEmpty()) {
				continue;
			}

			List<String> codes = Arrays.stream(script.childNodes().get(0).toString().split("\n"))
					.filter(line -> line.contains("$.get(\"../get_issuing_entities.php\""))
					.map(this::extractPrefillValue)
					.filter(s -> !s.isEmpty())
					.toList();

			if (!codes.isEmpty()) {
				return codes;
			}
		}
		return List.of();
	}

	private String extractPrefillValue(String line) {
		int start = line.indexOf("prefill:") + 10;
		int end = line.indexOf("\"})");
		return (start > 10 && end > start) ? line.substring(start, end) : "";
	}

	private Mono<IssuingEntity> processIssuingEntityCode(String nid, NumistaPage numistaPage) {
		return PAUSE_LOCK.awaitIfPaused()
				.then(issuingEntityService.findByNid(nid, numistaPage.getPipelineStepLogger()))
				.flatMap(executionResult -> {
					switch (executionResult.getStatus()) {
						case FOUND:
							return Mono.just(executionResult.getNode());
						default:
							numistaPage.getPipelineStepLogger().error("Failed to find IssuingEntity: {}",
									executionResult.getStatus());
							executionResult.logError(numistaPage.getPipelineStepLogger());
							return handleEntityNotFound(nid, numistaPage);
					}
				});
	}

	private Mono<IssuingEntity> handleEntityNotFound(String nid, NumistaPage numistaPage) {

		boolean acquiredLock = PAUSE_LOCK.pause();

		if (!acquiredLock) {
			// Another thread is already loading - wait and retry
			return PAUSE_LOCK.awaitIfPaused()
					.then(issuingEntityService.findByNid(nid, numistaPage.getPipelineStepLogger()))
					.flatMap(executionResult -> {
						switch (executionResult.getStatus()) {
							case FOUND:
								return Mono.just(executionResult.getNode());
							default:
								numistaPage.getPipelineStepLogger().error("Failed to find IssuingEntity: {}",
										executionResult.getStatus());
								executionResult.logError(numistaPage.getPipelineStepLogger());
								return Mono.error(new ParserException("Failed to find IssuingEntity: " + nid));
						}
					});
		}

		// This thread acquired the lock - load entities
		return parseIssuingEntitiesByIssuerCodeFromPHPRequestMono(numistaPage.issuer,
				numistaPage.getNumistaParserUserMono(), numistaPage)
				.subscribeOn(Schedulers.boundedElastic())
				.doFinally(signal -> PAUSE_LOCK.resume())
				.then(issuingEntityService.findByNid(nid, numistaPage.getPipelineStepLogger()))
				.flatMap(executionResult -> {
					switch (executionResult.getStatus()) {
						case FOUND:
							return Mono.just(executionResult.getNode());
						default:
							numistaPage.getPipelineStepLogger().error("Failed to find IssuingEntity: {}",
									executionResult.getStatus());
							executionResult.logError(numistaPage.getPipelineStepLogger());
							return Mono.error(new ParserException("Failed to find IssuingEntity: " + nid));
					}
				});
	}

	private Mono<Void> linkEntitiesToNType(List<IssuingEntity> entities,
			NumistaPage numistaPage) {
		if (numistaPage.nType == null || entities.isEmpty()) {
			return Mono.empty();
		}

		return nTypeService.setIssuingEntities(
				numistaPage.nType,
				entities,
				numistaPage.getNumistaParserUserMono(),
				numistaPage.getPipelineStepLogger())
				// .flatMap(executionResult -> {

				// if
				// (executionResult.getStatus().equals(ExecutionStatuses.RELATIONSHIP_IS_EXISTS))
				// {
				// numistaPage.getPipelineStepLogger()
				// .info("IssuingEntities: {}",
				// entities.stream().map(IssuingEntity::getName).collect(Collectors.joining(",")));
				// return Mono.just(numistaPage);
				// }
				// if
				// (!executionResult.getStatus().equals(ExecutionStatuses.RELATIONSHIP_WAS_CREATED))
				// {
				// numistaPage.getPipelineStepLogger()
				// .error("IssuingEntities error while linking to NType for nid: {} and issuing
				// entities names: {} - Error: {}",
				// numistaPage.nid, entities.stream().map(IssuingEntity::getName)
				// .collect(Collectors.joining(",")),
				// executionResult.getStatus());
				// return Mono.<ExecutionStatuses>error(new ParserException(
				// "Failed to link issuing entities to NType: " + numistaPage.nid));
				// }
				// numistaPage.getPipelineStepLogger()
				// .warning("IssuingEntities: set to {}",
				// numistaPage.nid, entities.stream().map(IssuingEntity::getName)
				// .collect(Collectors.joining(",")));
				// return Mono.just(numistaPage);
				// })
				.then();
	}

	private Mono<Boolean> parseIssuingEntitiesByIssuerCodeFromPHPRequestMono(Issuer issuer,
			Mono<ColligendisUser> colligendisUserMono, NumistaPage numistaPage) {

		String url = ISSUING_ENTITIES_URL_PREFIX + issuer.getNumistaCode();

		return Mono.fromCallable(() -> NumistaParseUtils.loadPageByURL(url))
				.subscribeOn(Schedulers.boundedElastic())
				.flatMap(document -> {
					if (document == null) {
						return Mono.error(new RuntimeException("Can't load PHP IssuingEntities from URL: " + url));
					}

					if (!document.select("optgroup").isEmpty()) {
						numistaPage.getPipelineStepLogger()
								.error("Found OPTGROUP in IssuingEntities for issuer: {}",
										issuer.getNumistaCode());
						return Mono.just(false);
					}

					Elements options = document.select("option");
					return Flux.fromIterable(options)
							.concatMap(option -> processOption(option, issuer, colligendisUserMono, numistaPage))
							.collectList()
							.flatMap(ie -> linkIssuingEntitiesToIssuer(ie, issuer, colligendisUserMono, numistaPage));
				});
	}

	private Mono<Boolean> linkIssuingEntitiesToIssuer(List<IssuingEntity> issuingEntities, Issuer issuer,
			Mono<ColligendisUser> user, NumistaPage numistaPage) {
		if (issuingEntities.isEmpty()) {
			return Mono.just(true);
		}
		return issuingEntityService.setIssuer(issuer, issuingEntities, user, numistaPage.getPipelineStepLogger())
				.flatMap(executionResult -> {
					if (!executionResult.getStatus().equals(CreateRelationshipExecutionStatus.WAS_CREATED)) {
						numistaPage.getPipelineStepLogger()
								.error("IssuingEntities error while linking to Issuer for nid: {} and issuing entities names: {} - Error: {}",
										numistaPage.nid, issuingEntities.stream().map(IssuingEntity::getName)
												.collect(Collectors.joining(",")),
										executionResult.getStatus());
						return Mono.error(new ParserException(
								"Failed to link issuing entities to issuer: " + issuer.getNumistaCode()));
					}
					numistaPage.getPipelineStepLogger()
							.info("IssuingEntities: (ISSUING_ENTITY {nid: {nid}}) -[:ISSUES_WHEN_BEEN]-> (ISSUING_ENTITY {name: {name}})",
									numistaPage.nid, issuingEntities.stream().map(IssuingEntity::getName)
											.collect(Collectors.joining(",")),
									issuer.getName());
					return Mono.just(true);
				})
				.thenReturn(true);
	}

	private Mono<IssuingEntity> processOption(Element option, Issuer issuer,
			Mono<ColligendisUser> colligendisUserMono, NumistaPage numistaPage) {

		String code = option.attr("value");
		String name = option.text();
		return issuingEntityService.findByNidWithCreate(code, name, colligendisUserMono,
				numistaPage.getPipelineStepLogger())
				.flatMap(executionResult -> {
					if (executionResult.getStatus().equals(FindExecutionStatus.FOUND)
							|| executionResult.getStatus().equals(CreateNodeExecutionStatus.WAS_CREATED)) {
						return Mono.just(executionResult.getNode());
					} else {
						numistaPage.getPipelineStepLogger().error("Failed to find IssuingEntity: {}",
								executionResult.getStatus());
						executionResult.logError(numistaPage.getPipelineStepLogger());
						return Mono.error(new ParserException("Failed to find IssuingEntity: " + code));
					}
				});
	}
}
