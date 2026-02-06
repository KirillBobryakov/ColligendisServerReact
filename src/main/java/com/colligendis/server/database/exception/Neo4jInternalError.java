package com.colligendis.server.database.exception;

public record Neo4jInternalError(String message) implements DatabaseException {

    @Override
    public int statusCode() {
        return 500;
    }
}
