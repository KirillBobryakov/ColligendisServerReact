package com.colligendis.server.parser.numista;

import java.util.concurrent.atomic.AtomicReference;

import org.springframework.stereotype.Component;

import com.colligendis.server.database.ColligendisUserService;
import com.colligendis.server.database.numista.service.NTypeService;
import com.colligendis.server.logger.BaseLogger;

import lombok.RequiredArgsConstructor;
import reactor.core.publisher.Mono;

@Component
@RequiredArgsConstructor
public class NumistaPipeline {

	private final PageLoader pageLoader;
	private final NTypeService nTypeService;
	private final TitleParser titleParser;
	private final CollectibleTypeParser collectibleTypeParser;
	private final IssuerParser issuerParser;
	private final RulerParser rulerParser;
	private final IssuingEntityParser issuingEntityParser;
	private final CurrencyParser currencyParser;
	private final ColligendisUserService colligendisUserService;

	public Mono<NumistaPage> pipeline(String nid) {
		AtomicReference<BaseLogger> pipelineLoggerRef = new AtomicReference<>();
		return NumistaPage.create(nid, colligendisUserService, nTypeService)
				.doOnNext(p -> pipelineLoggerRef.set(p.getPipelineStepLogger()))
				.flatMap(pageLoader::parse)
				.flatMap(NumistaPage::loadNType)
				.flatMap(titleParser::parse)
				.flatMap(collectibleTypeParser::parse)
				.flatMap(issuerParser::parse)
				.flatMap(rulerParser::parse)
				.flatMap(issuingEntityParser::parse)
				.flatMap(currencyParser::parse)
				.flatMap(NumistaPage::saveNType)
				.doFinally(signalType -> {
					BaseLogger log = pipelineLoggerRef.get();
					if (log != null) {
						log.flushToTerminal();
					}
				});
	}
}
