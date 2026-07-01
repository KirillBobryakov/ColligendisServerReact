package com.colligendis.server;

import org.neo4j.driver.Driver;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.EnableAspectJAutoProxy;

import com.colligendis.server.database.ColligendisUserService;
import com.colligendis.server.database.numista.model.CollectibleType;
import com.colligendis.server.database.numista.service.ArtistService;
import com.colligendis.server.database.numista.service.MintService;
import com.colligendis.server.database.numista.service.NTypeService;
import com.colligendis.server.database.numista.service.techdata.LetteringScriptService;
import com.colligendis.server.parser.numista.CurrencyParser;
import com.colligendis.server.parser.numista.NumistaPageParser;
import com.colligendis.server.parser.numista.catalogue.CatalogueParser;
import com.colligendis.server.parser.numista.catalogue.CatalogueParser.CatalogueParseResult;
import com.colligendis.server.util.NormalizedNeo4jPropertyUtil;

import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Flux;

@Slf4j
@SpringBootApplication(scanBasePackages = "com.colligendis.server")
@EnableAspectJAutoProxy
public class ColligendisServerApplication {

	public final NumistaPageParser numistaPageParser;
	public final NTypeService nTypeService;
	public final LetteringScriptService letteringScriptService;
	public final ColligendisUserService colligendisUserService;
	public final ArtistService artistService;
	public final MintService mintService;

	public final CurrencyParser currencyParser;

	public ColligendisServerApplication(NumistaPageParser numistaPageParser, NTypeService nTypeService,
			LetteringScriptService letteringScriptService, ColligendisUserService colligendisUserService,
			ArtistService artistService, MintService mintService, CurrencyParser currencyParser, Driver neo4jDriver) {
		this.numistaPageParser = numistaPageParser;
		this.nTypeService = nTypeService;
		this.letteringScriptService = letteringScriptService;
		this.colligendisUserService = colligendisUserService;
		this.artistService = artistService;
		this.mintService = mintService;
		this.currencyParser = currencyParser;
		this.neo4jDriver = neo4jDriver;
	}

	private final Driver neo4jDriver;

	@Value("${spring.neo4j.database:neo4j}")
	private String neo4jDatabase;

	@Bean
	CommandLineRunner initDatabase() {
		return args -> {
			log.info("Database initialization completed.");

			// Run parser after application is fully ready (ensures numistaParserUserMono is
			// initialized)
			Flux<String> nids = Flux.fromArray(new String[] { "101" });

			// Flux<String> nids = Flux.fromArray(new String[] { "41", "60", "59", "62",
			// "61", "63", "64", "68" });

			// Flux<String> nids = Flux.fromArray(new String[] { "1", "2", "3", "4", "5",
			// "6", "7", "8", "9", "10", "11",
			// "12", "13", "14", "15", "16", "17", "18", "19", "20", "21", "22", "23", "24",
			// "25", "26", "27",
			// "28", "29", "30", "31", "32", "33", "34", "35", "36", "37", "38", "39", "40",
			// "41", "42", "43",
			// "44", "45", "46", "47", "48", "49", "50", "51", "52", "53", "54", "55", "56",
			// "57", "58", "59",
			// "60", "61", "62", "63", "64", "65", "66", "67", "68", "69", "70", "71", "72",
			// "73", "74", "75",
			// "76", "77", "78", "79", "80", "81", "82", "83", "84", "85", "86", "87", "88",
			// "89", "90", "91",
			// "92", "93", "94", "95", "96", "97", "98", "99", "100", "462729" });
			// ArtistsPageParser artistsPageParser = new ArtistsPageParser(artistService,
			// colligendisUserService);
			// artistsPageParser.parseAllArtistsAndSave(true);

			// numistaPageParser.parseAll(nids);
			// CatalogueParser catalogueParser = new CatalogueParser();
			// CatalogueParseResult catalogueParseResult = catalogueParser.parse("abkhazia",
			// CollectibleType.COINS_CODE);
			// System.out.println(catalogueParseResult);
			// NormalizedNeo4jPropertyUtil.syncNormalizedProperties(
			// neo4jDriver, neo4jDatabase, 500, System.out::println);
		};
	}

	public static void main(String[] args) {
		// Clear console on application start
		System.out.print("\033[H\033[2J");
		System.out.flush();

		log.info("Starting ColligendisServerReact Application...");
		SpringApplication.run(ColligendisServerApplication.class, args);
		log.info("ColligendisServerReact Application started successfully!");
	}
}
