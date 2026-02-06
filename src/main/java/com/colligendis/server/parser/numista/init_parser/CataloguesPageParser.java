package com.colligendis.server.parser.numista.init_parser;

import java.util.ArrayList;
import java.util.List;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import com.colligendis.server.database.N4JUtil;
import com.colligendis.server.database.exception.NotFoundError;
import com.colligendis.server.database.numista.model.Author;
import com.colligendis.server.database.numista.model.Catalogue;
import com.colligendis.server.database.numista.service.AuthorService;
import com.colligendis.server.database.numista.service.CatalogueService;
import com.colligendis.server.parser.numista.NumistaParseUtils;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class CataloguesPageParser {
	public static final String CATALOGUES_LOCAL_DIR = "/Users/kirillbobryakov/ColligendisServerReact/numista/CATALOGUES";
	public static final String AUTHORS_LOCAL_DIR = "/Users/kirillbobryakov/ColligendisServerReact/numista/AUTHORS";

	public static final String CATALOGUES_URL = "https://en.numista.com/literature/catalogues.php";
	public static final String CATALOGUES_LOCAL_FILE = "/Users/kirillbobryakov/ColligendisServerReact/numista/CATALOGUES/catalogues.html";

	public static final String AUTHORS_URL = "https://en.numista.com/literature/authors.php";
	public static final String AUTHORS_LOCAL_FILE = "/Users/kirillbobryakov/ColligendisServerReact/numista/AUTHORS/authors.html";

	private final CatalogueService catalogueService;

	private final AuthorService authorService;

	public CataloguesPageParser() {
		this.catalogueService = N4JUtil.getInstance().numistaServices.catalogueService;
		this.authorService = N4JUtil.getInstance().numistaServices.authorService;
	}

	public void parseAllCataloguesAndSave(boolean fromLocalFile) {
		Document document = null;
		if (fromLocalFile) {
			try {
				java.io.File file = new java.io.File(CATALOGUES_LOCAL_FILE);
				document = Jsoup.parse(file, "UTF-8");
			} catch (Exception e) {
				e.printStackTrace();
				return;
			}
		} else {

			document = NumistaParseUtils.loadPageByURL(CATALOGUES_URL);

			try {

				java.nio.file.Files.createDirectories(
						java.nio.file.Paths.get(CATALOGUES_LOCAL_DIR));
				java.nio.file.Files.writeString(
						java.nio.file.Paths
								.get(CATALOGUES_LOCAL_FILE),
						document.outerHtml());
			} catch (Exception e) {
				e.printStackTrace();
			}
		}

		Element catalogues_list = document.selectFirst("dl#catalogues_list");
		Elements catalogues_list_items = catalogues_list.select("dt").select("a");
		List<Catalogue> catalogues = new ArrayList<>();
		for (Element catalogue_item : catalogues_list_items) {
			String number = catalogue_item.attr("href").replace("https://en.numista.com/", "");
			String code = catalogue_item.text();
			if (number.isEmpty() || code.isEmpty()) {
				System.out.println("Skipping catalogue: " + catalogue_item.text());
				continue;
			}
			Catalogue catalogue = new Catalogue(code, number);
			catalogues.add(catalogue);
		}

		final int totalCatalogues = catalogues.size();
		final java.util.concurrent.atomic.AtomicInteger savedCount = new java.util.concurrent.atomic.AtomicInteger(0);

		Flux.fromIterable(catalogues)
				.flatMap(catalogue -> catalogueService.save(catalogue, null)
						.doOnSuccess(c -> {
							int current = savedCount.incrementAndGet();
							System.out.printf("Saved catalogue %d/%d: %s%n", current, totalCatalogues,
									catalogue.getCode());
						})
						.doOnError(error -> {
							System.err.printf("Error saving catalogue %s: %s%n", catalogue.getCode(),
									error.getMessage());
						})
						.onErrorResume(error -> Mono.empty()), // Continue processing on error
						10) // Limit concurrent operations to 10
				.doOnComplete(() -> System.out.println("All catalogues have been saved."))
				.doOnError(error -> System.err.println("Fatal error processing catalogues: " + error.getMessage()))
				.subscribe();

	}

	public void parseAllAuthorsAndSave(boolean fromLocalFile) {
		Document document = null;
		if (fromLocalFile) {
			try {
				java.io.File file = new java.io.File(AUTHORS_LOCAL_FILE);
				document = Jsoup.parse(file, "UTF-8");
			} catch (Exception e) {
				e.printStackTrace();
				return;
			}
		} else {
			document = NumistaParseUtils.loadPageByURL(AUTHORS_URL);
			try {

				java.nio.file.Files.createDirectories(
						java.nio.file.Paths.get(AUTHORS_LOCAL_DIR));
				java.nio.file.Files.writeString(
						java.nio.file.Paths
								.get(AUTHORS_LOCAL_FILE),
						document.outerHtml());
			} catch (Exception e) {
				e.printStackTrace();
			}
		}
		Element authors_list = document.selectFirst("ul#author_list");
		Elements authors_list_items = authors_list.select("li");
		List<Author> authors = new ArrayList<>();
		for (Element author_item : authors_list_items) {
			Element author_link = author_item.selectFirst("a");
			Author author = new Author();
			author.setName(author_link.text());
			author.setCode(author_link.attr("href").replace("../people/", ""));
			authors.add(author);
		}

		final int totalAuthors = authors.size();
		final java.util.concurrent.atomic.AtomicInteger savedCount = new java.util.concurrent.atomic.AtomicInteger(0);

		// Use controlled concurrency to prevent connection pool exhaustion
		Flux.fromIterable(authors)
				.flatMap(author -> authorService.save(author, null)
						.doOnSuccess(a -> {
							int current = savedCount.incrementAndGet();
							System.out.printf("Saved author %d/%d: %s%n", current, totalAuthors, author.getName());
						})
						.doOnError(error -> {
							System.err.printf("Error saving author %s: %s%n", author.getName(), error.getMessage());
						})
						.onErrorResume(error -> Mono.empty()), // Continue processing on error
						10) // Limit concurrent operations to 10
				.doOnComplete(() -> System.out.println("All authors have been saved."))
				.doOnError(error -> System.err.println("Fatal error processing authors: " + error.getMessage()))
				.subscribe();

	}

	public void parseCatalogue(String code) {

		catalogueService.findByCode(code)
				.flatMap(either -> either.fold(
						error -> {
							if (error instanceof NotFoundError) {
								System.out.println("Catalogue not found: " + code);
								return Mono.empty();
							}
							return Mono.empty();
						},
						catalogue -> Mono.fromCallable(() -> {
							System.out
									.println("Parsing catalogue: " + catalogue.getCode() + " " + catalogue.getNumber());
							Document document = NumistaParseUtils
									.loadPageByURL("https://en.numista.com/" + catalogue.getNumber());

							Element main_content = document.selectFirst("main#main");

							String title = main_content.selectFirst("#main_title h1").text();
							catalogue.setTitle(title);

							Element reference_catalogue = main_content.selectFirst("table.reference_catalogue");
							Elements reference_catalogue_rows = reference_catalogue.selectFirst("tbody").select("tr");
							for (Element reference_catalogue_row : reference_catalogue_rows) {
								Element cell_header = reference_catalogue_row.selectFirst("th");
								Element cell_value = reference_catalogue_row.selectFirst("td");
								if (cell_header.text().equals("Title translation")) {
									catalogue.setTitle_en(cell_value.text());
								} else if (cell_header.text().equals("Author")) {
									Elements author_links = cell_value.select("a");

									List<String> authorCodes = author_links.stream()
											.map(t -> t.attr("href").replace("../people/", ""))
											.toList();

									Flux.fromIterable(authorCodes).flatMap(
											authorCode -> authorService.findByCode(authorCode)
													.flatMap(foundAuthor -> foundAuthor.fold(
															error -> {
																if (error instanceof NotFoundError) {
																	log.error("Author not found: " + foundAuthor);
																	return Mono.empty();
																}
																return Mono.empty();
															},
															author -> {
																return Mono.just(author);
															})))

											.subscribe();

								}
							}

							System.out.println("");

							return true;
						})))
				.subscribe();

	}
}
