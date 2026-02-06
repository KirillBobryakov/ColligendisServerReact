package com.colligendis.server.database.exception;

public record RelationshipAlreadyExistsError(String message) implements DatabaseException {

    @Override
    public int statusCode() {
        return 500;
    }

}
