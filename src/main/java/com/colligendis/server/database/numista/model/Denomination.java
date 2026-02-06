package com.colligendis.server.database.numista.model;

import com.colligendis.server.database.AbstractNode;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

@Data
@EqualsAndHashCode(callSuper = true)
@NoArgsConstructor
@AllArgsConstructor
public class Denomination extends AbstractNode {
    public static final String LABEL = "DENOMINATION";

    private String nid;
    private String fullName;
    private String name;

    private Float numericValue;

    public static final String UNDER_CURRENCY = "UNDER_CURRENCY";
    private Currency currency;

    public Denomination(String nid, String name, String fullName, Float numericValue) {
        this.nid = nid;
        this.name = name;
        this.fullName = fullName;
        this.numericValue = numericValue;
    }
}
