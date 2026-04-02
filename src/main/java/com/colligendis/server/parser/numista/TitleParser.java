package com.colligendis.server.parser.numista;

import java.util.Objects;

import org.springframework.stereotype.Component;

import com.colligendis.server.database.numista.model.NType;
import com.colligendis.server.parser.ParsingStatus;
import com.colligendis.server.parser.numista.exception.ParserException;

import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Mono;

@Slf4j
@Component
public class TitleParser extends Parser {

	@Override
	protected Mono<NumistaPage> parse(NumistaPage numistaPage) {
		return Mono.defer(() -> {
			NType nType = numistaPage.nType;
			// Extract title
			String title = NumistaParseUtils.getAttribute(numistaPage.page.selectFirst("#designation"), "value");
			if (title == null || title.isEmpty()) {
				numistaPage.getPipelineStepLogger().error("Title is empty for nid: {}", numistaPage.nid);
				numistaPage.parsingStatus = ParsingStatus.FAILED;
				return Mono.error(new ParserException("Title is empty for nid: " + numistaPage.nid));
			}

			if (!Objects.equals(nType.getTitle(), title)) {
				numistaPage.getPipelineStepLogger().warning("Title changed for nid: {} from {} to {}",
						numistaPage.nid,
						nType.getTitle(), title);
				nType.setTitle(title);
				numistaPage.parsingStatus = ParsingStatus.CHANGED;
			} else {
				numistaPage.getPipelineStepLogger().info("Title: wasn't changed '{}'", title);
			}

			return Mono.just(numistaPage);

		});
	}

}
