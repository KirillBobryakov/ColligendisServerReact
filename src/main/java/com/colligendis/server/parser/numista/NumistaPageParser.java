package com.colligendis.server.parser.numista;

import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Flux;
import reactor.core.scheduler.Schedulers;

@Slf4j
public class NumistaPageParser {

	public static void parseAll(Flux<String> nids) {

		nids.parallel(2).runOn(Schedulers.boundedElastic()).flatMap(NumistaPage::create)
				.flatMap(PageLoader.loadPageByURL())
				.flatMap(TitleParser.instance.parse)
				.flatMap(CollectibleTypeParser.instance.parse)
				.flatMap(IssuerParser.instance.parse)
				.flatMap(RulerParser.instance.parse)
				.flatMap(IssuingEntityParser.instance.parse)
				.flatMap(CurrencyParser.instance.parse)
				.flatMap(DenominationParser.instance.parse)
				.flatMap(CommemoratedEventParser.instance.parse)
				.flatMap(SeriesParser.instance.parse)
				.flatMap(DemonetizedAndIssueDateParser.instance.parse)
				.flatMap(ReferenceNumberParser.instance.parse)
				.subscribe(t -> {
					log.info("Parsed Numista Page with nid: {}", t.nid);
				});
	}
}
