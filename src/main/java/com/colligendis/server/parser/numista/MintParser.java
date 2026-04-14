package com.colligendis.server.parser.numista;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import org.apache.commons.lang3.StringUtils;
import org.jsoup.nodes.Element;
import org.springframework.stereotype.Component;

import com.colligendis.server.database.numista.model.Mintmark;
import com.colligendis.server.database.numista.model.SpecifiedMint;
import com.colligendis.server.database.numista.service.MintService;
import com.colligendis.server.database.numista.service.MintmarkService;
import com.colligendis.server.database.numista.service.NTypeService;
import com.colligendis.server.database.numista.service.SpecifiedMintService;
import com.colligendis.server.database.result.CreateNodeExecutionStatus;
import com.colligendis.server.database.result.CreateRelationshipExecutionStatus;
import com.colligendis.server.database.result.ExecutionResult;
import com.colligendis.server.database.result.ExecutionStatuses;
import com.colligendis.server.database.result.FindExecutionStatus;
import com.colligendis.server.parser.numista.exception.ParserException;

import lombok.RequiredArgsConstructor;
import reactor.core.publisher.Mono;

@Component
@RequiredArgsConstructor
public class MintParser extends Parser {

	private final MintService mintService;
	private final MintmarkService mintmarkService;
	private final SpecifiedMintService specifiedMintService;
	private final NTypeService nTypeService;

	@Override
	protected Mono<NumistaPage> parse(NumistaPage numistaPage) {
		return Mono.defer(() -> {
			Element mintsFieldset = numistaPage.page.selectFirst("fieldset:contains(Mint(s))");
			if (mintsFieldset == null) {
				numistaPage.getPipelineStepLogger().info("nid: {} — Mint(s) fieldset not on page", numistaPage.nid);
				return Mono.just(numistaPage);
			}

			return collectSpecifiedMints(mintsFieldset, numistaPage, 0)
					.flatMap(specifiedMints -> {
						if (specifiedMints.isEmpty()) {
							return Mono.just(numistaPage);
						}
						return nTypeService.setSpecifiedMints(numistaPage.nType, specifiedMints,
								numistaPage.getNumistaParserUserMono(), numistaPage.getPipelineStepLogger())
								.flatMap(er -> {
									if (er.getStatus().equals(CreateRelationshipExecutionStatus.WAS_CREATED)
											|| er.getStatus()
													.equals(CreateRelationshipExecutionStatus.IS_ALREADY_EXISTS)) {
										return Mono.just(numistaPage);
									}
									return Mono.error(new ParserException(
											"Failed to link SpecifiedMint nodes to NType for nid: " + numistaPage.nid));
								});
					});
		});
	}

	/**
	 * Recursively reads {@code mint_identifierN}, {@code mintN}, {@code mintmarkN}
	 * until the block ends.
	 */
	private Mono<List<SpecifiedMint>> collectSpecifiedMints(Element mintsFieldset, NumistaPage numistaPage, int index) {
		Element rowIdInput = mintsFieldset.selectFirst("input[name=mint_identifier" + index + "]");
		Element mintSelect = mintsFieldset.selectFirst("select[name=mint" + index + "]");
		Element mintmarkSelect = mintsFieldset.selectFirst("select[name=mintmark" + index + "]");

		if (rowIdInput == null || mintSelect == null || mintmarkSelect == null
				|| mintSelect.selectFirst("option") == null) {
			return Mono.just(List.of());
		}

		HashMap<String, String> mintOption = selectedOrFirstOption(mintSelect);
		if (!isValueAndTextNonBlank(mintOption)) {
			return collectSpecifiedMints(mintsFieldset, numistaPage, index + 1);
		}

		String mintNid = mintOption.get("value");

		return mintService.findByNid(mintNid, numistaPage.getPipelineStepLogger())
				.flatMap(mintEr -> {
					if (!mintEr.getStatus().equals(FindExecutionStatus.FOUND)) {
						numistaPage.getPipelineStepLogger().error(
								"Mint not in database (nid: {}) while parsing contribution nid: {}", mintNid,
								numistaPage.nid);
						return Mono.error(new ParserException(
								"Can't find Mint with nid: " + mintNid + " for page nid: " + numistaPage.nid));
					}
					String rawRowId = NumistaParseUtils.getAttribute(rowIdInput, "value");
					final String rowIdentifier = rawRowId != null ? rawRowId : "";

					HashMap<String, String> mintmarkOption = selectedOrFirstOption(mintmarkSelect);
					Mono<ExecutionResult<SpecifiedMint, ? extends ExecutionStatuses>> specifiedMono = isValueAndTextNonBlank(
							mintmarkOption)
									? loadOrCreateMintmark(mintmarkOption, numistaPage).flatMap(
											mm -> specifiedMintService.findOrCreateWithMintLinks(rowIdentifier,
													mintEr.getNode(), mm, numistaPage.getNumistaParserUserMono(),
													numistaPage.getPipelineStepLogger()))
									: specifiedMintService.findOrCreateWithMintLinks(rowIdentifier, mintEr.getNode(),
											null,
											numistaPage.getNumistaParserUserMono(),
											numistaPage.getPipelineStepLogger());

					return specifiedMono.flatMap(smResult -> {
						if (!smResult.getStatus().equals(FindExecutionStatus.FOUND)
								&& !smResult.getStatus().equals(CreateNodeExecutionStatus.WAS_CREATED)) {
							return Mono.error(new ParserException(
									"Failed to resolve SpecifiedMint for page nid: " + numistaPage.nid));
						}
						return collectSpecifiedMints(mintsFieldset, numistaPage, index + 1).map(rest -> {
							List<SpecifiedMint> out = new ArrayList<>();
							out.add(smResult.getNode());
							out.addAll(rest);
							return out;
						});
					});
				});
	}

	private Mono<Mintmark> loadOrCreateMintmark(HashMap<String, String> mintmarkOption, NumistaPage numistaPage) {
		String mintmarkNid = mintmarkOption.get("value");
		return mintmarkService.findByNid(mintmarkNid, numistaPage.getPipelineStepLogger())
				.flatMap(mmEr -> {
					if (mmEr.getStatus().equals(FindExecutionStatus.FOUND)) {
						return Mono.just(mmEr.getNode());
					}
					if (mmEr.getStatus().equals(FindExecutionStatus.NOT_FOUND)) {
						Mintmark created = new Mintmark(mintmarkNid, mintmarkOption.get("text"));
						return mintmarkService.create(created, numistaPage.getNumistaParserUserMono(),
								numistaPage.getPipelineStepLogger())
								.flatMap(createEr -> {
									if (createEr.getStatus().equals(CreateNodeExecutionStatus.WAS_CREATED)) {
										return Mono.just(createEr.getNode());
									}
									createEr.logError(numistaPage.getPipelineStepLogger());
									return Mono.error(new ParserException(
											"Failed to create Mintmark nid: " + mintmarkNid));
								});
					}
					mmEr.logError(numistaPage.getPipelineStepLogger());
					return Mono.error(new ParserException(
							"Failed to look up Mintmark nid: " + mintmarkNid));
				});
	}

	private static HashMap<String, String> selectedOrFirstOption(Element select) {
		if (select == null) {
			return null;
		}
		Element opt = select.select("option").stream()
				.filter(o -> o.hasAttr("selected"))
				.findFirst()
				.orElse(select.selectFirst("option"));
		if (opt == null) {
			return null;
		}
		HashMap<String, String> r = new HashMap<>();
		r.put("value", opt.attr("value"));
		r.put("text", opt.text());
		return r;
	}

	private static boolean isValueAndTextNonBlank(HashMap<String, String> map) {
		return map != null && StringUtils.isNotBlank(map.get("value")) && StringUtils.isNotBlank(map.get("text"));
	}

}
