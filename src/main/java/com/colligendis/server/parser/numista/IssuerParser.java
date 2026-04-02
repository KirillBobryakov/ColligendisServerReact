package com.colligendis.server.parser.numista;

import java.util.Map;
import java.util.Objects;

import org.springframework.stereotype.Component;

import com.colligendis.server.database.ExecutionStatus;
import com.colligendis.server.database.numista.model.Issuer;
import com.colligendis.server.database.numista.service.IssuerService;
import com.colligendis.server.database.numista.service.NTypeService;
import com.colligendis.server.parser.numista.exception.ParserException;

import lombok.RequiredArgsConstructor;
import reactor.core.publisher.Mono;

@Component
@RequiredArgsConstructor
public class IssuerParser extends Parser {

	// Cached services - lazily initialized
	private final NTypeService nTypeService;
	private final IssuerService issuerService;

	@Override
	protected Mono<NumistaPage> parse(NumistaPage numistaPage) {
		return Mono.defer(() -> {
			Map<String, String> emetteur = NumistaParseUtils.getAttributeWithTextSingleOption(
					numistaPage.page, "#emetteur", "value");

			if (emetteur == null) {
				numistaPage.getPipelineStepLogger()
						.error("Issuer: not found for nid: {} - Can't find Issuer on the page", numistaPage.nid);
				return Mono.error(new ParserException(
						"Can't find Issuer on the page for nid: " + numistaPage.nid));
			}

			String code = emetteur.get("value");
			String name = emetteur.get("text");

			return issuerService.findByNumistaCode(code, numistaPage.getPipelineStepLogger())
					.flatMap(executionResult -> {
						if (executionResult.getStatus().equals(ExecutionStatus.NODE_IS_FOUND)) {
							return Mono.just(executionResult.getNode());
						} else {
							numistaPage.getPipelineStepLogger().error("Failed to find Issuer: {}",
									executionResult.getStatus());
							executionResult.logError(numistaPage.getPipelineStepLogger());
							return Mono.error(new ParserException("Failed to find Issuer: " + code));
						}
					})
					.flatMap(issuer -> linkIssuerToNType(issuer, name, numistaPage));
		});
	}

	/**
	 * Link the issuer to the NType and update the parsing status
	 * 
	 * @param issuer
	 * @param expectedName
	 * @param numistaPage
	 * @return
	 */
	private Mono<NumistaPage> linkIssuerToNType(Issuer issuer, String expectedName, NumistaPage numistaPage) {
		return numistaPage.getNumistaParserUserMono()
				.flatMap(colligendisUser -> {
					if (!Objects.equals(issuer.getName(), expectedName)) {
						numistaPage.getPipelineStepLogger()
								.warning("Issuer name mismatch for nid: {} - db='{}' vs expected='{}' - updating",
										numistaPage.nid, issuer.getName(), expectedName);
						issuer.setName(expectedName);
						return issuerService
								.update(issuer, Mono.just(colligendisUser), numistaPage.getPipelineStepLogger())
								.flatMap(er -> {
									ExecutionStatus st = er.getStatus();
									if (ExecutionStatus.NODE_WAS_UPDATED.equals(st)
											|| ExecutionStatus.NODE_NOTHING_TO_UPDATE.equals(st)) {
										Issuer n = er.getNode();
										return Mono.just(n != null ? n : issuer);
									}
									numistaPage.getPipelineStepLogger().error("Issuer update failed: {}", st);
									er.logError(numistaPage.getPipelineStepLogger());
									return Mono.<Issuer>error(new ParserException(
											"Failed to update issuer for nid: " + numistaPage.nid));
								});
					} else {
						return Mono.just(issuer);
					}
				})
				.flatMap(issuerToUse -> nTypeService
						.isRelationshipToIssuerExists(numistaPage.nType, issuerToUse,
								numistaPage.getPipelineStepLogger())
						.flatMap(executionResult -> {
							if (executionResult.getStatus().equals(ExecutionStatus.RELATIONSHIP_IS_EXISTS)) {
								numistaPage.getPipelineStepLogger().info("Issuer: {}", issuerToUse.getName());
								numistaPage.setIssuer(issuerToUse);
								return Mono.<NumistaPage>just(numistaPage);
							}
							return nTypeService
									.setIssuer(numistaPage.nType, issuerToUse, numistaPage.getNumistaParserUserMono(),
											numistaPage.getPipelineStepLogger())
									.flatMap(linkStatus -> {

										if (!linkStatus.getStatus().equals(ExecutionStatus.RELATIONSHIP_WAS_CREATED)) {
											numistaPage.setIssuer(issuerToUse);
											numistaPage.getPipelineStepLogger()
													.error("Issuer error while linking to NType for nid: {} and issuer name: {} - Error: {}",
															numistaPage.nid, issuerToUse.getName(), linkStatus);
											return Mono.<NumistaPage>error(new ParserException(
													"Failed to link issuer to NType: " + numistaPage.nid));
										}

										numistaPage.setIssuer(issuerToUse);
										numistaPage.getPipelineStepLogger()
												.warning("Issuer: set to {}", issuerToUse.getName());

										return Mono.<NumistaPage>just(numistaPage);
									});
						}));
	}
}
