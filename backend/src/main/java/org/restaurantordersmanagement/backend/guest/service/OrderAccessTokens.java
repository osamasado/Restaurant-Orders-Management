package org.restaurantordersmanagement.backend.guest.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;

/**
 * The secret a guest is handed when an order is placed, to follow that one order. 256 random bits, so it cannot be
 * guessed; only its SHA-256 is stored (a copy of the database cannot be used to read orders), and it is compared in
 * constant time.
 */
final class OrderAccessTokens {

    private static final SecureRandom RANDOM = new SecureRandom();

    private OrderAccessTokens() {
    }

    static String newToken() {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    static String hash(String token) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is always available", e);
        }
    }

    static boolean matches(String token, String storedHash) {
        if (token == null || storedHash == null) {
            return false;
        }
        return MessageDigest.isEqual(
                hash(token.trim()).getBytes(StandardCharsets.UTF_8), storedHash.getBytes(StandardCharsets.UTF_8));
    }

}
