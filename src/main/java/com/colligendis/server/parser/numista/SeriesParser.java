package com.colligendis.server.parser.numista;

import java.util.Map;
import java.util.Objects;

import org.jsoup.nodes.Element;
import org.springframework.stereotype.Component;

import com.colligendis.server.database.numista.model.Series;
import com.colligendis.server.database.numista.service.NTypeService;
import com.colligendis.server.database.numista.service.SeriesService;
import com.colligendis.server.database.result.CreateNodeExecutionStatus;
import com.colligendis.server.database.result.CreateRelationshipExecutionStatus;
import com.colligendis.server.database.result.ExecutionResult;
import com.colligendis.server.database.result.ExecutionStatuses;
import com.colligendis.server.database.result.FindExecutionStatus;
import com.colligendis.server.parser.PauseLock;
import com.colligendis.server.parser.numista.exception.ParserException;

import reactor.core.publisher.Mono;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
@RequiredArgsConstructor
public class SeriesParser extends Parser {

	private static final PauseLock PAUSE_LOCK = new PauseLock("SeriesParser");

	private final SeriesService seriesService;
	private final NTypeService nTypeService;

	// {"id":7326,"text":"Westward Journey"}

	@Override
	protected Mono<NumistaPage> parse(NumistaPage numistaPage) {
		return Mono.defer(() -> {

			Element seriesElement = numistaPage.page.selectFirst("#series");
			if (seriesElement == null) {
				numistaPage.getPipelineStepLogger().info("nid: {} - Can't find Series on the page", numistaPage.nid);
				return Mono.just(numistaPage);
			}
			Element seriesOption = seriesElement.selectFirst("option");
			if (seriesOption == null) {
				numistaPage.getPipelineStepLogger().info("nid: {} - Can't find Series option on the page",
						numistaPage.nid);
				return Mono.just(numistaPage);
			}

			Map<String, String> seriesMap = NumistaParseUtils.getAttributeWithTextSingleOption(numistaPage, "#series",
					"value");

			if (seriesMap == null || seriesMap.get("value") == null || seriesMap.get("value").isEmpty()
					|| seriesMap.get("text") == null
					|| seriesMap.get("text").isEmpty()) {
				log.info("nid: {} - Series option is empty on the page", numistaPage.nid);
				return Mono.just(numistaPage);
			}

			String seriesName = Objects.requireNonNull(seriesMap).get("text");
			String seriesNid = Objects.requireNonNull(seriesMap).get("value");

			return PAUSE_LOCK.awaitIfPaused()
					.then(seriesService.findByNid(seriesNid, numistaPage.getPipelineStepLogger()))
					.flatMap(executionResult -> {
						switch (executionResult.getStatus()) {
							case FOUND:
								return linkSeriesToNType(executionResult, seriesNid, numistaPage);
							case NOT_FOUND:
								return handleSeriesNotFound(seriesNid, seriesName, numistaPage);
							default:
								executionResult.logError(numistaPage.getPipelineStepLogger());
								return Mono.error(new ParserException("Failed to find Series: " + seriesNid));
						}
					});
		});

	}

	private Mono<NumistaPage> handleSeriesNotFound(String seriesNid, String seriesName,
			NumistaPage numistaPage) {
		boolean acquiredLock = PAUSE_LOCK.pause();
		if (!acquiredLock) {
			return PAUSE_LOCK.awaitIfPaused()
					.then(seriesService.findByNid(seriesNid, numistaPage.getPipelineStepLogger()))
					.flatMap(executionResult -> {
						if (executionResult.getStatus().equals(FindExecutionStatus.FOUND)) {
							return linkSeriesToNType(executionResult, seriesNid, numistaPage);
						}
						executionResult.logError(numistaPage.getPipelineStepLogger());
						return Mono.error(new ParserException("Failed to find Series: " + seriesNid));
					});
		}

		return seriesService
				.findByNidWithCreate(seriesNid, seriesName, numistaPage.getNumistaParserUserMono(),
						numistaPage.getPipelineStepLogger())
				.doFinally(signal -> PAUSE_LOCK.resume())
				.flatMap(executionResult -> linkSeriesToNType(executionResult, seriesNid, numistaPage));
	}

	private Mono<NumistaPage> linkSeriesToNType(ExecutionResult<Series, ? extends ExecutionStatuses> executionResult,
			String seriesNid,
			NumistaPage numistaPage) {
		if (executionResult.getStatus().equals(FindExecutionStatus.FOUND)
				|| executionResult.getStatus().equals(CreateNodeExecutionStatus.WAS_CREATED)) {
			return nTypeService.setSeries(numistaPage.nType, executionResult.getNode(),
					numistaPage.getNumistaParserUserMono(), numistaPage.getPipelineStepLogger())
					.flatMap(rel -> {
						if (rel.getStatus().equals(CreateRelationshipExecutionStatus.IS_ALREADY_EXISTS)
								|| rel.getStatus().equals(CreateRelationshipExecutionStatus.WAS_CREATED)) {
							return Mono.just(numistaPage);
						}
						rel.logError(numistaPage.getPipelineStepLogger());
						return Mono.error(new ParserException(
								"Failed to set relationship between NType and Series " + seriesNid));
					});
		}
		executionResult.logError(numistaPage.getPipelineStepLogger());
		return Mono.error(new ParserException("Failed to find Series: " + seriesNid));
	}

}
