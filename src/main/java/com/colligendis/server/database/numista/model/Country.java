package com.colligendis.server.database.numista.model;

import java.util.ArrayList;
import java.util.List;

import com.colligendis.server.database.AbstractNode;
import com.colligendis.server.database.N4JUtil;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;

@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true, onlyExplicitlyIncluded = true)
public class Country extends AbstractNode {

    public static final String LABEL = "COUNTRY";

    // public static final String CONTAINS_CHILD_SUBJECT = "CONTAINS_CHILD_SUBJECT";
    // public static final String CONTAINS_ISSUER = "CONTAINS_ISSUER";
    /**
     * Only English name of countries. Unique field.
     */
    private String name;

    private String numistaCode;

    private List<String> ruAlternativeNames = new ArrayList<>();

    public static final String PARENT_SUBJECT = "PARENT_SUBJECT";
    private Subject parentSubject;

    public void addRuAlternativeName(String ruAlternativeName) {

        if (!this.ruAlternativeNames.contains(ruAlternativeName.trim())) {
            this.ruAlternativeNames.add(ruAlternativeName.trim());
        }
    }

    public void removeRuAlternativeName(String ruAlternativeName) {
        if (this.ruAlternativeNames.contains(ruAlternativeName.trim())) {
            this.ruAlternativeNames.remove(ruAlternativeName.trim());
        }
    }

}
