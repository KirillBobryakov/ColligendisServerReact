package com.colligendis.server.parser.numista.init_parser;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import lombok.Data;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class IssuerPageResponse {

    private List<IssuerEntry> results;
    private Pagination pagination;

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class IssuerEntry {
        private String id;
        private String text;
        private int level;

        @JsonProperty("short")
        private int shortValue;

        @JsonProperty("flag_type")
        private String flagType;

        @JsonProperty("flag_code")
        private String flagCode;

        private int section;
        private int period;
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Pagination {
        private boolean more;
    }
}
