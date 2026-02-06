package com.colligendis.server.database.common;

import org.springframework.stereotype.Service;

import com.colligendis.server.database.common.service.CalendarService;
import com.colligendis.server.database.common.service.YearService;
import com.colligendis.server.parser.numista.year_parser.YearPeriodParserService;

@Service
public class CommonServices {

    public final YearService yearService;
    public final YearPeriodParserService yearPeriodParserService;
    public final CalendarService calendarService;

    public CommonServices(YearService yearService, YearPeriodParserService yearPeriodParserService,
            CalendarService calendarService) {
        this.yearService = yearService;
        this.yearPeriodParserService = yearPeriodParserService;
        this.calendarService = calendarService;
    }

}
