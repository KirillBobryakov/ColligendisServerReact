package com.colligendis.server.parser.numista;

import java.util.Map;
import java.util.Objects;
import java.util.function.Function;

import org.jsoup.nodes.Element;
import org.springframework.stereotype.Component;

import com.colligendis.server.database.ExecutionStatus;
import com.colligendis.server.database.numista.model.Series;
import com.colligendis.server.database.numista.service.NTypeService;
import com.colligendis.server.database.numista.service.SeriesService;
import com.colligendis.server.parser.numista.exception.ParserException;

import reactor.core.publisher.Mono;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
@RequiredArgsConstructor
public class SeriesParser {

	private final SeriesService seriesService;
	private final NTypeService nTypeService;

	public Function<NumistaPage, Mono<NumistaPage>> parse = page -> Mono.defer(() -> parseSeries(page));

	private Mono<NumistaPage> parseSeries(NumistaPage numistaPage) {

		Element seriesElement = numistaPage.page.selectFirst("#series");
		if (seriesElement == null) {
			log.info("nid: {} - Can't find Series on the page", numistaPage.nid);
			return Mono.just(numistaPage);
		}
		Element seriesOption = seriesElement.selectFirst("option");
		if (seriesOption == null) {
			log.info("nid: {} - Can't find Series option on the page", numistaPage.nid);
			return Mono.just(numistaPage);
		}
		Map<String, String> seriesMap = NumistaParseUtils.getAttributeWithTextSingleOption(numistaPage.page, "#series",
				"value");

		if (seriesMap == null || seriesMap.get("value") == null || seriesMap.get("value").isEmpty()
				|| seriesMap.get("text") == null
				|| seriesMap.get("text").isEmpty()) {
			log.info("nid: {} - Series option is empty on the page", numistaPage.nid);
			return Mono.just(numistaPage);
		}

		String seriesName = Objects.requireNonNull(seriesMap).get("text");
		String seriesNid = Objects.requireNonNull(seriesMap).get("value");

		return seriesService
				.findByNidWithSave(seriesNid, seriesName, numistaPage.getNumistaParserUserMono(),
						numistaPage.getPipelineStepLogger())
				.flatMap(executionResult -> {
					ExecutionStatus status = executionResult.getStatus();
					if (ExecutionStatus.NODE_IS_FOUND.equals(status)
							|| ExecutionStatus.NODE_WAS_CREATED.equals(status)) {
						Series series = executionResult.getNode();
						if (series != null) {
							return Mono.just(series);
						}
					}
					numistaPage.getPipelineStepLogger().error("Failed to find Series: {}", status);
					executionResult.logError(numistaPage.getPipelineStepLogger());
					return Mono.<Series>error(new ParserException("Failed to find Series: " + seriesNid));
				})
				.flatMap(series -> nTypeService.setSeries(numistaPage.nType, series,
						numistaPage.getNumistaParserUserMono(), numistaPage.getPipelineStepLogger()))
				.thenReturn(numistaPage);

	}
}
