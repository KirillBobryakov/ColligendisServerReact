package com.colligendis.server.parser.numista;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;

import org.jsoup.nodes.Element;

import com.colligendis.server.database.N4JUtil;
import com.colligendis.server.database.numista.service.NTypeService;
import com.colligendis.server.database.numista.service.SeriesService;

import reactor.core.publisher.Mono;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class SeriesParser {

    public static final SeriesParser instance = new SeriesParser();
    private SeriesService seriesService;

    private SeriesService getSeriesService() {
        if (seriesService == null) {
            seriesService = N4JUtil.getInstance().numistaServices.seriesService;
        }
        return seriesService;
    }

    private NTypeService nTypeService;

    private NTypeService getNTypeService() {
        if (nTypeService == null) {
            nTypeService = N4JUtil.getInstance().numistaServices.nTypeService;
        }
        return nTypeService;
    }

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

        return getSeriesService().findByNidWithSave(seriesNid, seriesName, numistaPage.colligendisUser)
                .flatMap(seriesEither -> seriesEither.fold(
                        seriesErr -> {
                            return Mono.<NumistaPage>error(
                                    new RuntimeException("Failed to find or save series: " + seriesErr.message()));
                        },
                        series -> {
                            return getNTypeService().setSeries(numistaPage.nType, series, numistaPage.colligendisUser)
                                    .flatMap(result -> result.fold(
                                            resultErr -> {
                                                return Mono.<NumistaPage>error(new RuntimeException(
                                                        "Failed to set series: " + resultErr.message()));
                                            },
                                            success -> {
                                                return Mono.just(numistaPage);
                                            }));
                        }));
    }
}
