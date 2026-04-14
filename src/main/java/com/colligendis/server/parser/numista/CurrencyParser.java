package com.colligendis.server.parser.numista;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.springframework.stereotype.Component;

import com.colligendis.server.database.AbstractNode;
import com.colligendis.server.database.common.model.Year;
import com.colligendis.server.database.numista.model.Currency;
import com.colligendis.server.database.numista.service.CurrencyService;
import com.colligendis.server.database.numista.service.NTypeService;
import com.colligendis.server.database.result.CreateNodeExecutionStatus;
import com.colligendis.server.database.result.CreateRelationshipExecutionStatus;
import com.colligendis.server.database.result.ExecutionResult;
import com.colligendis.server.database.result.ExecutionStatuses;
import com.colligendis.server.database.result.FindExecutionStatus;
import com.colligendis.server.parser.PauseLock;
import com.colligendis.server.parser.numista.exception.ParserException;
import com.colligendis.server.parser.numista.year_parser.CirculationPeriod;
import com.colligendis.server.parser.numista.year_parser.CirculationPeriods;
import com.colligendis.server.parser.numista.year_parser.YearPeriodParserService;

import lombok.RequiredArgsConstructor;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

@Component
@RequiredArgsConstructor
public class CurrencyParser extends Parser {

	private final CurrencyService currencyService;
	private final NTypeService nTypeService;
	private final YearPeriodParserService yearPeriodParserService;

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
				return Mono.error(new ParserException(
						"Can't find Currency (devise) while parsing page for nid: " + numistaPage.nid));
			}

			String currencyNid = devise.get("value");

			return PAUSE_LOCK.awaitIfPaused()
					.then(currencyService.findByNid(currencyNid, numistaPage.getPipelineStepLogger()))
					.flatMap(executionResult -> {
						if (!executionResult.getStatus().equals(FindExecutionStatus.FOUND)) {
							return handleCurrencyNotFound(currencyNid, numistaPage);
						} else {
							return Mono.just(executionResult);
						}
					})
					.flatMap(executionResult -> {
						if (executionResult.getStatus().equals(FindExecutionStatus.FOUND)
								|| executionResult.getStatus().equals(CreateNodeExecutionStatus.WAS_CREATED)) {
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

	private Mono<ExecutionResult<Currency, FindExecutionStatus>> handleCurrencyNotFound(String currencyNid,
			NumistaPage numistaPage) {

		boolean acquiredLock = PAUSE_LOCK.pause();
		if (!acquiredLock) {
			// Another thread is already loading currencies - wait and retry
			return PAUSE_LOCK.awaitIfPaused()
					.then(currencyService.findByNid(currencyNid, numistaPage.getPipelineStepLogger()));
		}

		return loadAndParseCurrenciesByIssuerCodeFromPHPRequestMono(numistaPage)
				.subscribeOn(Schedulers.boundedElastic())
				.doFinally(signal -> PAUSE_LOCK.resume())
				.then(currencyService.findByNid(currencyNid, numistaPage.getPipelineStepLogger()));

	}

	private Mono<Boolean> loadAndParseCurrenciesByIssuerCodeFromPHPRequestMono(NumistaPage numistaPage) {

		String url = CURRENCIES_URL_PREFIX + "country=" + numistaPage.issuer.getNumistaCode() + "&ct="
				+ numistaPage.collectibleType.getCode();

		return Mono.fromCallable(() -> NumistaParseUtils.loadPageByURL(url))
				.subscribeOn(Schedulers.boundedElastic())
				.flatMapMany(doc -> extractOptionElements(doc, numistaPage))
				.flatMap(option -> processCurrencyOption(option, numistaPage))
				.then()
				.thenReturn(true);
	}

	private Flux<Element> extractOptionElements(Document doc, NumistaPage numistaPage) {
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
			NumistaPage numistaPage) {
		String nid = option.attr("value");
		String fullName = extractFullName(option.text());
		String cleanName = stripName(fullName);

		return currencyService
				.findByNidWithCreate(nid, fullName, cleanName, numistaPage.getNumistaParserUserMono(),
						numistaPage.getPipelineStepLogger())
				.flatMap(executionResult -> {
					if (executionResult.getStatus().equals(FindExecutionStatus.FOUND)
							|| executionResult.getStatus().equals(CreateNodeExecutionStatus.WAS_CREATED)) {
						return yearPeriodParserService.parsePeriods(fullName)
								.flatMap(periods -> setYearRelationships(executionResult, periods, numistaPage));
					} else {
						executionResult.logError(numistaPage.getPipelineStepLogger());
						return Mono.error(new ParserException("Failed to find or save currency " + nid));
					}
				})
				.flatMap(executionResult -> {
					if (executionResult.getStatus().equals(FindExecutionStatus.FOUND)
							|| executionResult.getStatus().equals(CreateNodeExecutionStatus.WAS_CREATED)) {
						return currencyService.setIssuer(executionResult.getNode(), numistaPage.issuer,
								numistaPage.getNumistaParserUserMono(), numistaPage.getPipelineStepLogger())
								.thenReturn(executionResult);
					} else {
						executionResult.logError(numistaPage.getPipelineStepLogger());
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
			NumistaPage numistaPage) {
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
						numistaPage.getNumistaParserUserMono(), numistaPage.getPipelineStepLogger());

		Mono<ExecutionResult<AbstractNode, CreateRelationshipExecutionStatus>> setTill = tillYears.isEmpty()
				? Mono.empty()
				: currencyService.setCirculatedTillYears(currency.getNode(), tillYears,
						numistaPage.getNumistaParserUserMono(), numistaPage.getPipelineStepLogger());

		return setFrom
				.flatMap(setFromExecutionResult -> {
					switch (setFromExecutionResult.getStatus()) {
						case WAS_CREATED:
							numistaPage.getPipelineStepLogger()
									.info("Circulated From Years set for currency " + currency.getNode().getNid());
							return Mono.empty();
						default:
							numistaPage.getPipelineStepLogger().error(
									"Failed to set Circulated From Years for currency " + currency.getNode().getNid());
							return Mono.error(new ParserException(
									"Failed to set Circulated From Years for currency " + currency.getNode().getNid()));
					}
				})
				.then(setTill).flatMap(setTillExecutionResult -> {
					switch (setTillExecutionResult.getStatus()) {
						case WAS_CREATED:
							numistaPage.getPipelineStepLogger()
									.info("Circulated Till Years set for currency " + currency.getNode().getNid());
							return Mono.empty();
						default:
							numistaPage.getPipelineStepLogger().error(
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

}
