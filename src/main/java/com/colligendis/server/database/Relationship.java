package com.colligendis.server.database;

public record Relationship(AbstractNode fromNode, AbstractNode toNode, String type) {

    public boolean isValid() {
        return fromNode != null && toNode != null && type != null;
    }
}
