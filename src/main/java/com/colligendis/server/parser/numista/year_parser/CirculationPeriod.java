package com.colligendis.server.parser.numista.year_parser;

import java.util.Optional;

import org.springframework.lang.Nullable;

import com.colligendis.server.database.common.model.Year;

public record CirculationPeriod(
        Optional<Year> from,
        Optional<Year> till,
        @Nullable String kind) {
}