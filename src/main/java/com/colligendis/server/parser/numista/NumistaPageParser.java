package com.colligendis.server.parser.numista;

import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

@Component
@Slf4j
@RequiredArgsConstructor
public class NumistaPageParser {

	private final NumistaPipeline numistaPipeline;

	public void parseAll(Flux<String> nids) {

		nids.flatMap(nid -> runPipeline(nid).subscribeOn(Schedulers.boundedElastic()), 2)
				.doOnNext(result -> {
					if (result.status == ParseResult.Status.SUCCESS) {
						log.info("Parsed Numista Page with nid: {}", result.nid);
					} else {
						log.error("Error parsing Numista Page with nid: {}", result.nid, result.error);
					}
				})
				.blockLast();
	}

	// .flatMap(IssuingEntityParser.instance.parse)
	// .flatMap(CurrencyParser.instance.parse)
	// .flatMap(DenominationParser.instance.parse)
	// .flatMap(CommemoratedEventParser.instance.parse)
	// .flatMap(SeriesParser.instance.parse)
	// .flatMap(DemonetizedAndIssueDateParser.instance.parse)
	// .flatMap(ReferenceNumberParser.instance.parse)
	// .flatMap(TechnicalDataParser.instance.parse)
	// .flatMap(NumistaPage::saveNType)

	// public static Mono<NumistaPage> pipeline(String nid) {
	// return NumistaPage.create(nid)
	// .flatMap(PageLoader.getInstance()::parse)
	// .flatMap(NumistaPage::loadNType)
	// .flatMap(TitleParser.getInstance()::parse)
	// .flatMap(CollectibleTypeParser.getInstance()::parse)
	// .flatMap(IssuerParser.getInstance()::parse)
	// .flatMap(RulerParser.instance.parse)
	// .flatMap(NumistaPage::saveNType);
	// }

	public record ParseResult(String nid, Status status, Throwable error) {
		public enum Status {
			SUCCESS,
			FAILED;
		}
	}

	public Mono<ParseResult> runPipeline(String nid) {
		return numistaPipeline.pipeline(nid)
				.map(numistaPage -> new ParseResult(numistaPage.nid, ParseResult.Status.SUCCESS, null))
				.onErrorResume(error -> Mono.just(new ParseResult(nid, ParseResult.Status.FAILED, error)));
	}

}
