package com.example.careerMonitor.Utils;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Utility class for generating SHA-256 hashes from strings (HTML content).
 */
public class HashUtils {

    private static final Logger log = LoggerFactory.getLogger(HashUtils.class);

    private HashUtils() {
        // Prevent direct instantiation
    }

    /**
     * Generates a SHA-256 hash for the given input string.
     * @param input The string (e.g., HTML content) to be hashed.
     * @return The hex string representation of the hash, or null if input is empty.
     * @throws RuntimeException if the SHA-256 algorithm is not available.
     */
    public static String generateSha256Hash(String input) {
        if (input == null || input.isEmpty()) {
            return null;
        }

        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(input.getBytes(StandardCharsets.UTF_8));

            // Convert byte array to hex string
            StringBuilder hexString = new StringBuilder();
            for (byte b : hash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) hexString.append('0');
                hexString.append(hex);
            }
            return hexString.toString();
        } catch (NoSuchAlgorithmException e) {
            log.error("SHA-256 algorithm not available", e);
            throw new RuntimeException("SHA-256 algorithm not available", e);
        }
    }
}
