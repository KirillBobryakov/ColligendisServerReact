package com.colligendis.server.parser.numista;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.springframework.stereotype.Component;

import com.colligendis.server.database.ExecutionResult;
import com.colligendis.server.database.ExecutionStatus;
import com.colligendis.server.database.common.model.Year;
import com.colligendis.server.database.numista.model.Currency;
import com.colligendis.server.database.numista.service.CurrencyService;
import com.colligendis.server.database.numista.service.NTypeService;
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

	private final PauseLock pauseLock = new PauseLock("CurrencyParser");

	@Override
	protected Mono<NumistaPage> parse(NumistaPage numistaPage) {
		return Mono.defer(() -> {
			Map<String, String> devise = NumistaParseUtils.getAttributeWithTextSingleOption(
					numistaPage.page, "#devise", "value");

			if (devise == null) {
				numistaPage.getPipelineStepLogger()
						.warning("Currency: not found for nid: {} - Can't find Currency (devise) while parsing page",
								numistaPage.nid);
				return Mono.error(new ParserException(
						"Can't find Currency (devise) while parsing page for nid: " + numistaPage.nid));
			}

			String currencyNid = devise.get("value");

			return pauseLock.awaitIfPaused()
					.then(currencyService.findByNid(currencyNid, numistaPage.getPipelineStepLogger()))
					.map(ExecutionResult<Currency>::getNode)
					.switchIfEmpty(
							handleCurrencyNotFound(currencyNid, numistaPage))
					.flatMap(currency -> nTypeService.setCurrency(numistaPage.nType, currency, numistaPage
							.getNumistaParserUserMono(), numistaPage.getPipelineStepLogger()))
					.flatMap(executionResult -> {
						if (executionResult.getStatus().equals(ExecutionStatus.ERROR)) {
							numistaPage.getPipelineStepLogger().error(
									"Currency: error while setting relationship between NType and Currency for nid: {} and currency nid: {}",
									numistaPage.nid, currencyNid);
						}
						if (executionResult.getStatus().equals(ExecutionStatus.RELATIONSHIP_IS_EXISTS)) {
							numistaPage.getPipelineStepLogger().info(
									"Currency: relationship between NType and Currency for nid: {} and currency nid: {} already exists",
									numistaPage.nid, currencyNid);
						}
						if (executionResult.getStatus().equals(ExecutionStatus.RELATIONSHIP_WAS_CREATED)) {
							numistaPage.getPipelineStepLogger().warning(
									"Currency: set to {}", currencyNid);
						}

						return Mono.just(numistaPage);
					});
		});
	}

	private Mono<Currency> handleCurrencyNotFound(String currencyNid,
			NumistaPage numistaPage) {

		boolean acquiredLock = pauseLock.pause();
		if (!acquiredLock) {
			// Another thread is already loading currencies - wait and retry
			return pauseLock.awaitIfPaused()
					.then(currencyService.findByNid(currencyNid, numistaPage.getPipelineStepLogger()))
					.map(ExecutionResult<Currency>::getNode);
		}

		return loadAndParseCurrenciesByIssuerCodeFromPHPRequestMono(numistaPage)
				.subscribeOn(Schedulers.boundedElastic())
				.doFinally(signal -> pauseLock.resume())
				.then(currencyService.findByNid(currencyNid, numistaPage.getPipelineStepLogger()))
				.map(ExecutionResult<Currency>::getNode);

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

	private Mono<Currency> processCurrencyOption(Element option,
			NumistaPage numistaPage) {
		String nid = option.attr("value");
		String fullName = extractFullName(option.text());
		String cleanName = stripName(fullName);

		return currencyService
				.findByNidWithSave(nid, fullName, cleanName, numistaPage.getNumistaParserUserMono(),
						numistaPage.getPipelineStepLogger())
				.map(ExecutionResult<Currency>::getNode)
				.switchIfEmpty(Mono.error(new ParserException("Can't find or save currency " + nid)))
				.flatMap(currency -> yearPeriodParserService.parsePeriods(fullName)
						.flatMap(periods -> saveCurrencyWithPeriods(currency, periods, numistaPage)
								.thenReturn(currency)))
				.flatMap(currency -> currencyService.setIssuer(currency, numistaPage.issuer,
						numistaPage.getNumistaParserUserMono(), numistaPage.getPipelineStepLogger())
						.thenReturn(currency));
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

	private Mono<Currency> setYearRelationships(Currency currency,
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

		Mono<Void> setFrom = fromYears.isEmpty()
				? Mono.empty()
				: currencyService.setCirculatedFromYears(currency, fromYears,
						numistaPage.getNumistaParserUserMono(), numistaPage.getPipelineStepLogger()).then();

		Mono<Void> setTill = tillYears.isEmpty()
				? Mono.empty()
				: currencyService.setCirculatedTillYears(currency, tillYears,
						numistaPage.getNumistaParserUserMono(), numistaPage.getPipelineStepLogger()).then();

		return setFrom.then(setTill).thenReturn(currency);
	}

	private Mono<Currency> saveCurrencyWithPeriods(Currency currency,
			CirculationPeriods periods,
			NumistaPage numistaPage) {

		return currencyService
				.update(currency, numistaPage.getNumistaParserUserMono(), numistaPage.getPipelineStepLogger())
				.map(ExecutionResult<Currency>::getNode)
				.defaultIfEmpty(currency)
				.flatMap(updated -> {
					Currency cur = updated != null ? updated : currency;
					return setYearRelationships(cur, periods, numistaPage);
				});
	}

}
