package com.colligendis.server.database.numista.model.techdata;

import com.colligendis.server.database.AbstractNode;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

@Data
@EqualsAndHashCode(callSuper = true)
@AllArgsConstructor
@NoArgsConstructor
public class CompositionType extends AbstractNode {
	public static final String LABEL = "COMPOSITION_TYPE";

	private String code;
	private String name;

	// plain("Single material"),
	// plated("Plated metal"),
	// clad("Clad metal"),
	// bimetallic("Bimetallic"),
	// bimetallic_plated("Bimetallic with plated metal centre"),
	// bimetallic_plated_ring("Bimetallic with plated metal ring"),
	// bimetallic_plated_plated("Bimetallic with plated centre and ring"),
	// bimetallic_clad("Bimetallic with clad metal centre"),
	// trimetallic("Trimetallic"),
	// other("Other");

	// gold_deposited_polymer("Gold‑deposited polymer"),
	// hybrid_substrate("Hybrid substrate"),
	// other("Other"),
	// paper("Paper"),
	// polymer("Polymer"),
	// silk("Silk"),
	// silver_deposited_polymer("Silver‑deposited polymer");
}
