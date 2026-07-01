package com.colligendis.server.parser.numista;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.springframework.stereotype.Component;

import com.colligendis.server.database.ColligendisUser;
import com.colligendis.server.database.common.model.Year;
import com.colligendis.server.database.numista.model.Issuer;
import com.colligendis.server.database.numista.model.RulingAuthority;
import com.colligendis.server.database.numista.model.RulingAuthorityGroup;
import com.colligendis.server.database.numista.service.NTypeService;
import com.colligendis.server.database.numista.service.RulingAuthorityGroupService;
import com.colligendis.server.database.numista.service.RulingAuthorityService;
import com.colligendis.server.database.result.CreateNodeExecutionStatus;
import com.colligendis.server.database.result.CreateRelationshipExecutionStatus;
import com.colligendis.server.database.result.ExecutionResult;
import com.colligendis.server.database.result.FindExecutionStatus;
import com.colligendis.server.parser.PauseLock;
import com.colligendis.server.parser.numista.exception.ParserException;
import com.colligendis.server.parser.numista.year_parser.CirculationPeriod;
import com.colligendis.server.parser.numista.year_parser.CirculationPeriods;
import com.colligendis.server.parser.numista.year_parser.YearPeriodParserService;
import com.colligendis.server.util.web.WebPageClient;
import com.colligendis.server.util.web.WebPageLoadException;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;
import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class RulingAuthorityParser extends Parser {

	private static final PauseLock PAUSE_LOCK = new PauseLock("RulerParser");

	// Cached services - lazily initialized
	private final RulingAuthorityService rulingAuthorityService;
	private final NTypeService nTypeService;
	private final RulingAuthorityGroupService rulingAuthorityGroupService;
	private final YearPeriodParserService yearPeriodParserService;
	private final WebPageClient webPageClient;

	@Override
	protected Mono<NumistaPage> parse(NumistaPage numistaPage) {
		return Mono.defer(() -> {

			List<Map<String, String>> rulerMaps = collectRulerMaps(numistaPage);

			if (rulerMaps.isEmpty()) {
				numistaPage.getPipelineStepLogger()
						.info("RulingAuthorities: not found for nid: {} - Can't find RulingAuthorities on the page",
								numistaPage.nid);
				return Mono.just(numistaPage);
			}

			return Flux.fromIterable(rulerMaps)
					.flatMap(map -> processRulerMap(map, numistaPage))
					.collectList()
					.flatMap(foundRulers -> linkRulersToNType(foundRulers, numistaPage))
					.thenReturn(numistaPage);
		});
	}

	private List<Map<String, String>> collectRulerMaps(NumistaPage numistaPage) {
		List<Map<String, String>> rulerMaps = new ArrayList<>();
		for (int index = 0;; index++) {
			Map<String, String> rulerMap = NumistaParseUtils.getAttributeWithTextSelectedOrFirstOption(
					numistaPage.page, "#ruler" + index, "value", numistaPage);
			if (rulerMap == null) {
				break;
			}
			rulerMaps.add(rulerMap);
		}
		return rulerMaps;
	}

	private Mono<RulingAuthority> processRulerMap(Map<String, String> map, NumistaPage numistaPage) {
		String raw = map.get("value");
		final String rulerNid = raw == null ? null : raw.strip();

		return PAUSE_LOCK.awaitIdle()
				.then(rulingAuthorityService.findByNid(rulerNid, numistaPage.getPipelineStepLogger()))
				.flatMap(executionResult -> {
					if (executionResult.getStatus().equals(FindExecutionStatus.FOUND)) {
						return Mono.just(executionResult.getNode());
					} else {
						numistaPage.getPipelineStepLogger().warning(
								"Failed to find RulingAuthority, try to resolve from Numista PHP request: {}",
								executionResult.getStatus());
						executionResult.logError(numistaPage.getPipelineStepLogger());
						return resolveRulerAfterCacheMiss(rulerNid, numistaPage);
					}
				});
	}

	/**
	 * Ruler was not in the graph (or mapping failed). Optionally refresh rulers
	 * from Numista PHP, then retry lookup.
	 * Warnings are logged only if lookup still fails after that — not on the first
	 * cache miss (avoids false alarms).
	 */
	private Mono<RulingAuthority> resolveRulerAfterCacheMiss(String rulerNid, NumistaPage numistaPage) {
		return PAUSE_LOCK.runExclusiveOrElse(
				() -> parseRulersByIssuerCodeFromPHPRequestMono(numistaPage.issuer,
						numistaPage.getNumistaParserUserMono(), numistaPage)
						.subscribeOn(Schedulers.boundedElastic())
						.then(rulingAuthorityService.findByNid(rulerNid, numistaPage.getPipelineStepLogger()))
						.flatMap(executionResult -> toRulingAuthorityOrError(executionResult, rulerNid, numistaPage)),
				() -> rulingAuthorityService.findByNid(rulerNid, numistaPage.getPipelineStepLogger())
						.flatMap(executionResult -> toRulingAuthorityOrError(executionResult, rulerNid, numistaPage)));
	}

	private Mono<RulingAuthority> toRulingAuthorityOrError(
			ExecutionResult<RulingAuthority, FindExecutionStatus> executionResult,
			String rulerNid,
			NumistaPage numistaPage) {
		if (executionResult.getStatus().equals(FindExecutionStatus.FOUND)) {
			return Mono.just(executionResult.getNode());
		}
		numistaPage.getPipelineStepLogger().error("Failed to find RulingAuthority: {}",
				executionResult.getStatus());
		executionResult.logError(numistaPage.getPipelineStepLogger());
		return Mono.error(new ParserException("Failed to find RulingAuthority: " + rulerNid));
	}

	private Mono<Void> linkRulersToNType(List<RulingAuthority> foundRulers, NumistaPage numistaPage) {
		if (numistaPage.nType == null || foundRulers.isEmpty()) {
			return Mono.empty();
		}

		return nTypeService
				.setRulingAuthorities(numistaPage.nType, foundRulers, numistaPage.getNumistaParserUserMono(),
						numistaPage.getPipelineStepLogger())
				.flatMap(executionResult -> {
					if (executionResult.getStatus().equals(CreateRelationshipExecutionStatus.IS_ALREADY_EXISTS)) {
						numistaPage.getPipelineStepLogger()
								.info("Rulers: {}",
										foundRulers.stream().map(RulingAuthority::getNid)
												.collect(Collectors.joining(",")));
						return Mono.just(numistaPage);
					}
					if (!executionResult.getStatus().equals(CreateRelationshipExecutionStatus.WAS_CREATED)) {
						numistaPage.getPipelineStepLogger()
								.error("Rulers error while setting relationship between NType and Rulers for nid: {} and rulers numistaCodes: {} - Error: {}",
										numistaPage.nid,
										foundRulers.stream().map(RulingAuthority::getNid)
												.collect(Collectors.joining(",")),
										executionResult.getStatus());
						return Mono.<NumistaPage>error(
								new ParserException("Error setting Rulers: " + executionResult.getStatus()));
					}
					numistaPage.getPipelineStepLogger()
							.warning("Rulers: set to {}",
									foundRulers.stream().map(RulingAuthority::getName)
											.collect(Collectors.joining(",")));
					return Mono.just(numistaPage);
				})
				.then();
	}

	private static final String RULERS_BY_ISSUER_PREFIX = "https://en.numista.com/catalogue/get_rulers.php?country=";
	private static final char FIGURE_SPACE = '\u2007';

	public Mono<Boolean> parseRulersByIssuerCodeFromPHPRequestMono(Issuer issuer, Mono<ColligendisUser> user,
			NumistaPage numistaPage) {
		String issuerCode = issuer.getNumistaCode();
		String url = RULERS_BY_ISSUER_PREFIX + issuerCode + "&e=1";

		return loadHtmlPage(url, numistaPage)
				.switchIfEmpty(Mono.error(new RuntimeException("Can't get PHP request with URL: " + url)))
				.flatMap(document -> {
					Elements options = document.select("option");
					AtomicReference<RulingAuthorityGroup> currentGroupRef = new AtomicReference<>(null);

					return Flux.fromIterable(options)
							.concatMap(
									option -> processOption(option, user, currentGroupRef, numistaPage))
							.collectList()
							.flatMap(rulers -> linkRulersToIssuer(rulers, issuer, user, numistaPage));
				});
	}

	private Mono<Boolean> linkRulersToIssuer(List<RulingAuthority> rulers, Issuer issuer, Mono<ColligendisUser> user,
			NumistaPage numistaPage) {
		if (rulers.isEmpty()) {
			return Mono.just(true);
		}
		return rulingAuthorityService.setIssuer(rulers, issuer, user, numistaPage.getPipelineStepLogger())
				.thenReturn(true);
	}

	private Mono<RulingAuthority> processOption(
			Element option,
			Mono<ColligendisUser> user,
			AtomicReference<RulingAuthorityGroup> currentGroupRef,
			NumistaPage numistaPage) {

		String nid = NumistaParseUtils.getAttribute(option, "value");
		if (nid == null) {
			numistaPage.getPipelineStepLogger()
					.error("Ruler not found for nid: {} - Can't find a nid for parsed Ruler on page", numistaPage.nid);
			return Mono.empty();
		}

		String fullName = option.text();

		// Group option - nid starts with "g"
		if (nid.startsWith("g")) {
			return processRulingAuthorityGroup(nid, fullName, user, currentGroupRef, numistaPage);
		}

		return processRulingAuthorityOption(nid, fullName, user, currentGroupRef, numistaPage);
	}

	private Mono<RulingAuthority> processRulingAuthorityGroup(
			String nid,
			String fullName,
			Mono<ColligendisUser> user,
			AtomicReference<RulingAuthorityGroup> currentGroupRef,
			NumistaPage numistaPage) {

		return rulingAuthorityGroupService.findByNidWithCreate(nid, fullName, user, numistaPage.getPipelineStepLogger())
				.doOnNext(executionResult -> {
					currentGroupRef.set(executionResult.getNode());
				})
				.then(Mono.empty()); // Groups are not returned in Flux<Ruler>
	}

	private Mono<RulingAuthority> processRulingAuthorityOption(
			String nid,
			String fullName,
			Mono<ColligendisUser> user,
			AtomicReference<RulingAuthorityGroup> currentGroupRef,
			NumistaPage numistaPage) {

		// If option's name doesn't contain figure space, ruler has no group
		if (fullName.indexOf(FIGURE_SPACE) < 0) {
			currentGroupRef.set(null);
		}

		String rulerName = extractRulerName(fullName);
		RulingAuthorityGroup currentGroup = currentGroupRef.get();

		return rulingAuthorityService.findByNidWithCreate(nid, rulerName, user, numistaPage.getPipelineStepLogger())
				.flatMap(executionResult -> {
					if (executionResult.getStatus().equals(FindExecutionStatus.FOUND)
							|| executionResult.getStatus().equals(CreateNodeExecutionStatus.WAS_CREATED)) {
						return Mono.just(executionResult.getNode());
					} else {
						numistaPage.getPipelineStepLogger().error("Failed to find RulingAuthority: {}",
								executionResult.getStatus());
						executionResult.logError(numistaPage.getPipelineStepLogger());
						return Mono.error(new ParserException("Failed to find RulingAuthority: " + nid));
					}
				})
				.flatMap(rulingAuthority -> applyPeriodsAndGroup(rulingAuthority, fullName, user, currentGroup,
						numistaPage));
	}

	private Mono<RulingAuthority> applyPeriodsAndGroup(
			RulingAuthority rulingAuthority,
			String fullName,
			Mono<ColligendisUser> user,
			RulingAuthorityGroup rulingAuthorityGroup,
			NumistaPage numistaPage) {

		if (rulingAuthorityGroup != null) {
			Mono<RulingAuthority> withGroup = applyRulingAuthorityGroup(rulingAuthority, rulingAuthorityGroup, user,
					numistaPage);
			return withGroup.flatMap(r -> applyCirculationPeriods(r, fullName, user, numistaPage));
		}

		return applyCirculationPeriods(rulingAuthority, fullName, user, numistaPage);
	}

	private Mono<RulingAuthority> applyRulingAuthorityGroup(RulingAuthority rulingAuthority,
			RulingAuthorityGroup rulingAuthorityGroup, Mono<ColligendisUser> user, NumistaPage numistaPage) {
		if (rulingAuthorityGroup == null) {
			return Mono.just(rulingAuthority);
		}

		return rulingAuthorityService
				.setRulingAuthorityGroup(rulingAuthority, rulingAuthorityGroup, user,
						numistaPage.getPipelineStepLogger())
				.thenReturn(rulingAuthority);
	}

	private Mono<RulingAuthority> applyCirculationPeriods(RulingAuthority rulingAuthority, String fullName,
			Mono<ColligendisUser> user, NumistaPage numistaPage) {
		return yearPeriodParserService.parsePeriods(fullName)
				.flatMap(periods -> {
					if (periods.periods().isEmpty()) {
						return Mono.just(rulingAuthority);
					}

					YearLists yearLists = collectYearLists(periods);

					return setRulingAuthorityYears(rulingAuthority, yearLists.fromYears(), yearLists.tillYears(),
							user, numistaPage);
				})
				.defaultIfEmpty(rulingAuthority);
	}

	static YearLists collectYearLists(CirculationPeriods periods) {
		List<Year> fromYears = new ArrayList<>();
		List<Year> tillYears = new ArrayList<>();

		for (CirculationPeriod period : periods.periods()) {
			period.from().ifPresent(fromYears::add);
			if (period.till().isPresent()) {
				period.till().ifPresent(tillYears::add);
			} else {
				period.from().ifPresent(tillYears::add);
			}
		}

		return new YearLists(fromYears, tillYears);
	}

	record YearLists(List<Year> fromYears, List<Year> tillYears) {
	}

	private Mono<RulingAuthority> setRulingAuthorityYears(RulingAuthority rulingAuthority, List<Year> fromYears,
			List<Year> tillYears,
			Mono<ColligendisUser> user,
			NumistaPage numistaPage) {
		Mono<Void> setFrom = fromYears.isEmpty()
				? Mono.empty()
				: rulingAuthorityService
						.setRulingAuthoritiesFromYears(rulingAuthority, fromYears, user,
								numistaPage.getPipelineStepLogger())
						.then();

		Mono<Void> setTill = tillYears.isEmpty()
				? Mono.empty()
				: rulingAuthorityService
						.setRulingAuthoritiesTillYears(rulingAuthority, tillYears, user,
								numistaPage.getPipelineStepLogger())
						.then();

		return setFrom.then(setTill).thenReturn(rulingAuthority);
	}

	private Mono<Document> loadHtmlPage(String url, NumistaPage numistaPage) {
		return numistaPage.getNumistaParserUserMono()
				.map(RulingAuthorityParser::resolveCookie)
				.defaultIfEmpty("")
				.flatMap(cookie -> webPageClient.loadPageDocument(url, cookie))
				.onErrorResume(WebPageLoadException.class, e -> {
					numistaPage.getPipelineStepLogger().error("RulingAuthorityParser: can't load {}: {}", url,
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
