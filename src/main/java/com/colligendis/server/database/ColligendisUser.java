package com.colligendis.server.database;

import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
public class ColligendisUser extends AbstractUser {
    public final static String LABEL = "COLLIGENDIS_USER";

}
