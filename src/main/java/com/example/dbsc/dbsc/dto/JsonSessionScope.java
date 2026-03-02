package com.example.dbsc.dbsc.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/**
 * JSON Session Scope per W3C DBSC § 9.7.
 */
public record JsonSessionScope(
        String origin,
        @JsonProperty("include_site") boolean include_site,
        @JsonProperty("scope_specification") List<JsonSessionScopeRule> scope_specification
) {}
