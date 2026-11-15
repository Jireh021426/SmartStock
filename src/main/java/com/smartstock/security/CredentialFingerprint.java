package com.smartstock.security;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

public final class CredentialFingerprint {

    private CredentialFingerprint() {
    }

    public static String fromPasswordHash(String passwordHash) {
        if (passwordHash == null || passwordHash.isBlank()) {
            throw new IllegalArgumentException(
                    "Password hash is required."
            );
        }

        try {
            MessageDigest digest =
                    MessageDigest.getInstance("SHA-256");

            byte[] fingerprint = digest.digest(
                    passwordHash.getBytes(StandardCharsets.UTF_8)
            );

            return HexFormat.of().formatHex(fingerprint);

        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException(
                    "SHA-256 is unavailable.",
                    ex
            );
        }
    }
}
