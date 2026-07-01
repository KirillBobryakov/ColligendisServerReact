package com.colligendis.server.database.meshok;

import java.util.List;

import com.colligendis.server.database.AbstractNode;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class MeshokLot extends AbstractNode {

	public static final String LABEL = "MESHKOT_LOT";

	private String lotId;
	private String title;
	private List<String> localPictures;
	private String lotJson;

}
