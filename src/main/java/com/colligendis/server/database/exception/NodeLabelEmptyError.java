package com.colligendis.server.database.exception;

public record NodeLabelEmptyError(String message) implements DatabaseException {

    @Override
    public int statusCode() {
        return 500;
    }
}
