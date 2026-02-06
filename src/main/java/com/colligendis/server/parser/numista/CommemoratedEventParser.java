package com.colligendis.server.parser.numista;

import java.util.function.Function;

import com.colligendis.server.database.N4JUtil;
import com.colligendis.server.database.numista.service.CommemoratedEventService;
import com.colligendis.server.database.numista.service.NTypeService;

import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Mono;

@Slf4j
public class CommemoratedEventParser {

    /**
     * Specify the subject of the commemorative issue. Do not include dates. Format
     * the subject according to the following examples:
     * 100th anniversary of the birth of Albert Einstein (only “Albert Einstein” in
     * the title)
     * 100th anniversary of the Gotthard Railway (only “Gotthard Railway” in the
     * title)
     * 150th anniversary of the death of Johann Heinrich Pestalozzi (only “Johann
     * Heinrich Pestalozzi” in the title)
     * 500th anniversary of the Treaty of Stans (only “Treaty of Stans” in the
     * title)
     * 600th anniversary of the Battle of Grunwald (only “Battle of Grunwald” in the
     * title)
     * Wedding of Prince Philip and Princess Mathilde (only “Wedding of Philip and
     * Mathilde” in the title)
     * Franklin Delano Roosevelt (just specify the subject if no particular event is
     * commemorated)
     */

    public static final CommemoratedEventParser instance = new CommemoratedEventParser();
    private CommemoratedEventService commemoratedEventService;

    private CommemoratedEventService getCommemoratedEventService() {
        if (commemoratedEventService == null) {
            commemoratedEventService = N4JUtil.getInstance().numistaServices.commemoratedEventService;
        }
        return commemoratedEventService;
    }

    private NTypeService nTypeService;

    private NTypeService getNTypeService() {
        if (nTypeService == null) {
            nTypeService = N4JUtil.getInstance().numistaServices.nTypeService;
        }
        return nTypeService;
    }

    public Function<NumistaPage, Mono<NumistaPage>> parse = page -> Mono.defer(() -> parseCommemoratedEvent(page));

    private Mono<NumistaPage> parseCommemoratedEvent(NumistaPage numistaPage) {

        String evenement = NumistaParseUtils.getAttribute(numistaPage.page.selectFirst("#evenement"),
                "value");
        if (evenement == null || evenement.isEmpty()) {
            log.info("nid: {} - Can't find Commemorated Event on the page", numistaPage.nid);
            return Mono.just(numistaPage);
        }

        return getCommemoratedEventService().findByNameWithSave(evenement, numistaPage.colligendisUser)
                .flatMap(commemoratedEventEither -> commemoratedEventEither.fold(
                        commemoratedEventErr -> {
                            return Mono.<NumistaPage>error(new RuntimeException(
                                    "Failed to find or save commemorated event: " + commemoratedEventErr.message()));
                        },
                        commemoratedEvent -> {
                            return getNTypeService().setCommemoratedEvent(numistaPage.nType, commemoratedEvent,
                                    numistaPage.colligendisUser).flatMap(
                                            result -> result.fold(
                                                    error -> {
                                                        log.error("nid: {} - Failed to set commemorated event: {}",
                                                                numistaPage.nid, error.message());
                                                        return Mono.just(numistaPage);
                                                    },
                                                    success -> {
                                                        return Mono.just(numistaPage);
                                                    }));
                        }));
    }

}
