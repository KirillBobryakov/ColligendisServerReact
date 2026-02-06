package com.colligendis.server.database.common.model;

import java.util.ArrayList;

import com.colligendis.server.database.AbstractNode;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

/**
 * Dynasty, house, extended period, or any other group of ruling authorities
 *
 * Information takes from <a href=
 * "https://en.numista.com/help/add-or-modify-a-ruling-authority-in-the-catalogue-192.html">Numista</a>
 */
@Data
@EqualsAndHashCode(callSuper = false)
@NoArgsConstructor
public class Year extends AbstractNode {
    public static final String LABEL = "YEAR";

    private Integer value;

    public static final String TO_NUMBER_IN = "TO_NUMBER_IN";
    private Calendar calendar;

    public static final String MATCH_UP_TO = "MATCH_UP_TO";
    private ArrayList<Year> sameYears = new ArrayList<>();

    public Year(Integer value) {
        this.value = value;
    }

}
