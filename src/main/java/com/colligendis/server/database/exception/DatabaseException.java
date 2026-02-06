package com.colligendis.server.database.exception;

public sealed interface DatabaseException
        permits NotFoundError, Neo4jInternalError, NodeLabelEmptyError, InputParametersError, NodeAlreadyExistsError,
        RelationshipAlreadyExistsError {

    String message();

    int statusCode();
}
