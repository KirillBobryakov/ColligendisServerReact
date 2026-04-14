package com.colligendis.server.parser.numista.init_parser;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.springframework.stereotype.Component;

import com.colligendis.server.database.ColligendisUserService;
import com.colligendis.server.database.numista.model.Mint;
import com.colligendis.server.database.numista.service.MintService;
import com.colligendis.server.logger.BaseLogger;
import com.colligendis.server.parser.numista.NumistaParseUtils;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Slf4j
@Component
@RequiredArgsConstructor
public class MintPageParser {

	public static final String MINTS_LOCAL_DIR = "/Users/kirillbobryakov/ColligendisServerReact/numista/MINTS";
	public static final String MINTS_URL = "https://en.numista.com/catalogue/mints.php";
	public static final String MINTS_LOCAL_FILE = "/Users/kirillbobryakov/ColligendisServerReact/numista/MINTS/mints.html";

	private static final Pattern MINT_ID_IN_QUERY = Pattern.compile("[?&]id=(\\d+)");
	private static final Pattern MAP_FLY_TO = Pattern.compile("map\\.flyTo\\(\\[([\\d.+-]+)\\s*,\\s*([\\d.+-]+)\\]");

	private final MintService mintService;
	private final ColligendisUserService colligendisUserService;

	private final BaseLogger mintPageParserLogger = new BaseLogger();

	public void parseAllMintsAndSave(boolean fromLocalFile) {
		List<Mint> mints = new ArrayList<>();
		Set<String> seenNids = new LinkedHashSet<>();

		if (fromLocalFile) {
			try {
				File file = new File(MINTS_LOCAL_FILE);
				Document document = Jsoup.parse(file, "UTF-8", MINTS_URL);
				extractMints(document, mints, seenNids);
			} catch (Exception e) {
				log.error("Failed to load local mints HTML", e);
				return;
			}
		} else {
			Document document = NumistaParseUtils.loadPageByURL(MINTS_URL);
			if (document == null) {
				System.err.printf("Failed to load mints list: %s%n", MINTS_URL);
				return;
			}
			try {
				Files.createDirectories(Paths.get(MINTS_LOCAL_DIR));
				Files.writeString(Paths.get(MINTS_LOCAL_FILE), document.outerHtml());
			} catch (Exception e) {
				log.warn("Could not cache mints HTML", e);
			}
			extractMints(document, mints, seenNids);
		}

		final int total = mints.size();
		final java.util.concurrent.atomic.AtomicInteger savedCount = new java.util.concurrent.atomic.AtomicInteger(0);

		Flux.fromIterable(mints)
				.flatMap(mint -> mintService
						.create(mint, colligendisUserService.getNumistaParserUserMono(), mintPageParserLogger)
						.doOnSuccess(a -> {
							int current = savedCount.incrementAndGet();
							System.out.printf("Saved mint %d/%d: %s (nid=%s)%n", current, total,
									mint.getName(), mint.getNid());
						})
						.doOnError(error -> System.err
								.printf("Error saving mint %s: %s%n", mint.getName(), error.getMessage()))
						.onErrorResume(error -> Mono.empty()),
						10)
				.doOnComplete(() -> System.out.println("All mints have been saved."))
				.doOnError(error -> System.err.println("Fatal error processing mints: " + error.getMessage()))
				.subscribe();
	}

	private static void extractMints(Document document, List<Mint> out, Set<String> seenNids) {
		for (Element li : document.select("#main > ul > li")) {
			Element mintLink = li.selectFirst("a[href*='mint.php?id=']");
			if (mintLink == null) {
				continue;
			}
			String href = mintLink.attr("href");
			if (href.isEmpty()) {
				href = mintLink.absUrl("href");
			}
			Matcher idMatcher = MINT_ID_IN_QUERY.matcher(href);
			if (!idMatcher.find()) {
				continue;
			}
			String nid = idMatcher.group(1);
			if (!seenNids.add(nid)) {
				continue;
			}

			Mint mint = new Mint();
			mint.setNid(nid);

			Element strong = mintLink.selectFirst("strong");
			if (strong != null) {
				String name = strong.text().trim();
				mint.setName(name);
				String full = mintLink.text().trim();
				String place = "";
				if (full.startsWith(name)) {
					place = full.substring(name.length()).replaceFirst("^,\\s*", "").trim();
				}
				mint.setPlace(place);
			} else {
				mint.setName(mintLink.text().trim());
				mint.setPlace("");
			}

			Element mapAnchor = li.selectFirst("a[onclick*=flyTo]");
			if (mapAnchor != null) {
				Matcher fly = MAP_FLY_TO.matcher(mapAnchor.attr("onclick"));
				if (fly.find()) {
					mint.setLatitude(fly.group(1));
					mint.setLongitude(fly.group(2));
				}
			}

			out.add(mint);
		}
	}
}
