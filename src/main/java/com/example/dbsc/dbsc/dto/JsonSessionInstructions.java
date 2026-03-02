package com.example.dbsc.dbsc.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/**
 * JSON Session Instruction Format per W3C DBSC § 9.6.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record JsonSessionInstructions(
        String session_identifier,
        String refresh_url,
        @JsonProperty("continue") Boolean continueSession,
        JsonSessionScope scope,
        List<JsonSessionCredential> credentials,
        List<String> allowed_refresh_initiators
) {
    public static JsonSessionInstructions of(String sessionId, String refreshUrl, String origin,
                                             String cookieName, String cookieAttributes,
                                             List<String> allowedRefreshInitiators) {
        JsonSessionScope scope = new JsonSessionScope(origin, false, List.of());
        JsonSessionCredential cred = new JsonSessionCredential("cookie", cookieName, cookieAttributes);
        return new JsonSessionInstructions(
                sessionId,
                refreshUrl,
                true,
                scope,
                List.of(cred),
                allowedRefreshInitiators != null ? allowedRefreshInitiators : List.of()
        );
    }
}
