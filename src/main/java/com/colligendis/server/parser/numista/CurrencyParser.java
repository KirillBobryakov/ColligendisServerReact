package com.colligendis.server.parser.numista;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.springframework.stereotype.Component;

import com.colligendis.server.database.AbstractNode;
import com.colligendis.server.database.ColligendisUser;
import com.colligendis.server.database.ColligendisUserService;
import com.colligendis.server.database.common.model.Year;
import com.colligendis.server.database.numista.model.Currency;
import com.colligendis.server.database.numista.model.Denomination;
import com.colligendis.server.database.numista.model.Issuer;
import com.colligendis.server.database.numista.service.CurrencyService;
import com.colligendis.server.database.numista.service.DenominationService;
import com.colligendis.server.database.numista.service.IssuerService;
import com.colligendis.server.database.numista.service.NTypeService;
import com.colligendis.server.database.result.CreateNodeExecutionStatus;
import com.colligendis.server.database.result.CreateRelationshipExecutionStatus;
import com.colligendis.server.database.result.ExecutionResult;
import com.colligendis.server.database.result.ExecutionStatuses;
import com.colligendis.server.database.result.FindExecutionStatus;
import com.colligendis.server.logger.BaseLogger;
import com.colligendis.server.parser.PauseLock;
import com.colligendis.server.parser.numista.exception.ParserException;
import com.colligendis.server.parser.numista.year_parser.CirculationPeriod;
import com.colligendis.server.parser.numista.year_parser.CirculationPeriods;
import com.colligendis.server.parser.numista.year_parser.YearPeriodParserService;
import com.colligendis.server.util.web.WebPageClient;
import com.colligendis.server.util.web.WebPageLoadException;

import lombok.RequiredArgsConstructor;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

@Component
@RequiredArgsConstructor
public class CurrencyParser extends Parser {

	private final ColligendisUserService colligendisUserService;
	private final CurrencyService currencyService;
	private final DenominationService denominationService;
	private final IssuerService issuerService;
	private final NTypeService nTypeService;
	private final YearPeriodParserService yearPeriodParserService;
	private final WebPageClient webPageClient;

	private static final String CURRENCIES_URL_PREFIX = "https://en.numista.com/catalogue/get_currencies.php?";

	private static final PauseLock PAUSE_LOCK = new PauseLock("CurrencyParser");

	@Override
	protected Mono<NumistaPage> parse(NumistaPage numistaPage) {
		return Mono.defer(() -> {

			Map<String, String> devise = NumistaParseUtils.getAttributeWithTextSingleOption(
					numistaPage, "#devise", "value");

			if (devise == null) {
				numistaPage.getPipelineStepLogger()
						.warning("Currency: not found for nid: {} - Can't find Currency (devise) while parsing page",
								numistaPage.nid);
				return Mono.just(numistaPage);
			}

			String currencyNid = devise.get("value");

			return PAUSE_LOCK.awaitIdle()
					.then(currencyService.findByNid(currencyNid, numistaPage.getPipelineStepLogger()))
					.flatMap(executionResult -> {
						if (!executionResult.getStatus().equals(FindExecutionStatus.FOUND)) {
							return handleCurrencyNotFound(currencyNid, numistaPage.issuer,
									numistaPage.getNumistaParserUserMono(), numistaPage.getPipelineStepLogger());
						} else {
							return Mono.just(executionResult);
						}
					})
					.flatMap(executionResult -> {
						if (executionResult.getStatus().equals(FindExecutionStatus.FOUND)) {
							numistaPage.currency = executionResult.getNode();
							return nTypeService.setCurrency(numistaPage.nType, executionResult.getNode(),
									numistaPage.getNumistaParserUserMono(), numistaPage.getPipelineStepLogger());
						} else {
							executionResult.logError(numistaPage.getPipelineStepLogger());
							return Mono.error(new ParserException(
									"Failed to set relationship between NType and Currency for nid: " + numistaPage.nid
											+ " and currency nid: " + currencyNid));
						}
					})
					.flatMap(executionResult -> {
						if (executionResult.getStatus().equals(CreateRelationshipExecutionStatus.IS_ALREADY_EXISTS)
								|| executionResult.getStatus().equals(CreateRelationshipExecutionStatus.WAS_CREATED)) {
							return Mono.just(numistaPage);
						} else {
							executionResult.logError(numistaPage.getPipelineStepLogger());
							numistaPage.currency = null;
							return Mono.error(new ParserException(
									"Failed to set relationship between NType and Currency for nid: " + numistaPage.nid
											+ " and currency nid: " + currencyNid));
						}
					});
		});
	}

	public Mono<Boolean> pearseCurrenciesByCountryCode(String countryCode) {
		String url = CURRENCIES_URL_PREFIX + "country=" + countryCode;
		BaseLogger baseLogger = new BaseLogger();
		Mono<ColligendisUser> colligendisUserMono = colligendisUserService.getNumistaParserUserMono();

		return issuerService.findByNumistaCode(countryCode, baseLogger)
				.flatMap(issuerResult -> {
					if (!issuerResult.getStatus().equals(FindExecutionStatus.FOUND)) {
						issuerResult.logError(baseLogger);
						return Mono.error(new ParserException("Issuer not found for country code: " + countryCode));
					}
					Issuer issuer = issuerResult.getNode();
					return loadHtmlPage(url, colligendisUserMono, baseLogger)
							.switchIfEmpty(Mono.error(new ParserException("Can't load currencies from URL: " + url)))
							.flatMapMany(this::extractOptionElements)
							.flatMap(option -> processCurrencyOption(option, issuer, colligendisUserMono, baseLogger))
							.then(Mono.fromRunnable(baseLogger::flushToTerminal))
							.thenReturn(true);
				});
	}

	private Mono<ExecutionResult<Currency, FindExecutionStatus>> handleCurrencyNotFound(String currencyNid,
			Issuer issuer, Mono<ColligendisUser> colligendisUserMono, BaseLogger baseLogger) {

		return PAUSE_LOCK.runExclusiveOrElse(
				() -> loadAndParseCurrenciesByIssuerCodeFromPHPRequestMono(issuer, colligendisUserMono, baseLogger)
						.subscribeOn(Schedulers.boundedElastic())
						.then(currencyService.findByNid(currencyNid, baseLogger)),
				() -> currencyService.findByNid(currencyNid, baseLogger));
	}

	/**
	 * Loads and parses all currencies of the given issuer from the Numista PHP
	 * endpoint. Resolves the issuer by its Numista code and delegates to
	 * {@link #loadAndParseCurrenciesByIssuerCodeFromPHPRequestMono(Issuer, Mono, BaseLogger)}.
	 */
	public Mono<Boolean> loadAndParseCurrenciesByIssuerCode(String issuerCode) {
		final String normalizedCode = issuerCode == null ? "" : issuerCode.trim();
		if (normalizedCode.isEmpty()) {
			return Mono.error(new ParserException("issuerCode is required"));
		}

		BaseLogger baseLogger = new BaseLogger();
		Mono<ColligendisUser> colligendisUserMono = colligendisUserService.getNumistaParserUserMono();

		return issuerService.findByNumistaCode(normalizedCode, baseLogger)
				.flatMap(issuerResult -> {
					if (!issuerResult.getStatus().equals(FindExecutionStatus.FOUND)) {
						issuerResult.logError(baseLogger);
						return Mono.error(new ParserException("Issuer not found for code: " + normalizedCode));
					}
					Issuer issuer = issuerResult.getNode();
					return loadAndParseCurrenciesByIssuerCodeFromPHPRequestMono(issuer, colligendisUserMono,
							baseLogger);
				})
				.subscribeOn(Schedulers.boundedElastic())
				.doFinally(signal -> baseLogger.flushToTerminal());
	}

	private Mono<Boolean> loadAndParseCurrenciesByIssuerCodeFromPHPRequestMono(Issuer issuer,
			Mono<ColligendisUser> colligendisUserMono, BaseLogger baseLogger) {

		String url = CURRENCIES_URL_PREFIX + "country=" + issuer.getNumistaCode();
		// String url = CURRENCIES_URL_PREFIX + "country=" +
		// numistaPage.issuer.getNumistaCode() + "&ct="
		// + numistaPage.collectibleType.getCode();

		return loadHtmlPage(url, colligendisUserMono, baseLogger)
				.switchIfEmpty(Mono.error(new ParserException("Can't load currencies from URL: " + url)))
				.flatMapMany(this::extractOptionElements)
				.flatMap(option -> processCurrencyOption(option, issuer, colligendisUserMono, baseLogger))
				.then()
				.thenReturn(true);
	}

	private Mono<Document> loadHtmlPage(String url, Mono<ColligendisUser> userMono, BaseLogger baseLogger) {
		return userMono
				.map(CurrencyParser::resolveCookie)
				.defaultIfEmpty("")
				.flatMap(cookie -> webPageClient.loadPageDocument(url, cookie))
				.onErrorResume(WebPageLoadException.class, e -> {
					baseLogger.error("CurrencyParser: can't load {}: {}", url, e.getMessage());
					return Mono.empty();
				});
	}

	private static String resolveCookie(ColligendisUser user) {
		if (user != null && user.getNumistaCookie() != null && !user.getNumistaCookie().isBlank()) {
			return user.getNumistaCookie().strip();
		}
		return "";
	}

	private Flux<Element> extractOptionElements(Document doc) {
		Elements optgroups = doc.select("optgroup");
		if (!optgroups.isEmpty()) {
			return Flux.error(new IllegalStateException("OPTGROUP found in currencies page"));
		}
		Elements options = doc.select("option");
		if (options.isEmpty()) {
			return Flux.error(new IllegalStateException("No <option> tags found in currencies page"));
		}

		return Flux.fromIterable(options);
	}

	private Mono<ExecutionResult<Currency, ? extends ExecutionStatuses>> processCurrencyOption(Element option,
			Issuer issuer,
			Mono<ColligendisUser> colligendisUserMono,
			BaseLogger baseLogger) {
		String nid = option.attr("value");
		String fullName = extractFullName(option.text());
		String cleanName = stripName(fullName);

		return currencyService
				.findByNidWithCreate(nid, fullName, cleanName, colligendisUserMono,
						baseLogger)
				.flatMap(executionResult -> {

					if (executionResult.getStatus().equals(FindExecutionStatus.FOUND)) {
						return yearPeriodParserService.parsePeriods(fullName)
								.flatMap(periods -> setYearRelationships(executionResult, periods, colligendisUserMono,
										baseLogger));
					} else if (executionResult.getStatus().equals(CreateNodeExecutionStatus.WAS_CREATED)) {
						// numistaPage.currency = executionResult.getNode();
						return parseDenominationsByCurrencyCodeFromPHPRequest(executionResult.getNode(),
								colligendisUserMono, baseLogger)
								.then(yearPeriodParserService.parsePeriods(fullName)
										.flatMap(
												periods -> setYearRelationships(executionResult, periods,
														colligendisUserMono, baseLogger)));
					} else {
						executionResult.logError(baseLogger);
						return Mono.error(new ParserException("Failed to find or save currency " + nid));
					}
				})
				.flatMap(executionResult -> {
					if (executionResult.getStatus().equals(FindExecutionStatus.FOUND)
							|| executionResult.getStatus().equals(CreateNodeExecutionStatus.WAS_CREATED)) {
						return currencyService.setIssuer(executionResult.getNode(), issuer,
								colligendisUserMono, baseLogger)
								.thenReturn(executionResult);
					} else {
						executionResult.logError(baseLogger);
						return Mono.error(new ParserException("Failed to set Issuer for currency " + nid));
					}
				});
	}

	private String extractFullName(String text) {
		// format: "123 – Mark (notgeld, 1914-1924)"
		int dashIdx = text.indexOf('–');
		return dashIdx >= 0 ? text.substring(dashIdx + 1).trim() : text.trim();
	}

	private String stripName(String fullName) {
		int parenIdx = fullName.indexOf('(');
		return parenIdx > 0 ? fullName.substring(0, parenIdx).trim() : fullName.trim();
	}

	private Mono<ExecutionResult<Currency, ? extends ExecutionStatuses>> setYearRelationships(
			ExecutionResult<Currency, ? extends ExecutionStatuses> currency,
			CirculationPeriods periods,
			Mono<ColligendisUser> colligendisUserMono,
			BaseLogger baseLogger) {
		List<Year> fromYears = periods.periods().stream()
				.map(CirculationPeriod::from)
				.filter(Optional::isPresent)
				.map(Optional::get)
				.toList();

		List<Year> tillYears = periods.periods().stream()
				.map(CirculationPeriod::till)
				.filter(Optional::isPresent)
				.map(Optional::get)
				.toList();

		Mono<ExecutionResult<AbstractNode, CreateRelationshipExecutionStatus>> setFrom = fromYears.isEmpty()
				? Mono.empty()
				: currencyService.setCirculatedFromYears(currency.getNode(), fromYears,
						colligendisUserMono, baseLogger);

		Mono<ExecutionResult<AbstractNode, CreateRelationshipExecutionStatus>> setTill = tillYears.isEmpty()
				? Mono.empty()
				: currencyService.setCirculatedTillYears(currency.getNode(), tillYears,
						colligendisUserMono, baseLogger);

		return setFrom
				.flatMap(setFromExecutionResult -> {
					switch (setFromExecutionResult.getStatus()) {
						case WAS_CREATED, IS_ALREADY_EXISTS:
							baseLogger.info("Circulated From Years set for currency " + currency.getNode().getNid());
							return Mono.empty();
						default:
							baseLogger.error(
									"Failed to set Circulated From Years for currency " + currency.getNode().getNid());
							return Mono.error(new ParserException(
									"Failed to set Circulated From Years for currency " + currency.getNode().getNid()));
					}
				})
				.then(setTill).flatMap(setTillExecutionResult -> {
					switch (setTillExecutionResult.getStatus()) {
						case WAS_CREATED, IS_ALREADY_EXISTS:
							baseLogger.info("Circulated Till Years set for currency " + currency.getNode().getNid());
							return Mono.empty();
						default:
							baseLogger.error(
									"Failed to set Circulated Till Years for currency " + currency.getNode().getNid());
							return Mono.error(new ParserException(
									"Failed to set Circulated Till Years for currency " + currency.getNode().getNid()));
					}
				})
				.thenReturn(currency);
	}

	// private Mono<ExecutionResult<Currency>>
	// saveCurrencyWithPeriods(ExecutionResult<Currency> currency,
	// CirculationPeriods periods,
	// NumistaPage numistaPage) {

	// return currencyService
	// .update(currency.getNode(), numistaPage.getNumistaParserUserMono(),
	// numistaPage.getPipelineStepLogger())
	// .flatMap(updated -> {
	// Currency cur = updated != null ? updated : currency;
	// return setYearRelationships(cur, periods, numistaPage);
	// });
	// }

	public static final String DENOMINATIONS_BY_CURRENCY_PREFIX = "https://en.numista.com/catalogue/get_denominations.php?";

	private Mono<Boolean> parseDenominationsByCurrencyCodeFromPHPRequest(Currency currency,
			Mono<ColligendisUser> colligendisUserMono, BaseLogger baseLogger) {
		String url = DENOMINATIONS_BY_CURRENCY_PREFIX + "currency=" + currency.getNid();

		return loadHtmlPage(url, colligendisUserMono, baseLogger)
				.flatMap(denominationsPHPDocument -> {
					if (!denominationsPHPDocument.select("optgroup").isEmpty()) {
						baseLogger.error(
								"Find OPTGROUP while parsing Denominations by Currency Code from PHP request for nid: {}",
								currency.getNid());
						return Mono.just(false);
					}
					return Flux.fromIterable(denominationsPHPDocument.select("option"))
							.flatMap(option -> processDenominationOption(option, currency, colligendisUserMono,
									baseLogger))
							.collectList()
							.thenReturn(true);
				})
				.switchIfEmpty(Mono.defer(() -> {
					baseLogger.error("Can't load Denominations by Currency Code from PHP request for nid: {}",
							currency.getNid());
					return Mono.just(false);
				}));
	}

	private Mono<ExecutionResult<Denomination, ? extends ExecutionStatuses>> processDenominationOption(Element option,
			Currency currency, Mono<ColligendisUser> colligendisUserMono, BaseLogger baseLogger) {
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
					baseLogger.error("Can't parse Denomination numericValue from '{}'", denFullName);
					if (denNumericValueStr.matches("[a-zA-Z]+")) {
						denNumericValue = null;
					}
				}
			}

		}
		return denominationService
				.findByNidWithCreate(denNid, denName, denFullName, denNumericValue,
						colligendisUserMono, baseLogger)
				.flatMap(executionResult -> {
					if (executionResult.getStatus().equals(FindExecutionStatus.FOUND)
							|| executionResult.getStatus().equals(CreateNodeExecutionStatus.WAS_CREATED)) {
						return denominationService.setCurrency(executionResult.getNode(), currency,
								colligendisUserMono, baseLogger)
								.flatMap(rel -> {
									if (rel.getStatus().equals(CreateRelationshipExecutionStatus.IS_ALREADY_EXISTS)
											|| rel.getStatus().equals(CreateRelationshipExecutionStatus.WAS_CREATED)) {
										return Mono.just(executionResult);
									}
									rel.logError(baseLogger);
									return Mono.error(new ParserException(
											"Failed to set relationship between Denomination and Currency " + denNid));
								});
					} else {
						executionResult.logError(baseLogger);
						return Mono.error(new ParserException("Failed to find or save denomination " + denNid));
					}
				});
	}
}
