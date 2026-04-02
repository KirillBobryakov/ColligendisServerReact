package com.colligendis.server.parser.numista;

import reactor.core.publisher.Mono;

public abstract class Parser {

	protected abstract Mono<NumistaPage> parse(NumistaPage numistaPage);

}
