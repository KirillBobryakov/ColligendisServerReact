package com.colligendis.server.database.exception;

public record NotFoundError(String message) implements DatabaseException {

    @Override
    public int statusCode() {
        return 404;
    }

}