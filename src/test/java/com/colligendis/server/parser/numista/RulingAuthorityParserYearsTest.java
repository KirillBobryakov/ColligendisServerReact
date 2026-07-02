package com.colligendis.server.parser.numista;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import com.colligendis.server.database.common.model.Year;
import com.colligendis.server.parser.numista.year_parser.CirculationPeriod;
import com.colligendis.server.parser.numista.year_parser.CirculationPeriods;

class RulingAuthorityParserYearsTest {

	@Test
	void collectYearLists_singleYearCreatesBothFromAndTill() {
		Year year1644 = new Year(1644);
		CirculationPeriods periods = new CirculationPeriods(List.of(
				new CirculationPeriod(Optional.of(year1644), Optional.of(year1644), null),
				new CirculationPeriod(Optional.of(new Year(1655)), Optional.of(new Year(1655)), null),
				new CirculationPeriod(Optional.of(new Year(1667)), Optional.of(new Year(1667)), null),
				new CirculationPeriod(Optional.of(new Year(1669)), Optional.of(new Year(1670)), null)));

		RulingAuthorityParser.YearLists yearLists = RulingAuthorityParser.collectYearLists(periods);

		assertThat(yearLists.fromYears()).extracting(Year::getDateYear)
				.containsExactly(1644, 1655, 1667, 1669);
		assertThat(yearLists.tillYears()).extracting(Year::getDateYear)
				.containsExactly(1644, 1655, 1667, 1670);
	}

	@Test
	void collectYearLists_singleYearWithOnlyFromUsesSameYearForTill() {
		Year year1936 = new Year(1936);
		CirculationPeriods periods = new CirculationPeriods(
				List.of(new CirculationPeriod(Optional.of(year1936), Optional.empty(), null)));

		RulingAuthorityParser.YearLists yearLists = RulingAuthorityParser.collectYearLists(periods);

		assertThat(yearLists.fromYears()).extracting(Year::getDateYear).containsExactly(1936);
		assertThat(yearLists.tillYears()).extracting(Year::getDateYear).containsExactly(1936);
	}
}
