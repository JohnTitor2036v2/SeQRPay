package com.example.seqrpay;

import android.util.Base64;
import android.util.Log; // Added for error logging

import java.nio.charset.StandardCharsets;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.KeySpec;

import javax.crypto.Cipher; // Keep if needed for other encryption later
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import javax.crypto.spec.SecretKeySpec; // Keep if needed for other encryption later
import javax.crypto.spec.GCMParameterSpec; // Keep if needed for other encryption later


public class SecurityUtils {

    private static final String TAG = "SecurityUtils";
    private static final String PBKDF2_ALGORITHM = "PBKDF2WithHmacSHA256";
    private static final int SALT_BYTE_SIZE = 16; // Standard salt size
    private static final int HASH_BYTE_SIZE = 32; // Corresponds to SHA-256 output size
    private static final int PBKDF2_ITERATIONS = 10000; // Iteration count (adjust as needed)

    // --- Keep AES methods if needed for future encryption, but DO NOT use encryptKey/decryptKey ---
    private static final String AES_ALGORITHM = "AES/GCM/NoPadding";
    private static final int GCM_TAG_LENGTH = 128;

    // Generate a secure random salt
    public static byte[] generateSalt() {
        SecureRandom random = new SecureRandom();
        byte[] salt = new byte[SALT_BYTE_SIZE];
        random.nextBytes(salt);
        return salt;
    }

    // Hash password using PBKDF2 with HmacSHA256
    public static String hashPassword(final String password, final byte[] salt) {
        try {
            KeySpec spec = new PBEKeySpec(password.toCharArray(), salt, PBKDF2_ITERATIONS, HASH_BYTE_SIZE * 8);
            SecretKeyFactory factory = SecretKeyFactory.getInstance(PBKDF2_ALGORITHM);
            byte[] hash = factory.generateSecret(spec).getEncoded();
            return Base64.encodeToString(hash, Base64.NO_WRAP);
        } catch (NoSuchAlgorithmException | InvalidKeySpecException e) {
            Log.e(TAG, "Error hashing password", e);
            // In a real app, handle this more gracefully - perhaps throw a custom exception
            return null;
        }
    }

    // --- REMOVED Insecure Methods ---
    // public static String hashPassword(String password) { ... } // Old SHA-256 version removed
    // public static String generateEncryptionKey() { ... } // Keep if needed for OTHER keys later
    // public static String encryptKey(String key, String hashedPassword) { ... } // REMOVED (Insecure)
    // public static String decryptKey(String encryptedKey, String hashedPassword) { ... } // REMOVED (Insecure)

    // --- Keep AES encrypt/decrypt if you plan to encrypt OTHER data securely LATER ---
    // Ensure you use a securely derived key (e.g., via PBKDF2 with a *different* salt)
    // or a randomly generated key stored securely.
    public static String encrypt(String data, String keyStr) {
        try {
            byte[] keyBytes = Base64.decode(keyStr, Base64.NO_WRAP);
            SecretKeySpec key = new SecretKeySpec(keyBytes, "AES");

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
            Log.e(TAG, "Encryption error", e);
            return null;
        }
    }

    public static String decrypt(String encryptedDataStr, String keyStr) {
        try {
            byte[] keyBytes = Base64.decode(keyStr, Base64.NO_WRAP);
            SecretKeySpec key = new SecretKeySpec(keyBytes, "AES");

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
            Log.e(TAG, "Decryption error", e);
            return null;
        }
    }

}