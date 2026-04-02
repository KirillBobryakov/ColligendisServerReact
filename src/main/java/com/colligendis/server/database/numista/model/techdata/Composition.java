package com.colligendis.server.database.numista.model.techdata;

import com.colligendis.server.database.AbstractNode;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

@Data
@EqualsAndHashCode(callSuper = true)
@NoArgsConstructor
public class Composition extends AbstractNode {
	public static final String LABEL = "COMPOSITION";

	public static final String HAS_COMPOSITION_TYPE = "HAS_COMPOSITION_TYPE";
	private CompositionType compositionType;

	public static final String PART1_IS_MADE_OF = "PART1_IS_MADE_OF";
	private Metal part1Metal;
	private CompositionPartType part1Type;
	private String part1MetalFineness;

	public static final String PART2_IS_MADE_OF = "PART2_IS_MADE_OF";
	private Metal part2Metal;
	private CompositionPartType part2Type;
	private String part2MetalFineness;

	public static final String PART3_IS_MADE_OF = "PART3_IS_MADE_OF";
	private Metal part3Metal;
	private CompositionPartType part3Type;
	private String part3MetalFineness;

	public static final String PART4_IS_MADE_OF = "PART4_IS_MADE_OF";
	private Metal part4Metal;
	private CompositionPartType part4Type;
	private String part4MetalFineness;

	private String compositionAdditionalDetails;

}
