package com.colligendis.server.parser.numista;

import java.util.Map;

import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.springframework.stereotype.Component;

import com.colligendis.server.database.ColligendisUser;
import com.colligendis.server.database.numista.model.Denomination;
import com.colligendis.server.database.numista.service.DenominationService;
import com.colligendis.server.database.numista.service.NTypeService;
import com.colligendis.server.database.result.CreateNodeExecutionStatus;
import com.colligendis.server.database.result.CreateRelationshipExecutionStatus;
import com.colligendis.server.database.result.ExecutionResult;
import com.colligendis.server.database.result.ExecutionStatuses;
import com.colligendis.server.database.result.FindExecutionStatus;
import com.colligendis.server.parser.PauseLock;
import com.colligendis.server.parser.numista.exception.ParserException;
import com.colligendis.server.util.web.WebPageClient;
import com.colligendis.server.util.web.WebPageLoadException;

import lombok.RequiredArgsConstructor;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

@Component
@RequiredArgsConstructor
public class DenominationParser extends Parser {

	/**
	 * One lock for all concurrent pages so bulk PHP refresh cannot race duplicate
	 * {@code findByNidWithSave}.
	 */
	private static final PauseLock PAUSE_LOCK = new PauseLock("DenominationParsing");

	private final DenominationService denominationService;
	private final NTypeService nTypeService;
	private final WebPageClient webPageClient;

	@Override
	protected Mono<NumistaPage> parse(NumistaPage numistaPage) {
		return Mono.defer(() -> {

			Map<String, String> denominationAttr = NumistaParseUtils.getAttributeWithTextSingleOption(numistaPage,
					"#denomination",
					"value");

			if (denominationAttr == null) {
				numistaPage.getPipelineStepLogger().warning("Can't find Denomination while parsing page with nid: {}",
						numistaPage.nid);
				return Mono.just(numistaPage);
			}
			String denominationNid = denominationAttr.get("value");

			return PAUSE_LOCK.awaitIdle()
					.then(denominationService.findByNid(denominationNid, numistaPage.getPipelineStepLogger()))
					.flatMap(executionResult -> {
						if (!executionResult.getStatus().equals(FindExecutionStatus.FOUND)) {
							return handleDenominationNotFound(denominationNid, numistaPage);
						} else {
							return Mono.just(executionResult);
						}
					})
					.flatMap(executionResult -> {
						if (executionResult.getStatus().equals(FindExecutionStatus.FOUND)) {
							return nTypeService.setDenomination(numistaPage.nType, executionResult.getNode(),
									numistaPage.getNumistaParserUserMono(), numistaPage.getPipelineStepLogger())
									.flatMap(rel -> {
										if (rel.getStatus().equals(CreateRelationshipExecutionStatus.IS_ALREADY_EXISTS)
												|| rel.getStatus()
														.equals(CreateRelationshipExecutionStatus.WAS_CREATED)) {
											return Mono.just(numistaPage);
										}
										rel.logError(numistaPage.getPipelineStepLogger());
										return Mono.error(new ParserException(
												"Failed to set relationship between NType and Denomination "
														+ denominationNid));
									});
						} else {
							executionResult.logError(numistaPage.getPipelineStepLogger());
							return Mono.error(
									new ParserException("Failed to find or save denomination " + denominationNid));
						}
					});
		});
	}

	private Mono<ExecutionResult<Denomination, FindExecutionStatus>> handleDenominationNotFound(String denominationNid,
			NumistaPage numistaPage) {

		return PAUSE_LOCK.runExclusiveOrElse(
				() -> parseDenominationsByCurrencyCodeFromPHPRequest(numistaPage, denominationNid)
						.subscribeOn(Schedulers.boundedElastic())
						.then(denominationService.findByNid(denominationNid, numistaPage.getPipelineStepLogger())),
				() -> denominationService.findByNid(denominationNid, numistaPage.getPipelineStepLogger()));
	}

	public static final String DENOMINATIONS_BY_CURRENCY_PREFIX = "https://en.numista.com/catalogue/get_denominations.php?";

	private Mono<Boolean> parseDenominationsByCurrencyCodeFromPHPRequest(NumistaPage numistaPage, String prefill) {
		final String currencyNid = numistaPage.currency.getNid();
		String url = DENOMINATIONS_BY_CURRENCY_PREFIX + "currency=" + currencyNid + "&prefill=" + prefill;

		return loadHtmlPage(url, numistaPage)
				.flatMap(denominationsPHPDocument -> {
					if (!denominationsPHPDocument.select("optgroup").isEmpty()) {
						numistaPage.getPipelineStepLogger()
								.error("Find OPTGROUP while parsing Denominations by Currency Code from PHP request for nid: {}",
										numistaPage.nid);
						return Mono.just(false);
					}
					return Flux.fromIterable(denominationsPHPDocument.select("option"))
							.flatMap(option -> processDenominationOption(option, numistaPage))
							.collectList()
							.thenReturn(true);
				})
				.switchIfEmpty(Mono.defer(() -> {
					numistaPage.getPipelineStepLogger()
							.error("Can't load Denominations by Currency Code from PHP request for nid: {}",
									numistaPage.nid);
					return Mono.just(false);
				}));
	}

	private Mono<Document> loadHtmlPage(String url, NumistaPage numistaPage) {
		return numistaPage.getNumistaParserUserMono()
				.map(DenominationParser::resolveCookie)
				.defaultIfEmpty("")
				.flatMap(cookie -> webPageClient.loadPageDocument(url, cookie))
				.onErrorResume(WebPageLoadException.class, e -> {
					numistaPage.getPipelineStepLogger().error("DenominationParser: can't load {}: {}", url,
							e.getMessage());
					return Mono.empty();
				});
	}

	private static String resolveCookie(ColligendisUser user) {
		if (user != null && user.getNumistaCookie() != null && !user.getNumistaCookie().isBlank()) {
			return user.getNumistaCookie().strip();
		}
		return "";
	}

	private Mono<ExecutionResult<Denomination, ? extends ExecutionStatuses>> processDenominationOption(Element option,
			NumistaPage numistaPage) {
		String denNid = option.attributes().get("value");
		String denFullName = option.text();

		String denName = denFullName.contains("(") ? denFullName.substring(0, denFullName.lastIndexOf('(') - 1)
				: denFullName;
		Float denNumericValue = null;

		if (denFullName.contains("(")) {
			String denNumericValueStr = denFullName
					.substring(denFullName.lastIndexOf('(') + 1, denFullName.lastIndexOf(')')).replace(" ", "")
					.replace(" ", "");

			denNumericValueStr = denNumericValueStr.replace("¾", "0.75");
			denNumericValueStr = denNumericValueStr.replace("⅔", "0.666");
			denNumericValueStr = denNumericValueStr.replace("⅝", "0.625");
			denNumericValueStr = denNumericValueStr.replace("⅗", "0.6");
			denNumericValueStr = denNumericValueStr.replace("½", "0.5");
			denNumericValueStr = denNumericValueStr.replace("⅖", "0.4");
			denNumericValueStr = denNumericValueStr.replace("⅜", "0.375");
			denNumericValueStr = denNumericValueStr.replace("⅓", "0.333");
			denNumericValueStr = denNumericValueStr.replace("¼", "0.25");
			denNumericValueStr = denNumericValueStr.replace("⅕", "0.2");
			denNumericValueStr = denNumericValueStr.replace("⅙", "0.166");
			denNumericValueStr = denNumericValueStr.replace("⅐", "0.143");
			denNumericValueStr = denNumericValueStr.replace("⅛", "0.125");
			denNumericValueStr = denNumericValueStr.replace("⅒", "0.1");

			if (denNumericValueStr.contains("⁄")) {
				float top = Float.parseFloat(denNumericValueStr.substring(0, denNumericValueStr.indexOf("⁄")));
				float bottom = Float.parseFloat(denNumericValueStr.substring(denNumericValueStr.indexOf("⁄") + 1));
				denNumericValue = top / bottom;
			} else {
				try {
					denNumericValue = Float.valueOf(denNumericValueStr);
				} catch (NumberFormatException e) {
					numistaPage.getPipelineStepLogger()
							.error("Can't parse Denomination numericValue from '{}'", denFullName);
					if (denNumericValueStr.matches("[a-zA-Z]+")) {
						denNumericValue = null;
					}
				}
			}

		}
		return denominationService
				.findByNidWithCreate(denNid, denName, denFullName, denNumericValue,
						numistaPage.getNumistaParserUserMono(), numistaPage.getPipelineStepLogger())
				.flatMap(executionResult -> {
					if (executionResult.getStatus().equals(FindExecutionStatus.FOUND)
							|| executionResult.getStatus().equals(CreateNodeExecutionStatus.WAS_CREATED)) {
						return denominationService.setCurrency(executionResult.getNode(), numistaPage.currency,
								numistaPage.getNumistaParserUserMono(), numistaPage.getPipelineStepLogger())
								.flatMap(rel -> {
									if (rel.getStatus().equals(CreateRelationshipExecutionStatus.IS_ALREADY_EXISTS)
											|| rel.getStatus().equals(CreateRelationshipExecutionStatus.WAS_CREATED)) {
										return Mono.just(executionResult);
									}
									rel.logError(numistaPage.getPipelineStepLogger());
									return Mono.error(new ParserException(
											"Failed to set relationship between Denomination and Currency " + denNid));
								});
					} else {
						executionResult.logError(numistaPage.getPipelineStepLogger());
						return Mono.error(new ParserException("Failed to find or save denomination " + denNid));
					}
				});
	}

}
