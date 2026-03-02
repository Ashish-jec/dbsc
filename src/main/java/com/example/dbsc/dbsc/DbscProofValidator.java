package com.example.dbsc.dbsc;

import com.nimbusds.jose.JWSVerifier;
import com.nimbusds.jose.crypto.ECDSAVerifier;
import com.nimbusds.jose.crypto.RSASSAVerifier;
import com.nimbusds.jose.jwk.ECKey;
import com.nimbusds.jose.jwk.JWK;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import org.springframework.stereotype.Component;

import java.util.Set;

/**
 * Validates DBSC proof JWTs per W3C DBSC § 9.10.
 * typ must be "dbsc+jwt", alg ES256/RS256, payload contains jti (challenge).
 */
@Component
public class DbscProofValidator {

    private static final String TYP_DBSC_JWT = "dbsc+jwt";
    private static final Set<String> ALLOWED_ALGS = Set.of("ES256", "RS256");

    /**
     * Validates a DBSC proof JWT for registration (must include jwk in header).
     * Returns the parsed claims and the JWK from the header.
     */
    public RegistrationProofResult validateRegistrationProof(String jwtString) throws DbscValidationException {
        SignedJWT jwt = parseAndValidateStructure(jwtString, true);
        JWK jwk = jwt.getHeader().getJWK();
        if (jwk == null) {
            throw new DbscValidationException("Registration proof must include jwk in header");
        }
        if (!ALLOWED_ALGS.contains(jwt.getHeader().getAlgorithm().getName())) {
            throw new DbscValidationException("Unsupported algorithm: " + jwt.getHeader().getAlgorithm());
        }
        JWSVerifier verifier = createVerifier(jwk);
        if (verifier == null) {
            throw new DbscValidationException("Could not create verifier for key type");
        }
        try {
            if (!jwt.verify(verifier)) {
                throw new DbscValidationException("Invalid signature");
            }
        } catch (Exception e) {
            throw new DbscValidationException("Signature verification failed: " + e.getMessage());
        }
        JWTClaimsSet claims = getClaims(jwt);
        requireClaim(claims, "jti");
        String authorization;
        try {
            authorization = claims.getStringClaim("authorization");
        } catch (java.text.ParseException e) {
            authorization = null;
        }
        return new RegistrationProofResult(claims.getJWTID(), authorization, jwk, jwt.getHeader().getAlgorithm().getName());
    }

    /**
     * Validates a DBSC proof JWT for refresh (no jwk in header; verified with stored key).
     */
    public RefreshProofResult validateRefreshProof(String jwtString, JWK expectedPublicKey) throws DbscValidationException {
        SignedJWT jwt = parseAndValidateStructure(jwtString, false);
        if (jwt.getHeader().getJWK() != null) {
            throw new DbscValidationException("Refresh proof must not include jwk");
        }
        JWSVerifier verifier = createVerifier(expectedPublicKey);
        if (verifier == null) {
            throw new DbscValidationException("Could not create verifier for stored key");
        }
        try {
            if (!jwt.verify(verifier)) {
                throw new DbscValidationException("Invalid signature");
            }
        } catch (Exception e) {
            throw new DbscValidationException("Signature verification failed: " + e.getMessage());
        }
        JWTClaimsSet claims = getClaims(jwt);
        String jti = claims.getJWTID();
        requireClaim(jti, "jti");
        return new RefreshProofResult(jti);
    }

    private SignedJWT parseAndValidateStructure(String jwtString, boolean requireJwk) throws DbscValidationException {
        SignedJWT jwt;
        try {
            jwt = SignedJWT.parse(jwtString);
        } catch (Exception e) {
            throw new DbscValidationException("Invalid JWT: " + e.getMessage());
        }
        if (jwt.getHeader().getType() == null || !TYP_DBSC_JWT.equals(jwt.getHeader().getType().getType())) {
            throw new DbscValidationException("JWT typ must be dbsc+jwt");
        }
        if (!ALLOWED_ALGS.contains(jwt.getHeader().getAlgorithm().getName())) {
            throw new DbscValidationException("Unsupported algorithm");
        }
        if (requireJwk && jwt.getHeader().getJWK() == null) {
            throw new DbscValidationException("Registration proof must include jwk");
        }
        return jwt;
    }

    private JWTClaimsSet getClaims(SignedJWT jwt) throws DbscValidationException {
        try {
            return jwt.getJWTClaimsSet();
        } catch (Exception e) {
            throw new DbscValidationException("Invalid JWT claims: " + e.getMessage());
        }
    }

    private void requireClaim(JWTClaimsSet claims, String name) throws DbscValidationException {
        try {
            if (claims.getClaim(name) == null) {
                throw new DbscValidationException("Missing claim: " + name);
            }
        } catch (DbscValidationException e) {
            throw e;
        } catch (Exception e) {
            throw new DbscValidationException("Missing claim: " + name);
        }
    }

    private void requireClaim(String value, String name) throws DbscValidationException {
        if (value == null || value.isBlank()) {
            throw new DbscValidationException("Missing claim: " + name);
        }
    }

    private JWSVerifier createVerifier(JWK jwk) {
        try {
            if (jwk instanceof ECKey ecKey) {
                return new ECDSAVerifier(ecKey);
            }
            if (jwk instanceof RSAKey rsaKey) {
                return new RSASSAVerifier(rsaKey);
            }
            return null;
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Compute JWK thumbprint per RFC 7638, base64url without padding (for provider_key).
     */
    public static String computeThumbprintBase64Url(JWK jwk) {
        try {
            return jwk.computeThumbprint().toString();
        } catch (Exception e) {
            throw new IllegalArgumentException("JWK thumbprint failed", e);
        }
    }

    public record RegistrationProofResult(String jti, String authorization, JWK publicKey, String algorithm) {}
    public record RefreshProofResult(String jti) {}

    public static class DbscValidationException extends Exception {
        public DbscValidationException(String message) {
            super(message);
        }
    }
}
