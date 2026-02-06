package com.colligendis.server.database.common.model;

import com.colligendis.server.database.AbstractNode;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

@Data
@EqualsAndHashCode(callSuper = true)
@AllArgsConstructor
@NoArgsConstructor
public class Calendar extends AbstractNode {
    public static final String LABEL = "CALENDAR";

    public static final String GREGORIAN_CODE = "gregorien";
    public static final String ISLAMI_HIJRI = "musulman";
    public static final String IRANIAN_PERSIAN = "persan";

    private String code;
    private String name;

    private Integer toGregorianShift;

}
