package com.colligendis.server.parser.numista.init_parser;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.springframework.stereotype.Component;

import com.colligendis.server.database.ColligendisUserService;
import com.colligendis.server.database.numista.model.techdata.LetteringScript;
import com.colligendis.server.database.numista.service.techdata.LetteringScriptService;
import com.colligendis.server.logger.BaseLogger;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Slf4j
@Component
@RequiredArgsConstructor
public class LetteringScriptInit {

	/**
	 * Option list from Numista search / filters (lettering script field), as
	 * returned
	 * in HTML.
	 */
	private static final String NUMISTA_LETTERING_SCRIPT_OPTIONS = """
			<select>
			<option value="1">'Phags-pa</option>
			<option value="2">Ancient South Arabian</option>
			<option value="3">Arabic</option>
			<option value="4">Arabic (kufic)</option>
			<option value="93">Arabic (Maghribi)</option>
			<option value="95">Arabic (naskh)</option>
			<option value="92">Arabic (ruqʿah)</option>
			<option value="110">Arabic (thuluth)</option>
			<option value="94">Arabic (tughra)</option>
			<option value="5">Aramaic</option>
			<option value="6">Armenian</option>
			<option value="96">Aurebesh</option>
			<option value="105">Autobot</option>
			<option value="70">Baybayin</option>
			<option value="7">Bengali</option>
			<option value="9">Brahmi</option>
			<option value="10">Braille</option>
			<option value="8">Burmese</option>
			<option value="114">Carian</option>
			<option value="73">Cherokee</option>
			<option value="11">Chinese</option>
			<option value="19">Chinese (simplified)</option>
			<option value="12">Chinese (traditional, clerical script)</option>
			<option value="13">Chinese (traditional, Dai script)</option>
			<option value="14">Chinese (traditional, grass script)</option>
			<option value="15">Chinese (traditional, regular script)</option>
			<option value="16">Chinese (traditional, running script)</option>
			<option value="17">Chinese (traditional, seal script)</option>
			<option value="18">Chinese (traditional, slender gold script)</option>
			<option value="101">Cuneiform</option>
			<option value="20">Cypriot</option>
			<option value="21">Cyrillic</option>
			<option value="22">Cyrillic (cursive)</option>
			<option value="112">Cyrillic (medieval)</option>
			<option value="99">Demotic</option>
			<option value="103">Deseret</option>
			<option value="23">Devanagari</option>
			<option value="102">Etruscan</option>
			<option value="25">Ge'ez</option>
			<option value="85">Georgian (Asomtavruli)</option>
			<option value="26">Georgian (Mkhedruli)</option>
			<option value="86">Georgian (Nuskhuri)</option>
			<option value="27">Glagolitic</option>
			<option value="28">Greek</option>
			<option value="91">Greek (retrograde)</option>
			<option value="29">Gujarati</option>
			<option value="30">Gurmukhi</option>
			<option value="31">Hangul</option>
			<option value="32">Hebrew</option>
			<option value="34">Hieroglyphic</option>
			<option value="67">Hiragana</option>
			<option value="113">Hiragana (hentaigana)</option>
			<option value="81">Iberian</option>
			<option value="77">Iberian (Celtiberian)</option>
			<option value="78">Iberian (Levantine)</option>
			<option value="79">Iberian (Meridional)</option>
			<option value="80">Iberian (South-Lusitanian)</option>
			<option value="50">Inscriptional Pahlavi</option>
			<option value="68">Inuktitut</option>
			<option value="35">Javanese</option>
			<option value="111">Jawi</option>
			<option value="36">Kannada</option>
			<option value="65">Katakana</option>
			<option value="106">Kawi</option>
			<option value="37">Kharosthi</option>
			<option value="76">Khitan large script</option>
			<option value="75">Khitan small script</option>
			<option value="38">Khmer</option>
			<option value="39">Lao</option>
			<option value="40" selected="selected" data-select2-id="8">Latin</option>
			<option value="43">Latin (cursive)</option>
			<option value="89">Latin (Fraktur blackletter)</option>
			<option value="24">Latin (Gaelic)</option>
			<option value="90">Latin (retrograde)</option>
			<option value="41">Latin (uncial)</option>
			<option value="107">Lontara</option>
			<option value="64">Lycian</option>
			<option value="44">Malayalam</option>
			<option value="108">Maya</option>
			<option value="62">Mongolian (folded)</option>
			<option value="45">Mongolian / Manchu</option>
			<option value="100">Morse</option>
			<option value="82">Nabataean</option>
			<option value="46">Neo-Punic</option>
			<option value="63">Odia</option>
			<option value="47">Old Italics</option>
			<option value="48">Old Turkic</option>
			<option value="49">Old Uyghur</option>
			<option value="109">Osage</option>
			<option value="71">Persian</option>
			<option value="88">Persian (nastaliq)</option>
			<option value="51">Phoenician</option>
			<option value="87">Psalter Pahlavi</option>
			<option value="52">Ranjana</option>
			<option value="98">Rongorongo</option>
			<option value="53">Runic</option>
			<option value="84">Cirth</option>
			<option value="83">Elder Futhark</option>
			<option value="104">Sharada</option>
			<option value="54">Sinhala</option>
			<option value="55">Sogdian</option>
			<option value="56">Syriac</option>
			<option value="57">Tamil</option>
			<option value="58">Tangut</option>
			<option value="59">Telugu</option>
			<option value="74">Tengwar</option>
			<option value="66">Thaana</option>
			<option value="60">Thai</option>
			<option value="61">Tibetan</option>
			<option value="97">Tifinagh</option>
			<option value="72">Urdu</option>
			</select>
			""";

	private final LetteringScriptService letteringScriptService;

	private final ColligendisUserService colligendisUserService;

	private final BaseLogger letteringScriptInitLogger = new BaseLogger();

	/**
	 * Parses embedded Numista option HTML and persists each {@link LetteringScript}
	 * (nid = option value, name = option text).
	 */
	public void saveAllFromEmbeddedOptions() {
		List<LetteringScript> scripts = parseScripts(NUMISTA_LETTERING_SCRIPT_OPTIONS);
		final int total = scripts.size();
		var savedCount = new java.util.concurrent.atomic.AtomicInteger(0);

		Flux.fromIterable(scripts)
				.flatMap(
						script -> letteringScriptService.create(script,
								colligendisUserService.getNumistaParserUserMono(), letteringScriptInitLogger)
								.doOnSuccess(r -> {
									int current = savedCount.incrementAndGet();
									System.out.printf("Saved lettering script %d/%d: %s (nid=%s)%n",
											current, total, script.getName(), script.getNid());
								})
								.doOnError(err -> System.err.printf("Error saving lettering script %s: %s%n",
										script.getName(), err.getMessage()))
								.onErrorResume(err -> Mono.empty()),
						10)
				.doOnComplete(() -> log.info("LetteringScript init finished ({} scripts).", total))
				.doOnError(err -> log.error("LetteringScript init failed", err))
				.subscribe();
	}

	static List<LetteringScript> parseScripts(String htmlWrappedSelect) {
		Document document = Jsoup.parse(htmlWrappedSelect);
		List<LetteringScript> out = new ArrayList<>();
		Set<String> seenNids = new LinkedHashSet<>();
		for (Element option : document.select("option")) {
			String nid = option.attr("value").trim();
			String name = option.text().trim();
			if (nid.isEmpty() || name.isEmpty() || !seenNids.add(nid)) {
				continue;
			}
			LetteringScript script = new LetteringScript();
			script.setNid(nid);
			script.setName(name);
			out.add(script);
		}
		return out;
	}
}
