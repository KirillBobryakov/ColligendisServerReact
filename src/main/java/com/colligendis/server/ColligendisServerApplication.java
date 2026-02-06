package com.colligendis.server;

import java.util.List;
import java.util.stream.Stream;

import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.EnableAspectJAutoProxy;

import com.colligendis.server.database.ColligendisUserService;
import com.colligendis.server.database.N4JUtil;
import com.colligendis.server.database.common.CommonServices;
import com.colligendis.server.database.numista.NumistaServices;
import com.colligendis.server.parser.numista.NumistaPageParser;
import com.colligendis.server.parser.numista.init_parser.CataloguesPageParser;

import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Flux;

@Slf4j
@SpringBootApplication(scanBasePackages = "com.colligendis.server")
@EnableAspectJAutoProxy
public class ColligendisServerApplication {

	public final NumistaServices numistaServices;
	public final ColligendisUserService colligendisUserService;
	public final CommonServices commonServices;

	public ColligendisServerApplication(NumistaServices numistaServices,
			CommonServices commonServices, ColligendisUserService colligendisUserService) {
		this.numistaServices = numistaServices;
		this.commonServices = commonServices;
		this.colligendisUserService = colligendisUserService;
		N4JUtil.InitInstance(numistaServices, commonServices, colligendisUserService);

		// Flux<String> nids = Flux.fromArray(new String[] { "1", "359072", "533849",
		// "202308", "354725", "372347" });
		Flux<String> nids = Flux
				.fromArray(
						new String[] { "1", "2", "3", "4", "5", "6", "7", "8", "9", "10", "336747",
								"8685", "361262" });
		NumistaPageParser.parseAll(nids);
		// CataloguesPageParser parser = new CataloguesPageParser();
		// parser.parseCatalogue("Gra");
	}

	@Bean
	CommandLineRunner initDatabase() {
		return args -> {

			// NumistaPageParser.parse.accept(Stream.of("1", "359072", "533849", "202308",
			// "354725", "372347"));

			log.info("Database initialization completed.");

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
