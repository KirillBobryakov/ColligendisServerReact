package com.colligendis.server.database.numista.service;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.colligendis.server.database.numista.model.RulingAuthority;
import com.colligendis.server.database.result.ExecutionResult;
import com.colligendis.server.database.result.FindExecutionStatus;
import com.colligendis.server.logger.BaseLogger;

import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

class RulerServiceTest {

	private static final BaseLogger LOGGER = new BaseLogger();

	private CapturingRulingAuthorityService rulingAuthorityService;

	@BeforeEach
	void setUp() {
		rulingAuthorityService = new CapturingRulingAuthorityService();
	}

	@Test
	void findByNid_returnsRuler_whenNodeExists() {
		RulingAuthority expected = new RulingAuthority("11", "Test ruling authority");
		rulingAuthorityService.setCanned(Mono.just(expected));

		StepVerifier.create(rulingAuthorityService.findByNid("11", LOGGER).map(ExecutionResult::getNode))
				.expectNext(expected)
				.verifyComplete();
	}

	@Test
	void findByNid_returnsEmpty_whenNotFound() {
		rulingAuthorityService.setCanned(Mono.empty());

		StepVerifier.create(rulingAuthorityService.findByNid("11", LOGGER))
				.verifyComplete();
	}

	@Test
	void findByNid_passesStrippedNid() {
		rulingAuthorityService.setCanned(Mono.just(new RulingAuthority("42", "ignored")));

		rulingAuthorityService.findByNid("  42  ", LOGGER).block();

		assertThat(rulingAuthorityService.lastNidPassed).isEqualTo("42");
	}

	/**
	 * Stubs {@link RulingAuthorityService#findByNid(String, BaseLogger)} without
	 * Neo4j.
	 */
	private static final class CapturingRulingAuthorityService extends RulingAuthorityService {

		private Mono<ExecutionResult<RulingAuthority, FindExecutionStatus>> canned = Mono.empty();

		String lastNidPassed;

		void setCanned(Mono<RulingAuthority> rulerMono) {
			this.canned = rulerMono == null ? Mono.empty()
					: rulerMono.map(r -> ExecutionResult.<RulingAuthority, FindExecutionStatus>builder()
							.node(r)
							.status(FindExecutionStatus.FOUND)
							.build());
		}

		@Override
		public Mono<ExecutionResult<RulingAuthority, FindExecutionStatus>> findByNid(String nid,
				BaseLogger baseLogger) {
			if (nid == null) {
				return Mono.empty();
			}
			nid = nid.strip();
			if (nid.isEmpty()) {
				return Mono.empty();
			}
			lastNidPassed = nid;
			return canned;
		}
	}
}
