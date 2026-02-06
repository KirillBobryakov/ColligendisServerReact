package com.colligendis.server.database.exception;

public record InputParametersError(String message) implements DatabaseException {

    @Override
    public int statusCode() {
        return 500;
    }
}
