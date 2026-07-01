package com.colligendis.server.database.result;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class ExecutionStatusCoercionTest {

	@Test
	void toCreateNode_mapsWriteExecutionStatusByName() {
		assertEquals(
				CreateNodeExecutionStatus.EMPTY_RESULT,
				ExecutionStatusCoercion.toCreateNode(WriteExecutionStatus.EMPTY_RESULT));
	}

	@Test
	void isCollectionItemPersistSuccess_acceptsNothingToUpdate() {
		assertTrue(ExecutionStatusCoercion.isCollectionItemPersistSuccess(UpdateExecutionStatus.NOTHING_TO_UPDATE));
	}
}
