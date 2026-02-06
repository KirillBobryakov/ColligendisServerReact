package com.colligendis.server.database.numista.model;

import java.util.ArrayList;
import java.util.List;

import com.colligendis.server.database.AbstractNode;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

@Data
@EqualsAndHashCode(callSuper = true)
@NoArgsConstructor
public class Subject extends AbstractNode {

    public static final String LABEL = "SUBJECT";

    private String numistaCode;
    private String name;

    private List<String> ruAlternativeNames = new ArrayList<>();

    public static final String RELATE_TO_COUNTRY = "RELATE_TO_COUNTRY";
    private Country country;

    public static final String PARENT_SUBJECT = "PARENT_SUBJECT";
    private Subject parentSubject;

    public Subject(String numistaCode, String name) {
        this.numistaCode = numistaCode;
        this.name = name;
    }

}
