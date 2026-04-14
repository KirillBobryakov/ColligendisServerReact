package com.colligendis.server.parser.numista;

import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.util.ArrayList;
import java.util.List;

@Component
@Slf4j
@RequiredArgsConstructor
public class NumistaPageParser {

	private final NumistaPipeline numistaPipeline;

	public void parseAll(Flux<String> nids) {
		List<String> errorNids = new ArrayList<>();

		nids.flatMap(nid -> runPipeline(nid).subscribeOn(Schedulers.boundedElastic()), 2)
				.doOnNext(result -> {
					if (result.status == ParseResult.Status.FAILED) {
						errorNids.add(result.nid);
					}
				})
				.blockLast();

		if (!errorNids.isEmpty()) {
			log.error("Error parsing Numista Pages with nids: {}", errorNids);
			for (String errorNid : errorNids) {
				log.error("Error parsing Numista Page with nid: {}", errorNid);
			}
		}
	}

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
