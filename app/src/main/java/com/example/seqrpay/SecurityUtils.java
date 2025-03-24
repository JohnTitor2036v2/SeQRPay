package com.example.seqrpay;

import android.util.Base64;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

public class SecurityUtils {
    private static final String AES_ALGORITHM = "AES/GCM/NoPadding";
    private static final int GCM_TAG_LENGTH = 128;

    // Hash password using SHA-256
    public static String hashPassword(String password) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(password.getBytes(StandardCharsets.UTF_8));
            return Base64.encodeToString(hash, Base64.NO_WRAP);
        } catch (NoSuchAlgorithmException e) {
            e.printStackTrace();
            return null;
        }
    }

    // Generate a random AES-256 encryption key
    public static String generateEncryptionKey() {
        try {
            KeyGenerator keyGen = KeyGenerator.getInstance("AES");
            keyGen.init(256, new SecureRandom());
            SecretKey key = keyGen.generateKey();
            return Base64.encodeToString(key.getEncoded(), Base64.NO_WRAP);
        } catch (NoSuchAlgorithmException e) {
            e.printStackTrace();
            return null;
        }
    }

    // Encrypt data using AES-256
    public static String encrypt(String data, String keyStr) {
        try {
            byte[] keyBytes = Base64.decode(keyStr, Base64.NO_WRAP);
            SecretKey key = new SecretKeySpec(keyBytes, "AES");

            Cipher cipher = Cipher.getInstance(AES_ALGORITHM);
            SecureRandom random = new SecureRandom();
            byte[] iv = new byte[12]; // GCM recommended IV size
            random.nextBytes(iv);

            GCMParameterSpec spec = new GCMParameterSpec(GCM_TAG_LENGTH, iv);
            cipher.init(Cipher.ENCRYPT_MODE, key, spec);

            byte[] encryptedData = cipher.doFinal(data.getBytes(StandardCharsets.UTF_8));

            // Combine IV and encrypted data
            byte[] combined = new byte[iv.length + encryptedData.length];
            System.arraycopy(iv, 0, combined, 0, iv.length);
            System.arraycopy(encryptedData, 0, combined, iv.length, encryptedData.length);

            return Base64.encodeToString(combined, Base64.NO_WRAP);
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    // Decrypt data using AES-256
    public static String decrypt(String encryptedDataStr, String keyStr) {
        try {
            byte[] keyBytes = Base64.decode(keyStr, Base64.NO_WRAP);
            SecretKey key = new SecretKeySpec(keyBytes, "AES");

            byte[] combined = Base64.decode(encryptedDataStr, Base64.NO_WRAP);

            // Extract IV and encrypted data
            byte[] iv = new byte[12];
            byte[] encryptedData = new byte[combined.length - 12];
            System.arraycopy(combined, 0, iv, 0, iv.length);
            System.arraycopy(combined, iv.length, encryptedData, 0, encryptedData.length);

            Cipher cipher = Cipher.getInstance(AES_ALGORITHM);
            GCMParameterSpec spec = new GCMParameterSpec(GCM_TAG_LENGTH, iv);
            cipher.init(Cipher.DECRYPT_MODE, key, spec);

            byte[] decryptedData = cipher.doFinal(encryptedData);
            return new String(decryptedData, StandardCharsets.UTF_8);
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    // Encrypt user's key with their password (simplified - in a real app use a more secure method)
    public static String encryptKey(String key, String hashedPassword) {
        // Use the first 32 bytes of the hashed password as the encryption key
        String simplifiedKey = hashedPassword.substring(0, Math.min(hashedPassword.length(), 32));
        return encrypt(key, Base64.encodeToString(simplifiedKey.getBytes(), Base64.NO_WRAP));
    }

    // Decrypt user's key with their password
    public static String decryptKey(String encryptedKey, String hashedPassword) {
        // Use the first 32 bytes of the hashed password as the encryption key
        String simplifiedKey = hashedPassword.substring(0, Math.min(hashedPassword.length(), 32));
        return decrypt(encryptedKey, Base64.encodeToString(simplifiedKey.getBytes(), Base64.NO_WRAP));
    }
}

