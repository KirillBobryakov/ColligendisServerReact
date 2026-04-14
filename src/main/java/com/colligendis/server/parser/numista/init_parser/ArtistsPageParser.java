package com.colligendis.server.parser.numista.init_parser;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashSet;
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
import com.colligendis.server.database.numista.model.Artist;
import com.colligendis.server.database.numista.service.ArtistService;
import com.colligendis.server.logger.BaseLogger;
import com.colligendis.server.parser.numista.NumistaParseUtils;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Slf4j
@Component
@RequiredArgsConstructor
public class ArtistsPageParser {

	public static final String ARTISTS_LOCAL_DIR = "/Users/kirillbobryakov/ColligendisServerReact/numista/ARTISTS";
	public static final String ARTISTS_URL = "https://en.numista.com/catalogue/artists.php";
	public static final String ARTISTS_LOCAL_FILE = "/Users/kirillbobryakov/ColligendisServerReact/numista/ARTISTS/artists.html";

	private static final Pattern ARTIST_ID_IN_QUERY = Pattern.compile("[?&]id=(\\d+)");

	private final ArtistService artistService;
	private final ColligendisUserService colligendisUserService;

	private final BaseLogger artistsPageParserLogger = new BaseLogger();

	public void parseAllArtistsAndSave(boolean fromLocalFile) {
		List<Artist> artists = new ArrayList<>();
		Set<String> seenNids = new LinkedHashSet<>();

		if (fromLocalFile) {
			try {
				File file = new File(ARTISTS_LOCAL_FILE);
				Document document = Jsoup.parse(file, "UTF-8", ARTISTS_URL);
				extractArtists(document, artists, seenNids);
			} catch (Exception e) {
				log.error("Failed to load local artists HTML", e);
				return;
			}
		} else {
			String nextUrl = ARTISTS_URL;
			Set<String> visitedListUrls = new HashSet<>();
			boolean firstPage = true;
			while (nextUrl != null && visitedListUrls.add(nextUrl)) {
				Document document = NumistaParseUtils.loadPageByURL(nextUrl);
				if (document == null) {
					System.err.printf("Failed to load artists list: %s%n", nextUrl);
					break;
				}
				if (firstPage) {
					firstPage = false;
					try {
						Files.createDirectories(Paths.get(ARTISTS_LOCAL_DIR));
						Files.writeString(Paths.get(ARTISTS_LOCAL_FILE), document.outerHtml());
					} catch (Exception e) {
						log.warn("Could not cache artists HTML", e);
					}
				}
				extractArtists(document, artists, seenNids);
				nextUrl = findNextArtistsListPageUrl(document);
			}
		}

		final int totalArtists = artists.size();
		final java.util.concurrent.atomic.AtomicInteger savedCount = new java.util.concurrent.atomic.AtomicInteger(0);

		Flux.fromIterable(artists)
				.flatMap(artist -> artistService
						.create(artist, colligendisUserService.getNumistaParserUserMono(), artistsPageParserLogger)
						.doOnSuccess(a -> {
							int current = savedCount.incrementAndGet();
							System.out.printf("Saved artist %d/%d: %s (nid=%s)%n", current, totalArtists,
									artist.getName(), artist.getNid());
						})
						.doOnError(error -> System.err.printf("Error saving artist %s: %s%n", artist.getName(),
								error.getMessage()))
						.onErrorResume(error -> Mono.empty()),
						10)
				.doOnComplete(() -> System.out.println("All artists have been saved."))
				.doOnError(error -> System.err.println("Fatal error processing artists: " + error.getMessage()))
				.subscribe();
	}

	private static void extractArtists(Document document, List<Artist> out, Set<String> seenNids) {
		for (Element link : document.select("a[href*='artist.php?id=']")) {
			String href = link.absUrl("href");
			if (!href.contains("artist.php")) {
				continue;
			}
			Matcher m = ARTIST_ID_IN_QUERY.matcher(href);
			if (!m.find()) {
				continue;
			}
			String nid = m.group(1);
			String name = link.text().trim();
			if (name.isEmpty() || !seenNids.add(nid)) {
				continue;
			}
			Artist artist = new Artist();
			artist.setNid(nid);
			artist.setName(name);
			out.add(artist);
		}
	}

	private static String findNextArtistsListPageUrl(Document document) {
		Element relNext = document.selectFirst("a[rel=next]");
		if (relNext != null) {
			String href = relNext.absUrl("href");
			if (href.contains("artists.php")) {
				return href;
			}
		}
		for (Element a : document.select("a[href*='artists.php']")) {
			String abs = a.absUrl("href");
			if (!abs.contains("artists.php")) {
				continue;
			}
			String text = a.text().trim();
			if (text.equals("»") || text.equals("›") || text.equals(">") || text.equalsIgnoreCase("next")
					|| text.contains("Suivant") || text.contains("Weiter")) {
				return abs;
			}
		}
		return null;
	}
}
