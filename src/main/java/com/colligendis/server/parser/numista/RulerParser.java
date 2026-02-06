package com.colligendis.server.parser.numista;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;

import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import com.colligendis.server.database.ColligendisUser;
import com.colligendis.server.database.N4JUtil;
import com.colligendis.server.database.common.model.Year;
import com.colligendis.server.database.exception.NotFoundError;
import com.colligendis.server.database.numista.model.Issuer;
import com.colligendis.server.database.numista.model.Ruler;
import com.colligendis.server.database.numista.model.RulerGroup;
import com.colligendis.server.database.numista.service.NTypeService;
import com.colligendis.server.database.numista.service.RulerGroupService;
import com.colligendis.server.database.numista.service.RulerService;
import com.colligendis.server.parser.PauseLock;
import com.colligendis.server.parser.numista.year_parser.YearPeriodParserService;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class RulerParser {

	public static final RulerParser instance = new RulerParser();

	private static final String ANSI_BLUE = "\u001B[34m";
	private static final String ANSI_RED = "\u001B[31m";
	private static final String ANSI_RESET = "\u001B[0m";

	private final PauseLock pauseLock = new PauseLock("RulerParserLockable");

	// Cached services - lazily initialized
	private RulerService rulerService;
	private NTypeService nTypeService;
	private RulerGroupService rulerGroupService;
	private final YearPeriodParserService yearPeriodParserService = new YearPeriodParserService();

	private RulerService getRulerService() {
		if (rulerService == null) {
			rulerService = N4JUtil.getInstance().numistaServices.rulerService;
		}
		return rulerService;
	}

	private NTypeService getNTypeService() {
		if (nTypeService == null) {
			nTypeService = N4JUtil.getInstance().numistaServices.nTypeService;
		}
		return nTypeService;
	}

	private RulerGroupService getRulerGroupService() {
		if (rulerGroupService == null) {
			rulerGroupService = N4JUtil.getInstance().numistaServices.rulerGroupService;
		}
		return rulerGroupService;
	}

	public Function<NumistaPage, Mono<NumistaPage>> parse = page -> Mono.defer(() -> parseRulers(page));

	private Mono<NumistaPage> parseRulers(NumistaPage numistaPage) {
		if (log.isInfoEnabled()) {
			log.info("nid: {}{}{} - Parsing rulers by #ruler attribute value",
					ANSI_BLUE, numistaPage.nid, ANSI_RESET);
		}

		List<Map<String, String>> rulerMaps = collectRulerMaps(numistaPage);

		if (rulerMaps.isEmpty()) {
			return Mono.just(numistaPage);
		}

		return Flux.fromIterable(rulerMaps)
				.flatMap(map -> processRulerMap(map, numistaPage))
				.collectList()
				.flatMap(foundRulers -> linkRulersToNType(foundRulers, numistaPage))
				.thenReturn(numistaPage);
	}

	private List<Map<String, String>> collectRulerMaps(NumistaPage numistaPage) {
		List<Map<String, String>> rulerMaps = new ArrayList<>();
		for (int index = 0;; index++) {
			Map<String, String> rulerMap = NumistaParseUtils.getAttributeWithTextSingleOption(
					numistaPage.page, "#ruler" + index, "value");
			if (rulerMap == null) {
				break;
			}
			rulerMaps.add(rulerMap);
		}
		return rulerMaps;
	}

	private Mono<Ruler> processRulerMap(Map<String, String> map, NumistaPage numistaPage) {
		String rulerNid = map.get("value");

		return pauseLock.awaitIfPaused()
				.then(getRulerService().findByNid(rulerNid))
				.flatMap(rulerEither -> rulerEither.fold(
						error -> handleRulerNotFound(error, rulerNid, numistaPage),
						Mono::just));
	}

	private Mono<Ruler> handleRulerNotFound(
			com.colligendis.server.database.exception.DatabaseException error,
			String rulerNid,
			NumistaPage numistaPage) {

		if (!(error instanceof NotFoundError)) {
			if (log.isErrorEnabled()) {
				log.error("nid: {}{}{} - Error finding ruler by nid: {}{}{}, error: {}{}{}",
						ANSI_BLUE, numistaPage.nid, ANSI_RESET,
						ANSI_BLUE, rulerNid, ANSI_RESET,
						ANSI_RED, error.message(), ANSI_RESET);
			}
			return Mono.empty();
		}

		boolean acquiredLock = pauseLock.pause();

		if (!acquiredLock) {
			// Another thread is already loading - wait and retry
			return pauseLock.awaitIfPaused()
					.then(getRulerService().findByNid(rulerNid))
					.flatMap(result -> result.fold(
							err -> {
								if (log.isErrorEnabled()) {
									log.error(
											"nid: {}{}{} - Can't find Ruler by nid: {}{}{} after waiting, error: {}{}{}",
											ANSI_BLUE, numistaPage.nid, ANSI_RESET,
											ANSI_BLUE, rulerNid, ANSI_RESET,
											ANSI_RED, err.message(), ANSI_RESET);
								}
								return Mono.empty();
							},
							Mono::just));
		}

		// This thread acquired the lock - load rulers
		return numistaPage.colligendisUserM
				.flatMap(user -> parseRulersByIssuerCodeFromPHPRequestMono(numistaPage.issuer, user)
						.subscribeOn(Schedulers.boundedElastic())
						.doFinally(signal -> pauseLock.resume())
						.then(getRulerService().findByNid(rulerNid))
						.flatMap(r2 -> r2.fold(
								err -> {
									if (log.isErrorEnabled()) {
										log.error(
												"nid: {}{}{} - Can't find Ruler by nid: {}{}{} after parsing, error: {}{}{}",
												ANSI_BLUE, numistaPage.nid, ANSI_RESET,
												ANSI_BLUE, rulerNid, ANSI_RESET,
												ANSI_RED, err.message(), ANSI_RESET);
									}
									return Mono.empty();
								},
								Mono::just)));
	}

	private Mono<Void> linkRulersToNType(List<Ruler> foundRulers, NumistaPage numistaPage) {
		if (numistaPage.nType == null || foundRulers.isEmpty()) {
			return Mono.empty();
		}

		return getNTypeService()
				.setRulersMono(
						Mono.just(numistaPage.nType),
						Mono.just(foundRulers),
						numistaPage.colligendisUserM)
				.doOnNext(result -> result.simpleFold(log))
				.then();
	}

	private static final String RULERS_BY_ISSUER_PREFIX = "https://en.numista.com/catalogue/get_rulers.php?country=";
	private static final char FIGURE_SPACE = '\u2007';

	public Mono<Boolean> parseRulersByIssuerCodeFromPHPRequestMono(Issuer issuer, ColligendisUser colligendisUser) {
		String issuerCode = issuer.getNumistaCode();
		String url = RULERS_BY_ISSUER_PREFIX + issuerCode;

		return Mono.fromCallable(() -> NumistaParseUtils.loadPageByURL(url))
				.subscribeOn(Schedulers.boundedElastic())
				.flatMap(document -> {
					if (document == null) {
						return Mono.error(new RuntimeException("Can't get PHP request with URL: " + url));
					}

					Elements options = document.select("option");
					AtomicReference<RulerGroup> currentGroupRef = new AtomicReference<>(null);

					return Flux.fromIterable(options)
							.concatMap(option -> processOption(option, colligendisUser, currentGroupRef))
							.collectList()
							.flatMap(rulers -> linkRulersToIssuer(rulers, issuer, colligendisUser));
				});
	}

	private Mono<Boolean> linkRulersToIssuer(List<Ruler> rulers, Issuer issuer, ColligendisUser user) {
		if (rulers.isEmpty()) {
			return Mono.just(true);
		}
		return getRulerService().setIssuer(rulers, issuer, user)
				.doOnNext(either -> either.simpleFold(log))
				.map(either -> true);
	}

	private Mono<Ruler> processOption(
			Element option,
			ColligendisUser user,
			AtomicReference<RulerGroup> currentGroupRef) {

		String nid = NumistaParseUtils.getAttribute(option, "value");
		if (nid == null) {
			log.error("Can't find a nid for parsed Ruler");
			return Mono.empty();
		}

		String fullName = option.text();

		// Group option - nid starts with "g"
		if (nid.startsWith("g")) {
			return processRulerGroup(nid, fullName, user, currentGroupRef);
		}

		return processRulerOption(nid, fullName, user, currentGroupRef);
	}

	private Mono<Ruler> processRulerGroup(
			String nid,
			String fullName,
			ColligendisUser user,
			AtomicReference<RulerGroup> currentGroupRef) {

		return getRulerGroupService().findByNidWithSave(nid, fullName, user)
				.doOnNext(either -> either.fold(
						err -> {
							log.error("Error finding ruler group by nid: {}, error: {}", nid, err.message());
							return null;
						},
						group -> {
							currentGroupRef.set(group);
							return group;
						}))
				.then(Mono.empty()); // Groups are not returned in Flux<Ruler>
	}

	private Mono<Ruler> processRulerOption(
			String nid,
			String fullName,
			ColligendisUser user,
			AtomicReference<RulerGroup> currentGroupRef) {

		// If option's name doesn't contain figure space, ruler has no group
		if (fullName.indexOf(FIGURE_SPACE) < 0) {
			currentGroupRef.set(null);
		}

		String rulerName = extractRulerName(fullName);
		RulerGroup currentGroup = currentGroupRef.get();

		return getRulerService().findByNidWithSave(nid, rulerName, user)
				.flatMap(either -> either.fold(
						err -> {
							log.error("Error finding ruler by nid: {}, error: {}", nid, err.message());
							return Mono.empty();
						},
						ruler -> applyPeriodsAndGroup(ruler, fullName, user, currentGroup)));
	}

	private Mono<Ruler> applyPeriodsAndGroup(
			Ruler ruler,
			String fullName,
			ColligendisUser user,
			RulerGroup rulerGroup) {

		Mono<Ruler> withGroup = applyRulerGroup(ruler, rulerGroup, user);

		return withGroup.flatMap(r -> applyCirculationPeriods(r, fullName, user));
	}

	private Mono<Ruler> applyRulerGroup(Ruler ruler, RulerGroup rulerGroup, ColligendisUser user) {
		if (rulerGroup == null) {
			return Mono.just(ruler);
		}

		return getRulerService().setRulerGroup(ruler, rulerGroup, user)
				.flatMap(either -> either.fold(
						err -> {
							log.error("Error setting ruler group for nid: {}, error: {}", ruler.getNid(),
									err.message());
							return Mono.empty();
						},
						res -> Mono.just(ruler)));
	}

	private Mono<Ruler> applyCirculationPeriods(Ruler ruler, String fullName, ColligendisUser user) {
		return yearPeriodParserService.parsePeriods(fullName, user)
				.flatMap(periods -> {
					if (periods.periods().isEmpty()) {
						return Mono.just(ruler);
					}

					List<Year> fromYears = periods.periods().stream()
							.flatMap(p -> p.from().stream())
							.toList();
					List<Year> tillYears = periods.periods().stream()
							.flatMap(p -> p.till().stream())
							.toList();

					return setRulerYears(ruler, fromYears, tillYears, user);
				})
				.defaultIfEmpty(ruler);
	}

	private Mono<Ruler> setRulerYears(Ruler ruler, List<Year> fromYears, List<Year> tillYears, ColligendisUser user) {
		Mono<Void> setFrom = fromYears.isEmpty()
				? Mono.empty()
				: getRulerService().setRulersFromYears(ruler, fromYears, user)
						.doOnNext(either -> either.simpleFold(log))
						.then();

		Mono<Void> setTill = tillYears.isEmpty()
				? Mono.empty()
				: getRulerService().setRulersTillYears(ruler, tillYears, user)
						.doOnNext(either -> either.simpleFold(log))
						.then();

		return setFrom.then(setTill).thenReturn(ruler);
	}

	private String extractRulerName(String fullName) {
		int parenIdx = fullName.indexOf('(');
		String name = parenIdx > 0 ? fullName.substring(0, parenIdx) : fullName;
		// Remove figure space (U+2007) and trim
		int len = name.length();
		int start = 0;
		int end = len;

		while (start < end && (name.charAt(start) <= ' ' || name.charAt(start) == FIGURE_SPACE)) {
			start++;
		}
		while (end > start && (name.charAt(end - 1) <= ' ' || name.charAt(end - 1) == FIGURE_SPACE)) {
			end--;
		}

		return name.substring(start, end);
	}
}
