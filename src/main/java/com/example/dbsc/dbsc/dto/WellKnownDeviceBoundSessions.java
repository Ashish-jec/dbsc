package com.example.dbsc.dbsc.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;

/**
 * Well-known device-bound-sessions response per W3C DBSC § 11.6.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record WellKnownDeviceBoundSessions(
        List<String> registering_origins,
        List<String> relying_origins,
        String provider_origin
) {}
