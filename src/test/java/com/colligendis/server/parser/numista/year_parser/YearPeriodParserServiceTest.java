package com.colligendis.server.parser.numista.year_parser;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.colligendis.server.database.ColligendisUserService;
import com.colligendis.server.database.common.model.Year;
import com.colligendis.server.database.common.service.YearService;

import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

@ExtendWith(MockitoExtension.class)
class YearPeriodParserServiceTest {

	@Mock
	private YearService yearService;

	@Mock
	private ColligendisUserService colligendisUserService;

	private YearPeriodParserService parserService;

	@BeforeEach
	void setUp() {
		parserService = new YearPeriodParserService(yearService, colligendisUserService);
		when(yearService.findYearByValueWithCreate(any(Integer.class), any(), any()))
				.thenAnswer(invocation -> Mono.just(new Year(invocation.getArgument(0))));
	}

	@Test
	void parsePeriods_parsesCommaSeparatedYearsAndRanges() {
		String fullName = "Antonio Barberini (Sede Vacante) (1644, 1655, 1667, 1669-1670)";

		StepVerifier.create(parserService.parsePeriods(fullName))
				.assertNext(periods -> {
					assertThat(periods.periods()).hasSize(4);

					assertSingleYearPeriod(periods.periods().get(0), 1644);
					assertSingleYearPeriod(periods.periods().get(1), 1655);
					assertSingleYearPeriod(periods.periods().get(2), 1667);
					assertRangePeriod(periods.periods().get(3), 1669, 1670);
				})
				.verifyComplete();
	}

	@Test
	void parsePeriods_parsesSemicolonSeparatedYearsAndRanges() {
		String fullName = "Guido Ascanio Sforza di Santa Fiora (Sede Vacante) (1549-1550; 1555; 1559)";

		StepVerifier.create(parserService.parsePeriods(fullName))
				.assertNext(periods -> {
					assertThat(periods.periods()).hasSize(3);

					assertRangePeriod(periods.periods().get(0), 1549, 1550);
					assertSingleYearPeriod(periods.periods().get(1), 1555);
					assertSingleYearPeriod(periods.periods().get(2), 1559);
				})
				.verifyComplete();
	}

	@Test
	void parsePeriods_ignoresMonthPrefixes() {
		String fullName = "Lambert Simnel (May-June 1487)";

		StepVerifier.create(parserService.parsePeriods(fullName))
				.assertNext(periods -> assertSingleYearPeriod(periods.periods().get(0), 1487))
				.verifyComplete();
	}

	@Test
	void parsePeriods_ignoresMonthPrefixesWithOrdinalsAndAbbreviations() {
		String fullName = "Interregnum (Jan. 6th - Sept. 1st 1448)";

		StepVerifier.create(parserService.parsePeriods(fullName))
				.assertNext(periods -> assertSingleYearPeriod(periods.periods().get(0), 1448))
				.verifyComplete();
	}

	@Test
	void parsePeriods_parsesOpenEndedYearRange() {
		StepVerifier.create(parserService.parsePeriods("Some Ruler (1990-date)"))
				.assertNext(periods -> {
					assertThat(periods.periods()).hasSize(1);
					assertThat(periods.periods().get(0).from()).isPresent();
					assertThat(periods.periods().get(0).from().get().getValue()).isEqualTo(1990);
					assertThat(periods.periods().get(0).till()).isEmpty();
				})
				.verifyComplete();
	}

	@Test
	void parsePeriods_singleYearHasSameFromAndTill() {
		StepVerifier.create(parserService.parsePeriods("Some Ruler (1936)"))
				.assertNext(periods -> assertSingleYearPeriod(periods.periods().get(0), 1936))
				.verifyComplete();
	}

	private static void assertSingleYearPeriod(CirculationPeriod period, int year) {
		assertThat(period.from()).isPresent();
		assertThat(period.from().get().getValue()).isEqualTo(year);
		assertThat(period.till()).isPresent();
		assertThat(period.till().get().getValue()).isEqualTo(year);
	}

	private static void assertRangePeriod(CirculationPeriod period, int from, int till) {
		assertThat(period.from()).isPresent();
		assertThat(period.from().get().getValue()).isEqualTo(from);
		assertThat(period.till()).isPresent();
		assertThat(period.till().get().getValue()).isEqualTo(till);
	}
}
