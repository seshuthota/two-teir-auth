package com.company.l2app.security;

import org.springframework.stereotype.Component;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;

@Component
public class ClientSecretHasher {

    private static final String HASH_ALGORITHM = "SHA-256";
    private static final int SALT_LENGTH = 16;

    public String hash(String secret) {
        var salt = generateSalt();
        var digest = digest(secret, salt);
        return salt + ":" + Base64.getEncoder().encodeToString(digest);
    }

    public boolean matches(String secret, String storedHash) {
        var parts = storedHash.split(":", 2);
        if (parts.length != 2) return false;
        var salt = parts[0];
        var expectedDigest = Base64.getDecoder().decode(parts[1]);
        var actualDigest = digest(secret, salt);
        return MessageDigest.isEqual(expectedDigest, actualDigest);
    }

    private byte[] digest(String secret, String salt) {
        try {
            var md = MessageDigest.getInstance(HASH_ALGORITHM);
            md.update(salt.getBytes());
            return md.digest(secret.getBytes());
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("Hash algorithm not available", e);
        }
    }

    private String generateSalt() {
        var saltBytes = new byte[SALT_LENGTH];
        new SecureRandom().nextBytes(saltBytes);
        return Base64.getEncoder().encodeToString(saltBytes);
    }
}
