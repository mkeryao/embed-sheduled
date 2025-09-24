package com.github.embed.scheduler.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;


public class PasswordUtil {

    private static final Logger logger = LoggerFactory.getLogger(PasswordUtil.class);

    public static String hashPassword(String password, String salt) {
        if (password == null || password.isEmpty()) {
            throw new IllegalArgumentException("Password cannot be null or empty.");
        }
        if (salt == null || salt.isEmpty()) {
            // Using a default salt or throwing an error are options.
            // For better security, a unique salt per user is best, but here username is used as salt.
            // If username can be empty, this could be an issue.
            // Let's assume username (as salt) will not be empty based on usage.
            logger.warn("Salt is null or empty. This may compromise password security if password is also weak.");
            // For this example, let's proceed, but in a real app, enforce non-empty salt.
        }

        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            // Add salt to password to protect against rainbow table attacks
            // Concatenating salt and password. Order can vary.
            String saltedPassword = salt + password;
            byte[] hashedBytes = md.digest(saltedPassword.getBytes(StandardCharsets.UTF_8));

            // Convert byte array to hexadecimal string
            StringBuilder sb = new StringBuilder();
            for (byte b : hashedBytes) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            logger.error("SHA-256 algorithm not found. This should not happen.", e);
            // Fallback or rethrow. For a critical function like this, rethrowing might be appropriate.
            throw new RuntimeException("Password hashing failed due to missing SHA-256 algorithm.", e);
        }
    }

    // Optional: A method to verify password if needed, though direct hash comparison is done in AuthController
    public static  boolean verifyPassword(String providedPassword,
                                          String storedHashedPassword, String salt) {
        if (providedPassword == null || providedPassword.isEmpty() ||
            storedHashedPassword == null || storedHashedPassword.isEmpty()) {
            return false;
        }
        String newHash = hashPassword(providedPassword, salt);
        return newHash.equals(storedHashedPassword);
    }
}
