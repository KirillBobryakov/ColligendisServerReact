package com.colligendis.server.database;

import com.colligendis.server.database.common.CommonServices;
import com.colligendis.server.database.numista.NumistaServices;

public class N4JUtil {

    private static N4JUtil instance;

    public final NumistaServices numistaServices;
    public final CommonServices commonServices;
    public final ColligendisUserService colligendisUserService;

    private N4JUtil(NumistaServices numistaServices, CommonServices commonServices,
            ColligendisUserService colligendisUserService) {
        this.numistaServices = numistaServices;
        this.commonServices = commonServices;
        this.colligendisUserService = colligendisUserService;
    }

    public static void InitInstance(NumistaServices numistaServices, CommonServices commonServices,
            ColligendisUserService colligendisUserService) {
        instance = new N4JUtil(numistaServices, commonServices, colligendisUserService);
    }

    public static synchronized N4JUtil getInstance() {
        if (instance == null) {
            try {
                throw new Exception("N4JUtil's Instance was forgotten to be initialized");
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }
        return instance;
    }

}
