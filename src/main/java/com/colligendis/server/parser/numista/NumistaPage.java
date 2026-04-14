package com.colligendis.server.parser.numista;

import org.jsoup.nodes.Document;

import com.colligendis.server.database.ColligendisUser;
import com.colligendis.server.database.ColligendisUserService;
import com.colligendis.server.database.numista.model.CollectibleType;
import com.colligendis.server.database.numista.model.Currency;
import com.colligendis.server.database.numista.model.Denomination;
import com.colligendis.server.database.numista.model.Issuer;
import com.colligendis.server.database.numista.model.NType;
import com.colligendis.server.database.numista.service.NTypeService;
import com.colligendis.server.database.result.CreateNodeExecutionStatus;
import com.colligendis.server.database.result.FindExecutionStatus;
import com.colligendis.server.database.result.UpdateExecutionStatus;
import com.colligendis.server.logger.BaseLogger;
import com.colligendis.server.parser.ParsingStatus;
import com.colligendis.server.parser.numista.exception.ParserException;
import com.colligendis.server.util.Either;

import lombok.Data;
import lombok.EqualsAndHashCode;
import reactor.core.publisher.Mono;

@Data
@EqualsAndHashCode(callSuper = false)
public class NumistaPage {

	private final NTypeService nTypeService;
	private final ColligendisUserService colligendisUserService;

	public static final String TYPE_PAGE_PREFIX = "https://en.numista.com/catalogue/contributions/modifier.php?id=";

	public String nid;

	public String url;
	public Document page;

	public ParsingStatus parsingStatus = ParsingStatus.NOT_CHANGED;

	@EqualsAndHashCode.Exclude
	private final BaseLogger pipelineStepLogger = new BaseLogger();

	public NType nType = null;

	public CollectibleType collectibleType = null;

	public Issuer issuer = null;

	public Currency currency = null;
	public Denomination denomination = null;

	public NumistaPage(String nid, ColligendisUserService colligendisUserService, NTypeService nTypeService) {
		this.nid = nid;
		this.url = TYPE_PAGE_PREFIX + nid;
		this.colligendisUserService = colligendisUserService;
		this.nTypeService = nTypeService;
	}

	/**
	 * Resolves lazily so the Mono is used only after ColligendisUserService is
	 * fully initialized.
	 */
	public Mono<ColligendisUser> getNumistaParserUserMono() {
		return colligendisUserService.getNumistaParserUserMono();
	}

	public static Mono<NumistaPage> create(String nid, ColligendisUserService colligendisUserService,
			NTypeService nTypeService) {
		return Mono.fromSupplier(() -> new NumistaPage(nid, colligendisUserService, nTypeService));
	}

	public Mono<NumistaPage> loadNType() {
		return Mono.defer(() -> {
			return nTypeService.findByNid(nid, pipelineStepLogger)
					.flatMap(executionResult -> {
						if (executionResult.getStatus().equals(FindExecutionStatus.FOUND)) {
							this.nType = executionResult.getNode();
							return Mono.just(this);
						} else if (executionResult.getStatus().equals(FindExecutionStatus.NOT_FOUND)) {
							pipelineStepLogger.warning("NType: (nid: {}) was creating", nid);
							return nTypeService.create(new NType(nid), getNumistaParserUserMono(), pipelineStepLogger)
									.flatMap(createdExecutionResult -> {
										if (createdExecutionResult.getStatus()
												.equals(CreateNodeExecutionStatus.WAS_CREATED)) {
											this.nType = createdExecutionResult.getNode();
											pipelineStepLogger.info("NType created successfully with nid: {}", nid);
											return Mono.just(this);
										} else {
											pipelineStepLogger.error("Failed to create NType with nid: {}", nid);
											createdExecutionResult.logError(pipelineStepLogger);
											return Mono.error(new ParserException(
													"Failed to create NType with nid: " + nid + " - "
															+ createdExecutionResult.getStatus()));
										}
									});
						} else {
							pipelineStepLogger.error("Failed to load NType with nid: {}", nid);
							executionResult.logError(pipelineStepLogger);
							return Mono.error(new ParserException(
									"Failed to load NType with nid: " + nid + " - " + executionResult.getStatus()));
						}
					});
		});
	}

	public Mono<NumistaPage> saveNType() {
		return Mono.defer(() -> {
			if (nType == null) {
				return Mono.error(new ParserException("NType is null while saving NType with nid: " + nid));
			}
			return nTypeService.update(nType, getNumistaParserUserMono(), pipelineStepLogger)
					.flatMap(updatedExecutionResult -> {
						if (updatedExecutionResult.getStatus().equals(UpdateExecutionStatus.WAS_UPDATED)) {
							pipelineStepLogger.info("NType updated successfully with nid: {}", nid);
							return Mono.just(this);
						} else if (updatedExecutionResult.getStatus().equals(UpdateExecutionStatus.NOT_FOUND)) {
							pipelineStepLogger.error("NType not found with nid: {}", nid);
							updatedExecutionResult.logError(pipelineStepLogger);
							return Mono.error(new ParserException(
									"NType not found with nid: " + nid + " - " + updatedExecutionResult.getStatus()));
						} else if (updatedExecutionResult.getStatus()
								.equals(UpdateExecutionStatus.NOTHING_TO_UPDATE)) {
							pipelineStepLogger.info("NType has no properties to update with nid: {}", nid);
							return Mono.just(this);
						} else {
							pipelineStepLogger.error("Failed to update NType with nid: {}", nid);
							updatedExecutionResult.logError(pipelineStepLogger);
							return Mono.error(new ParserException(
									"Failed to update NType with nid: " + nid + " - "
											+ updatedExecutionResult.getStatus()));
						}
					});

		});
	}

	// .andThen(pageParser.mintageParser)
	// .andThen(pageParser.technicalDataParser)
	// .andThen(pageParser.obverseParser)
	// .andThen(pageParser.reverseParser)
	// .andThen(pageParser.edgeParser)
	// .andThen(pageParser.watermarkParser)
	// .andThen(pageParser.mintsParser)
	// .andThen(pageParser.printerParser);

}
