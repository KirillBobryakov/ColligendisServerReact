package com.colligendis.server.parser.numista;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.function.Function;

import java.time.Duration;

import org.jsoup.Connection;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.springframework.stereotype.Component;

import java.io.FileNotFoundException;

import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;
import reactor.util.retry.Retry;

@Component
@NoArgsConstructor
@Slf4j
public class PageLoader extends Parser {

	public static Boolean useCookies = true;

	private static final Path NTYPES_PAGES_DIR = Paths
			.get("/Users/kirillbobryakov/Coins/Numista/NTYPES_PAGES")
			.toAbsolutePath()
			.normalize();

	protected static String COOKIE = "IABGPP_HDR_GppString=DBABMA~CQhhRkAQhhRkAAKA4AENCXFsAP_gAEPgAAqIMMtR_G__bWlr-bb3abtkeYxP9_hr7sQxBgbJk24FzLPW7JwHx2E5NAzatqIKmRIAu3TBIQNlHJDURUCgKIgFryDMaE2U4TNKJ6BkiFMZA2tYCFxvm4tjWQCY4vr_5lc1mB-t7dr82dzyy6hHn3a5fmS1UJCdIYetDfv8ZBOT-9IEd-x8v4v4_EbpEm-eS1n_pGtp4jd6YnM_dBmxt-Tyff7Pn__rl_e7X_ve_n3zv8oXH77r____f_-7___2b_-___b-__4MNAAmGhUQRlkQIBAoCEECABQVhABQIAgAASBogIATBgQ5AwAXWEyAEAKAAYIAQAAgwABAAAJAAhEAEABAIAQIBAoAAwAIAgIAGBgADABYiAQAAgOgYpgQQCBYAJEZVBpgSgAJBAS2VCCUDAgrhCkWOAQQIiYKAAAEAAoAAEB8LAQklBKxIIAuILoAACAAAKIESBFIWYAgqDNFoKwJOAyNMAyfMEySnQZAEwQkZBkQmqCQeKYohQQ5AbFLMAdPEFACLtZIQ8AA.fwAAAAAAAAAA; addtl_consent=1~20.23.3.9.2.4.9.13.6.4.15.9.5.2.11.8.1.3.2.10.2.23.8.4.15.17.2.6.3.16.4.7.6.14.5.20.2.1.6.2.1.4.31.9.3.1.14.22.8.9.5.1.6.9.24.17.5.3.1.27.1.17.10.10.8.6.2.8.3.4.30.102.14.60.1.4.1.17.7.12.25.35.5.18.9.7.17.4.20.2.4.1.17.24.4.2.7.6.1.1.3.2.14.25.3.2.2.8.2.17.9.8.6.3.10.4.20.2.4.13.10.5.6.1.3.22.16.2.6.8.6.11.6.5.17.13.3.11.9.10.28.12.1.3.2.2.17.9.6.40.17.2.2.9.15.8.7.3.12.7.2.4.1.14.5.13.22.13.2.6.1.7.10.1.4.15.2.4.9.4.4.1.4.7.3.10.5.3.12.17.4.14.8.2.15.2.5.6.2.3.2.14.11.8.2.2.7.9.13.6.11.1.13.2.14.4.1.1.3.1.1.9.7.2.16.5.19.8.3.1.5.3.5.4.8.4.1.3.2.10.4.2.13.4.2.6.9.6.3.2.2.3.1.6.9.10.11.9.19.8.3.3.1.2.1.1.1.2.7.17.2.18.4.4.3.13.4.10.1.2.4.6.3.3.3.4.1.7.11.4.1.11.3.3.1.10.13.3.2.1.1.3.1.3.1.1.2.7.2.13.7.6.8.4.3.4.5.7.2.2.5.5.3.5.4.7.9.1.4.1.2.1.7.10.7.4.1.3.1.1.2.1.3.2.2.4.1.5.6.1.8.1.3.1.1.2.2.4.3.3.3.1.1.4.3.6.1.2.1.4.1.1.4.1.1.2.1.8.1.7.4.3.2.1.2.1.5.3.15.1.15.10.28.1.2.2.6.6.3.4.1.6.3.4.7.1.1.2.1.4.1.2.3.3.1.1.1.1.4.1.5.2.3.1.2.2.6.2.1.2.2.2.3.2.1.3.2.1.1.1.1.2.1.1.1.2.2.1.1.2.1.2.1.7.1.7.1.1.2.2.1.4.2.1.1.9.1.6.2.1.6.2.3.2.1.1.1.2.2.2.1.1.1.4.1.1.2.2.1.1.7.1.2.2.1.1.1.1.2.3.1.1.2.4.1.1.1.4.5.3.3.4.5.8.1.1.2.3.1.4.3.2.2.3.1.1.1.1.11.1.1.3.1.1.2.2.1.6.1.2.3.5.2.7.1.1.2.3.2.1.1.8.4.1.1.2.1.1.8.2.2.2.3.1.4.5.1.1.1.1.1.1.1.1.4.2.4.1.8.1.1.2.1.1.2.1.4.1.2.1.1.1.2.1.2.1.1.1.1.1.2.4.1.5.1.2.1.3.3.6.4.2.9.5.2.1.1.2.1.3.3.1.6.1.2.5.1.1.2.5.1.4.2.1.200.100.100.100.300.200.200.100.100.100.400.1700.200.104.596.100.1000.800.500.400.200.200.500.100.1800.201.99.303.99.104.95.1399.1100.100.4302.498.1300.2100.800.100.600.200.900.100.200.301.399.100.800.700.201.200.1799.1400.300.400.100.2100.2300.400.1101.499.400.2100.100.100.2100.1100.201.299.600.1100.101.99.1400.2000.1400.2600.100.200.100.300; euconsent-v2=CQhhRkAQhhRkAAKA4AENCXFsAP_gAEPgAAqIMMtR_G__bWlr-bb3abtkeYxP9_hr7sQxBgbJk24FzLPW7JwHx2E5NAzatqIKmRIAu3TBIQNlHJDURUCgKIgFryDMaE2U4TNKJ6BkiFMZA2tYCFxvm4tjWQCY4vr_5lc1mB-t7dr82dzyy6hHn3a5fmS1UJCdIYetDfv8ZBOT-9IEd-x8v4v4_EbpEm-eS1n_pGtp4jd6YnM_dBmxt-Tyff7Pn__rl_e7X_ve_n3zv8oXH77r____f_-7___2b_-___b-__4MNAAmGhUQRlkQIBAoCEECABQVhABQIAgAASBogIATBgQ5AwAXWEyAEAKAAYIAQAAgwABAAAJAAhEAEABAIAQIBAoAAwAIAgIAGBgADABYiAQAAgOgYpgQQCBYAJEZVBpgSgAJBAS2VCCUDAgrhCkWOAQQIiYKAAAEAAoAAEB8LAQklBKxIIAuILoAACAAAKIESBFIWYAgqDNFoKwJOAyNMAyfMEySnQZAEwQkZBkQmqCQeKYohQQ5AbFLMAdPEFACLtZIQ8AA.IMNNR_G__bXlv-bb36btkeYxf9_hr7sQxBgbJs24FzLvW7JwH32E7NEzatqYKmRIAu3TBIQNtHJjURUChKIgVrzDsaE2U4TtKJ-BkiHMZY2tYCFxvm4tjWQCZ4vr_51d9mT-t7dr-2dzy27hnv3a9fuS1UJidKYetHfv8ZBOT-_IU9_x-_4v4_MbpEm-eS1v_tWtt43d64vP_dpuxt-Tyff7____73_e7X__e__33_-qXX_77____________f_________8.fwAAAAAAAAAA; usprivacy=1---; _pk_ses.10.0242=1; hb_insticator_uid=8772cf40-1e4f-4830-a325-6b5ee8930de8; cto_bidid=PQzDP194M1NCNnBZViUyRlJEJTJGOVpRUmlCdE01Qnc3bE1aWSUyQkVza2l6QjNUVlNjSEVjU09OVHNJanBUeiUyQjQ1dG5zQ0FjMk5uQ284RjFPTjBhYlg5a1RlQnJJSXZ3JTNEJTNE; cto_bundle=Tf8TZl9VcUJ0c1hqNiUyQjQ5aUZRTlBrMlFKdUlTJTJGdEtzZTVjN3B5QSUyQk83SUFVSHdWSzhTMU9LRWlWaFgwbmhTaTdzWTJ6U28yVm0lMkZIYmNURSUyQktkemRneExaZkNIajFvOHRlMW50TzJneWYlMkYyZ25EeDJKSk5WSUNjRHRBN3VSZkxJYldEag; __eoi=ID=b5cb8c17bfeff269:T=1762711520:RT=1775216488:S=AA-AfjZeOF6g0IJhKpux_LcmJMen; __gads=ID=832995cdcb1024f1:T=1762711520:RT=1775216488:S=ALNI_MaK7izygkkOAagl3LnfgtMYv4u-Hw; __gpi=UID=000012884dc93d7f:T=1762711520:RT=1775216488:S=ALNI_MbwSQATS4iyXHYDCQJzCyCoKj4KqQ; challenge_pass=MTAzLjI0NS4yMjkuMjI1fDE3NzUzMDI4ODJ8MzUwM2QwNWI2MzA0MGRkOGI4N2NhZjczNjYwNTk5OWMzZDQ5YzM0YzY5ZTQwZGFjMmI4MmZlZjE5ZjkxYTY0YQ%3D%3D; _pk_ref.10.0242=%5B%22%22%2C%22%22%2C1775216182%2C%22https%3A%2F%2Fwww.google.com%2F%22%5D; PHPSESSID=3eh16tm15kgfm17ssm8ctal3ql; cf_hmac=243029-1775211914-nFtzkCuix0cc8E0fc58L7MP9TYCwhAolVmGDpVzgahU; _sharedID=ca6bb764-6890-4fdf-9d94-222e1e66266c; _sharedID_cst=ayxgLOcsPA%3D%3D; __mggpc__=0; _ublock=1; search_order=y; __ai_fp_uuid=f4f9ff7b862be841%3A1; __upin=6ikmALdCWVhE5CWjmElk0g; saisie_rapide=c; carte=type; search_subtypes=all; pieces_par_page=200; issuer_sort=d; cf_clearance=KNlKQpcinFgvCqybJxRu3gJ6CwQF86ExLIGFv.HIHtk-1759610496-1.2.1.1-ZIkhKIJyRywXBhOw8kg8YUklv.wmZiHx.NCsNBy.RhXJC4s_gd8XTFhE5ZJgq9j_VHIOAHAHV.P2ZcH7aCzHJ_MHMGc5x27wzP65r_ZMatDXheLQIUX6jm9sY3ruestVsloIgyXdFydDCS22iAz1lllue7W2Q_uy44vDxXavB03D_pGTzeGkhhl9eyIvL6MgVzaXtivBkTA5sqSoDiIs6GKa3lnn5lmdfne8gDyq.rc; access_token=P%3EbLPZEY%24%21t.%3F9jIWgGg%28sG%5DrBF1S%28b%21%231.x7g%21%3E; pseudo=kbobryakov; _pk_id.10.0242=a36509097c0e55fd.1751222019.";
	public static final String USER_AGENT = "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/18.3 Safari/605.1.15";

	@Override
	protected Mono<NumistaPage> parse(NumistaPage numistaPage) {
		return loadPageByURL().apply(numistaPage);
	}

	public Function<NumistaPage, Mono<NumistaPage>> loadPageByURL() {
		return numistaPage -> Mono.defer(() -> {
			numistaPage.getPipelineStepLogger()
					.infoBlue("Parsing started for item with nid: {} and url: {}", numistaPage.nid, numistaPage.url);
			return loadDocument(numistaPage);
		})
				.retryWhen(
						Retry.backoff(3, Duration.ofSeconds(1))
								.maxBackoff(Duration.ofSeconds(3))
								.filter(ex -> !(ex instanceof FileNotFoundException)) // не ретраем 404
				)
				.doOnError(e -> numistaPage.getPipelineStepLogger().error("Failed to load after retries: {} : {}",
						numistaPage.url, e))
				.onErrorResume(e -> Mono.empty())
				.map(doc -> {
					numistaPage.page = doc;
					return numistaPage;
				});
	}

	public Mono<Document> loadDocument(NumistaPage numistaPage) {
		return Mono.fromCallable(() -> {
			Path localHtml = localHtmlPath(numistaPage.nid);
			if (Files.isRegularFile(localHtml) && Files.size(localHtml) > 0) {
				numistaPage.getPipelineStepLogger().trace("Loading from local file {}", localHtml);
				return Jsoup.parse(localHtml.toFile(), StandardCharsets.UTF_8.name(), numistaPage.url);
			}

			numistaPage.getPipelineStepLogger().trace("Loading {}", numistaPage.url);

			Connection con = Jsoup.connect(numistaPage.url)
					.ignoreHttpErrors(true)
					.userAgent(USER_AGENT);

			if (useCookies) {
				con.header("Cookie", COOKIE);
			}

			Connection.Response response = con.execute();

			if (response.statusCode() == 404) {
				numistaPage.getPipelineStepLogger().warning("404 Not Found: {}", numistaPage.url);
				throw new FileNotFoundException(numistaPage.url);
			}

			String html = response.body();
			if (html != null && !html.isEmpty()) {
				Files.createDirectories(localHtml.getParent());
				Files.writeString(localHtml, html, StandardCharsets.UTF_8);
			}

			return Jsoup.parse(html != null ? html : "", numistaPage.url);

		}).subscribeOn(Schedulers.boundedElastic());

	}

	private static Path localHtmlPath(String nid) {
		Path resolved = NTYPES_PAGES_DIR.resolve(nid + ".html").normalize();
		if (!resolved.startsWith(NTYPES_PAGES_DIR)) {
			throw new IllegalArgumentException("Invalid nid for cache path: " + nid);
		}
		return resolved;
	}
}
