package com.colligendis.server.database.numista.model;

import com.colligendis.server.database.AbstractNode;

import lombok.AllArgsConstructor;
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
@AllArgsConstructor
@NoArgsConstructor
public class RulerGroup extends AbstractNode {

    public static final String LABEL = "RULER_GROUP";

    private String nid;
    private String name;

}
