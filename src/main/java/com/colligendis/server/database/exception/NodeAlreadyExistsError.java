package com.colligendis.server.database.exception;

public record NodeAlreadyExistsError(String message) implements DatabaseException {
    @Override
    public int statusCode() {
        return 500;
    }

}
