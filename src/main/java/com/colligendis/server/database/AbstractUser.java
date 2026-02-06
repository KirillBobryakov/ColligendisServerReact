package com.colligendis.server.database;

import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
public class AbstractUser extends AbstractNode {

    public String username;
    public String password;

}
