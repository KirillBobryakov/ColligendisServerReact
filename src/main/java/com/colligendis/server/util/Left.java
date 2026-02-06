package com.colligendis.server.util;

public record Left<L, R>(L value) implements Either<L, R> {
    public boolean isLeft() {
        return true;
    }

    public boolean isRight() {
        return false;
    }

    public L left() {
        return value;
    }

    public R right() {
        throw new IllegalStateException();
    }
}