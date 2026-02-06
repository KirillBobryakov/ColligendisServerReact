package com.colligendis.server.util;

public record Right<L, R>(R value) implements Either<L, R> {
    public boolean isLeft() {
        return false;
    }

    public boolean isRight() {
        return true;
    }

    public L left() {
        throw new IllegalStateException();
    }

    public R right() {
        return value;
    }
}