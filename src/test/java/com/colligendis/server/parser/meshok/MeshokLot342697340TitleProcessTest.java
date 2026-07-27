package com.colligendis.server.parser.meshok;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.neo4j.driver.Values;
import org.neo4j.driver.types.Node;

import com.colligendis.server.database.ColligendisUser;
import com.colligendis.server.database.common.model.Year;
import com.colligendis.server.database.meshok.MeshokLot;
import com.colligendis.server.database.meshok.MeshokLotService;
import com.colligendis.server.database.numista.model.Country;
import com.colligendis.server.database.numista.model.Currency;
import com.colligendis.server.database.numista.model.Denomination;
import com.colligendis.server.database.numista.model.Issuer;
import com.colligendis.server.database.result.ExecutionResult;
import com.colligendis.server.database.result.FindExecutionStatus;
import com.colligendis.server.database.result.UpdateExecutionStatus;
import com.colligendis.server.dto.MeshokLotTitleProcessResponse;
import com.colligendis.server.logger.BaseLogger;
import com.colligendis.server.search.coin.CoinQueryExtractor;
import com.colligendis.server.search.coin.CoinSearchProperties;
import com.colligendis.server.search.coin.ExtractedQuery;
import com.colligendis.server.search.coin.SemanticNodeFinder;
import com.colligendis.server.search.coin.SemanticNodeFinder.ScoredMatch;
import com.colligendis.server.util.ollama.OllamaClient;

import reactor.core.publisher.Mono;

/**
 * Regression: Meshok lot {@code 342697340}
 * ({@code 1 апсар 2025 года. Древний город Диоскуриады. Республика Абхазия ММД})
 * must persist {@code MATCHES} to Issuer, Country, Denomination, Currency, and
 * Gregorian Year after title extract.
 */
@ExtendWith(MockitoExtension.class)
class MeshokLot342697340TitleProcessTest {

	private static final String LOT_ID = "342697340";
	private static final String LOT_UUID = "c5c38f01-d1de-4fc2-8b7f-63c71eda4c9c";
	private static final String TITLE =
			"1 апсар 2025 года. Древний город Диоскуриады. Республика Абхазия ММД";
	private static final String COUNTRY_TEXT = "Республика Абхазия";

	private static final String ISSUER_UUID = "a9018559-4c9e-4648-a0e2-76b5124812c8";
	private static final String COUNTRY_UUID = "92130537-7cc8-4009-8d84-6de8a11850f4";
	private static final String DENOMINATION_UUID = "ace78619-4ca3-4b31-aeed-7cab48e1ef2d";
	private static final String CURRENCY_UUID = "6ae21370-911b-4e8d-8fdf-45bddf9ef2b9";
	private static final String YEAR_UUID = "year-2025-uuid";

	@Mock
	private MeshokLotService meshokLotService;
	@Mock
	private CoinQueryExtractor coinQueryExtractor;
	@Mock
	private OllamaClient ollamaClient;
	@Mock
	private SemanticNodeFinder semanticNodeFinder;
	@Mock
	private MeshokLotTitleProcessPersistence persistence;

	private CoinSearchProperties coinSearchProperties;
	private MeshokLotTitleProcessService service;
	private ColligendisUser user;

	@BeforeEach
	void setUp() {
		coinSearchProperties = new CoinSearchProperties();
		coinSearchProperties.setEmbeddingModel("qwen3-embedding:0.6b");
		service = new MeshokLotTitleProcessService(
				meshokLotService,
				coinQueryExtractor,
				ollamaClient,
				coinSearchProperties,
				semanticNodeFinder,
				persistence);
		user = new ColligendisUser();
		user.setUuid("user-1");
		user.setUsername("user1@gmail.com");
	}

	@Test
	@DisplayName("Lot 342697340 should persist MATCHES to Issuer/Country/Denomination/Currency/Year")
	void processLot_persistsFiveCatalogMatches() {
		MeshokLot lot = lotNode();
		stubFindAndUpdate(lot);
		when(coinQueryExtractor.extract(TITLE)).thenReturn(new ExtractedQuery(
				COUNTRY_TEXT,
				"1 апсар",
				"апсар",
				2025,
				"1 апсар. Древний город Диоскуриады. ММД"));
		when(ollamaClient.getEmbedding(anyString(), anyString())).thenReturn(new float[] { 0.1f, 0.2f });
		when(persistence.clearExtractMatches(LOT_UUID)).thenReturn(Mono.empty());
		when(persistence.persistMatch(anyString(), anyString(), anyString(), anyDouble(), anyString(), anyString(),
				anyString()))
				.thenReturn(Mono.just(true));

		ScoredMatch issuer = scoredNode(ISSUER_UUID, "abkhazia", "Abkhazia", 0.938);
		ScoredMatch country = scoredNode(COUNTRY_UUID, "abkhazia", "Abkhazia", 0.938);
		ScoredMatch denomination = scoredNode(DENOMINATION_UUID, "23307", "1 Apsar", 0.964);
		ScoredMatch currency = scoredNode(CURRENCY_UUID, "3267", "Apsar", 0.921);
		ScoredMatch year = scoredYearNode(YEAR_UUID, 2025, 1.0);
		when(semanticNodeFinder.findIssuerMatch(COUNTRY_TEXT)).thenReturn(Optional.of(issuer));
		when(semanticNodeFinder.findCountryMatch(COUNTRY_TEXT)).thenReturn(Optional.of(country));
		when(semanticNodeFinder.findDenominationMatch("1 апсар")).thenReturn(Optional.of(denomination));
		when(semanticNodeFinder.findCurrencyMatch("апсар")).thenReturn(Optional.of(currency));
		when(semanticNodeFinder.findYearMatch(2025)).thenReturn(Optional.of(year));

		MeshokLotTitleProcessResponse response = service.processByLotId(LOT_ID, user).block();

		assertThat(response).isNotNull();
		assertThat(response.lotId()).isEqualTo(LOT_ID);
		assertThat(response.title()).isEqualTo(TITLE);
		assertThat(response.extractedQueryJson())
				.contains(COUNTRY_TEXT, "1 апсар", "апсар", "2025");
		assertThat(response.links()).hasSize(5);
		assertThat(response.links())
				.extracting(MeshokLotTitleProcessResponse.LinkedEntityResponse::entityType)
				.containsExactlyInAnyOrder("ISSUER", "COUNTRY", "DENOMINATION", "CURRENCY", "YEAR");

		ArgumentCaptor<String> targetUuid = ArgumentCaptor.forClass(String.class);
		ArgumentCaptor<String> targetLabel = ArgumentCaptor.forClass(String.class);
		verify(persistence, times(5)).persistMatch(
				eq(LOT_UUID),
				targetUuid.capture(),
				targetLabel.capture(),
				anyDouble(),
				anyString(),
				anyString(),
				anyString());

		assertThat(targetLabel.getAllValues())
				.containsExactlyInAnyOrder(
						Issuer.LABEL, Country.LABEL, Denomination.LABEL, Currency.LABEL, Year.LABEL);
		assertThat(Set.copyOf(targetUuid.getAllValues()))
				.containsExactlyInAnyOrder(
						ISSUER_UUID, COUNTRY_UUID, DENOMINATION_UUID, CURRENCY_UUID, YEAR_UUID);

		verify(meshokLotService).update(any(MeshokLot.class), any(), any(BaseLogger.class));
		verify(persistence).clearExtractMatches(LOT_UUID);
		verify(ollamaClient, atLeastOnce()).getEmbedding(eq("qwen3-embedding:0.6b"), eq(COUNTRY_TEXT));
		verify(ollamaClient, atLeastOnce()).getEmbedding(eq("qwen3-embedding:0.6b"), eq("1 апсар"));
		verify(ollamaClient, atLeastOnce()).getEmbedding(eq("qwen3-embedding:0.6b"), eq("апсар"));
		verify(ollamaClient, atLeastOnce()).getEmbedding(eq("qwen3-embedding:0.6b"), eq("2025"));
	}

	@Test
	@DisplayName("Lot 342697340 should not report links when MATCHES persist fails")
	void processLot_excludesFailedPersistsFromResponse() {
		MeshokLot lot = lotNode();
		stubFindAndUpdate(lot);
		when(coinQueryExtractor.extract(TITLE)).thenReturn(new ExtractedQuery(
				COUNTRY_TEXT,
				"1 апсар",
				"апсар",
				2025,
				"1 апсар. Древний город Диоскуриады. ММД"));
		when(ollamaClient.getEmbedding(anyString(), anyString())).thenReturn(new float[] { 0.1f });
		when(persistence.clearExtractMatches(LOT_UUID)).thenReturn(Mono.empty());
		when(persistence.persistMatch(anyString(), anyString(), anyString(), anyDouble(), anyString(), anyString(),
				anyString()))
				.thenReturn(Mono.just(false));

		ScoredMatch issuer = scoredNode(ISSUER_UUID, "abkhazia", "Abkhazia", 0.938);
		ScoredMatch country = scoredNode(COUNTRY_UUID, "abkhazia", "Abkhazia", 0.938);
		ScoredMatch denomination = scoredNode(DENOMINATION_UUID, "23307", "1 Apsar", 0.964);
		ScoredMatch currency = scoredNode(CURRENCY_UUID, "3267", "Apsar", 0.921);
		ScoredMatch year = scoredYearNode(YEAR_UUID, 2025, 1.0);
		when(semanticNodeFinder.findIssuerMatch(COUNTRY_TEXT)).thenReturn(Optional.of(issuer));
		when(semanticNodeFinder.findCountryMatch(COUNTRY_TEXT)).thenReturn(Optional.of(country));
		when(semanticNodeFinder.findDenominationMatch("1 апсар")).thenReturn(Optional.of(denomination));
		when(semanticNodeFinder.findCurrencyMatch("апсар")).thenReturn(Optional.of(currency));
		when(semanticNodeFinder.findYearMatch(2025)).thenReturn(Optional.of(year));

		MeshokLotTitleProcessResponse response = service.processByLotId(LOT_ID, user).block();

		assertThat(response).isNotNull();
		assertThat(response.links())
				.as("failed Neo4j writes must not be reported as created relationships")
				.isEmpty();
		verify(persistence, times(5)).persistMatch(
				eq(LOT_UUID), anyString(), anyString(), anyDouble(), anyString(), anyString(), anyString());
	}

	private void stubFindAndUpdate(MeshokLot lot) {
		when(meshokLotService.findByLotId(eq(LOT_ID), any(BaseLogger.class)))
				.thenReturn(Mono.just(ExecutionResult.<MeshokLot, FindExecutionStatus>builder()
						.node(lot)
						.status(FindExecutionStatus.FOUND)
						.build()));
		when(meshokLotService.update(any(MeshokLot.class), any(), any(BaseLogger.class)))
				.thenAnswer(invocation -> {
					MeshokLot payload = invocation.getArgument(0);
					assertThat(payload.getExtractedQueryJson()).contains(COUNTRY_TEXT);
					MeshokLot saved = lotNode();
					saved.setExtractedQueryJson(payload.getExtractedQueryJson());
					return Mono.just(ExecutionResult.<MeshokLot, UpdateExecutionStatus>builder()
							.node(saved)
							.status(UpdateExecutionStatus.WAS_UPDATED)
							.build());
				});
	}

	private static MeshokLot lotNode() {
		MeshokLot lot = new MeshokLot();
		lot.setUuid(LOT_UUID);
		lot.setLotId(LOT_ID);
		lot.setTitle(TITLE);
		return lot;
	}

	private static ScoredMatch scoredNode(String uuid, String code, String name, double score) {
		Node node = mock(Node.class);
		when(node.containsKey(anyString())).thenAnswer(invocation -> {
			String key = invocation.getArgument(0);
			return "uuid".equals(key) || "numistaCode".equals(key) || "name".equals(key)
					|| "code".equals(key) || "nid".equals(key);
		});
		when(node.get(anyString())).thenAnswer(invocation -> {
			String key = invocation.getArgument(0);
			return switch (key) {
				case "uuid" -> Values.value(uuid);
				case "numistaCode", "code", "nid" -> Values.value(code);
				case "name" -> Values.value(name);
				default -> Values.NULL;
			};
		});
		return new ScoredMatch(node, score);
	}

	private static ScoredMatch scoredYearNode(String uuid, int dateYear, double score) {
		Node node = mock(Node.class);
		when(node.containsKey(anyString())).thenAnswer(invocation -> {
			String key = invocation.getArgument(0);
			return "uuid".equals(key) || "dateYear".equals(key);
		});
		when(node.get(anyString())).thenAnswer(invocation -> {
			String key = invocation.getArgument(0);
			return switch (key) {
				case "uuid" -> Values.value(uuid);
				case "dateYear" -> Values.value(dateYear);
				default -> Values.NULL;
			};
		});
		return new ScoredMatch(node, score);
	}
}
