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
	private final DenominationParser denominationParsing;
	private final CommemoratedEventParser commemoratedEventParser;
	private final SeriesParser seriesParser;
	private final DemonetizedAndIssueDateParser demonetizedAndIssueDateParser;
	private final ReferenceNumberParser referenceNumberParser;
	private final TechnicalDataParser technicalDataParser;
	private final NTypePartParser nTypePartParser;
	private final MintParser mintParser;
	private final VariantsParser variantsParser;
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
				.flatMap(denominationParsing::parse)
				.flatMap(commemoratedEventParser::parse)
				.flatMap(seriesParser::parse)
				.flatMap(demonetizedAndIssueDateParser::parse)
				.flatMap(referenceNumberParser::parse)
				.flatMap(technicalDataParser::parse)
				.flatMap(nTypePartParser::parse)
				.flatMap(mintParser::parse)
				.flatMap(variantsParser::parse)
				.flatMap(NumistaPage::saveNType)
				.doFinally(signalType -> {
					BaseLogger log = pipelineLoggerRef.get();
					if (log != null) {
						log.flushToTerminal();
					}
				});
	}
}
