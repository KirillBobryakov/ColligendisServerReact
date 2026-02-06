package com.colligendis.server.parser.numista;

import java.util.function.Function;

import org.jsoup.nodes.Element;

import com.colligendis.server.database.N4JUtil;
import com.colligendis.server.database.exception.NotFoundError;
import com.colligendis.server.database.numista.service.CollectibleTypeService;
import com.colligendis.server.database.numista.service.NTypeService;
import com.colligendis.server.parser.ParsingStatus;

import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Mono;

@Slf4j
public class CollectibleTypeParser {

    public static final CollectibleTypeParser instance = new CollectibleTypeParser();

    // All collectable types are loaded in the database by CollectibleTypeTreeParser

    public Function<NumistaPage, Mono<NumistaPage>> parse = numistaPage -> {
        return Mono.defer(() -> {

            CollectibleTypeService collectibleTypeService = N4JUtil
                    .getInstance().numistaServices.collectibleTypeService;

            Element collectibleSubtype = numistaPage.page.selectFirst("#collectible_type");

            if (collectibleSubtype == null) {
                numistaPage.parsingStatus = ParsingStatus.FAILED;
                return Mono.empty();
            }

            Element typeElement = collectibleSubtype.select("option").stream()
                    .filter(e -> e.hasAttr("selected")
                            && !"Unknown".equals(e.text()))
                    .findFirst().orElse(null);

            if (typeElement == null) {
                numistaPage.parsingStatus = ParsingStatus.FAILED;
                log.error("nid: {} - CollectibleType not found",
                        "\u001B[34m" + numistaPage.nid + "\u001B[0m");
                return Mono.just(numistaPage);
            }

            String collectibleTypeCode = NumistaParseUtils.getAttribute(typeElement, "value");
            NTypeService nTypeService = N4JUtil.getInstance().numistaServices.nTypeService;

            return collectibleTypeService.findByCode(collectibleTypeCode)
                    .flatMap(foundCollectibleType -> foundCollectibleType.fold(
                            error -> {
                                if (error instanceof NotFoundError) {
                                    log.error("nid: {} - CollectibleType not found: {}",
                                            "\u001B[34m" + numistaPage.nid + "\u001B[0m",
                                            "\u001B[34m" + collectibleTypeCode + "\u001B[0m");
                                } else {
                                    log.error("nid: {} - Error finding CollectibleType: {}",
                                            "\u001B[34m" + numistaPage.nid + "\u001B[0m",
                                            "\u001B[34m" + collectibleTypeCode + "\u001B[0m",
                                            "\u001B[31m" + error.message() + "\u001B[0m");
                                }
                                numistaPage.parsingStatus = ParsingStatus.FAILED;
                                return Mono.just(numistaPage);
                            }, collectibleType -> {
                                return nTypeService.setCollectibleType(numistaPage.nType, collectibleType,
                                        numistaPage.colligendisUser)
                                        .flatMap(result -> result.fold(
                                                error -> {
                                                    log.error("nid: {} - Error setting CollectibleType: {} - Error: {}",
                                                            "\u001B[34m" + numistaPage.nid + "\u001B[0m",
                                                            "\u001B[34m" + collectibleTypeCode + "\u001B[0m",
                                                            "\u001B[31m" + error.message() + "\u001B[0m");
                                                    numistaPage.parsingStatus = ParsingStatus.FAILED;
                                                    return Mono.just(numistaPage);
                                                }, connected -> {
                                                    numistaPage.collectibleType = collectibleType;
                                                    numistaPage.parsingStatus = ParsingStatus.CHANGED;
                                                    return Mono.just(numistaPage);
                                                }));
                            }));

        });
    };

}
