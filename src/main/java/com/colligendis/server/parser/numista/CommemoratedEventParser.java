package com.colligendis.server.parser.numista;

import com.colligendis.server.database.numista.service.CommemoratedEventService;
import com.colligendis.server.database.numista.service.NTypeService;
import com.colligendis.server.database.result.CreateNodeExecutionStatus;
import com.colligendis.server.database.result.FindExecutionStatus;

import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Mono;

import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;

@Slf4j
@Component
@RequiredArgsConstructor
public class CommemoratedEventParser extends Parser {

	/**
	 * Specify the subject of the commemorative issue. Do not include dates.
	 * Format
	 * the subject according to the following examples:
	 * 100th anniversary of the birth of Albert Einstein (only “Albert Einstein”
	 * in
	 * the title)
	 * 100th anniversary of the Gotthard Railway (only “Gotthard Railway” in the
	 * title)
	 * 150th anniversary of the death of Johann Heinrich Pestalozzi (only “Johann
	 * Heinrich Pestalozzi” in the title)
	 * 500th anniversary of the Treaty of Stans (only “Treaty of Stans” in the
	 * title)
	 * 600th anniversary of the Battle of Grunwald (only “Battle of Grunwald” in
	 * the
	 * title)
	 * Wedding of Prince Philip and Princess Mathilde (only “Wedding of Philip and
	 * Mathilde” in the title)
	 * Franklin Delano Roosevelt (just specify the subject if no particular event
	 * is
	 * commemorated)
	 */

	private final CommemoratedEventService commemoratedEventService;

	private final NTypeService nTypeService;

	@Override
	protected Mono<NumistaPage> parse(NumistaPage numistaPage) {
		return parseCommemoratedEvent(numistaPage);
	}

	private Mono<NumistaPage> parseCommemoratedEvent(NumistaPage numistaPage) {

		String evenement = NumistaParseUtils.getAttribute(numistaPage.page.selectFirst("#evenement"),
				"value");
		if (evenement == null || evenement.isEmpty()) {
			log.info("nid: {} - Can't find Commemorated Event on the page",
					numistaPage.nid);
			return Mono.just(numistaPage);
		}

		return commemoratedEventService.findByNameWithCreate(evenement,
				numistaPage.getNumistaParserUserMono(), numistaPage.getPipelineStepLogger())
				.flatMap(executionResult -> {

					if (executionResult.getStatus().equals(FindExecutionStatus.FOUND)
							|| executionResult.getStatus().equals(CreateNodeExecutionStatus.WAS_CREATED)) {
						return nTypeService
								.setCommemoratedEvent(numistaPage.nType, executionResult.getNode(),
										numistaPage.getNumistaParserUserMono(), numistaPage.getPipelineStepLogger())
								.flatMap(setCommemoratedEventExecutionResult -> {
									switch (setCommemoratedEventExecutionResult.getStatus()) {
										case WAS_CREATED:
											numistaPage.getPipelineStepLogger()
													.info("Commemorated Event linked to NType successfully");
											return Mono.just(numistaPage);
										case IS_ALREADY_EXISTS:
											numistaPage.getPipelineStepLogger()
													.info("Commemorated Event already linked to NType");
											return Mono.just(numistaPage);
										default:
											numistaPage.getPipelineStepLogger()
													.error("Failed to link Commemorated Event to NType: {}",
															setCommemoratedEventExecutionResult.getStatus());
											setCommemoratedEventExecutionResult
													.logError(numistaPage.getPipelineStepLogger());
											return Mono.just(numistaPage);
									}
								});
					} else {
						numistaPage.getPipelineStepLogger()
								.error("Failed to find or save commemorated event: {}", executionResult.getStatus());
						executionResult.logError(numistaPage.getPipelineStepLogger());
						return Mono.just(numistaPage);
					}
				});
	}

}
