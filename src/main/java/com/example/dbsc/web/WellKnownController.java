package com.example.dbsc.web;

import com.example.dbsc.config.DbscProperties;
import com.example.dbsc.dbsc.dto.WellKnownDeviceBoundSessions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Well-known URI for device-bound sessions per W3C DBSC § 11.6.
 * Serves /.well-known/device-bound-sessions with registering_origins, relying_origins, or provider_origin.
 */
@RestController
public class WellKnownController {

    private static final Logger log = LoggerFactory.getLogger(WellKnownController.class);
    private final DbscProperties properties;

    public WellKnownController(DbscProperties properties) {
        this.properties = properties;
    }

    @GetMapping(value = "/.well-known/device-bound-sessions", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<WellKnownDeviceBoundSessions> deviceBoundSessions() {
        List<String> registering = properties.getRegisteringOrigins() != null ? properties.getRegisteringOrigins() : List.of();
        List<String> relying = properties.getRelyingOrigins() != null ? properties.getRelyingOrigins() : List.of();
        String provider = properties.getProviderOrigin();
        WellKnownDeviceBoundSessions body = new WellKnownDeviceBoundSessions(
                registering.isEmpty() ? null : registering,
                relying.isEmpty() ? null : relying,
                provider
        );
        log.debug("Well-known: served device-bound-sessions");
        return ResponseEntity.ok(body);
    }
}
