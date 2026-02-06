package com.colligendis.server.parser.numista.year_parser;

import java.util.List;

public record CirculationPeriods(List<CirculationPeriod> periods) {
    static CirculationPeriods empty() {
        return new CirculationPeriods(List.of());
    }
}