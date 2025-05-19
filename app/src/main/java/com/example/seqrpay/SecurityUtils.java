package com.example.seqrpay;

import android.content.Context; // Keep if needed for other methods, not directly used in new crypto methods
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.util.Base64;
import android.util.Log;

import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.KeyStore;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.Signature;
import java.security.spec.ECGenParameterSpec;
import java.security.spec.InvalidKeySpecException; // For PBKDF2
import java.security.spec.KeySpec; // For PBKDF2
import java.security.NoSuchAlgorithmException; // For PBKDF2 & KeyFactory
import java.security.KeyFactory; // For decoding public key
import java.security.spec.X509EncodedKeySpec; // For decoding public key

import javax.crypto.SecretKeyFactory; // For PBKDF2
import javax.crypto.spec.PBEKeySpec; // For PBKDF2
import javax.crypto.Cipher; // Keep if needed for other encryption later
import javax.crypto.spec.SecretKeySpec; // Keep if needed for other encryption later
import javax.crypto.spec.GCMParameterSpec; // Keep if needed for other encryption later
import java.security.SecureRandom; // For salt and AES IV

public class SecurityUtils {

    private static final String TAG = "SecurityUtils";
    // Constants for Password-Based Key Derivation
    private static final String PBKDF2_ALGORITHM = "PBKDF2WithHmacSHA256";
    private static final int SALT_BYTE_SIZE = 16;
    private static final int HASH_BYTE_SIZE = 32; // Corresponds to SHA-256 output size
    private static final int PBKDF2_ITERATIONS = 10000;

    // Constants for AES Encryption (if used for other purposes)
    private static final String AES_ALGORITHM = "AES/GCM/NoPadding";
    private static final int GCM_TAG_LENGTH = 128; // bits
    private static final int GCM_IV_LENGTH = 12; // bytes

    // --- NEW: Constants for Digital Signatures ---
    private static final String ANDROID_KEYSTORE_PROVIDER = "AndroidKeyStore";
    private static final String EC_KEY_ALGORITHM = KeyProperties.KEY_ALGORITHM_EC; // Elliptic Curve
    public static final String SIGNATURE_ALGORITHM = "SHA256withECDSA"; // Algorithm for signing
    private static final String EC_CURVE_SPEC = "secp256r1"; // NIST P-256 curve, widely supported

    /**
     * Generates a secure random salt.
     * @return byte array of the salt.
     */
    public static byte[] generateSalt() {
        SecureRandom random = new SecureRandom();
        byte[] salt = new byte[SALT_BYTE_SIZE];
        random.nextBytes(salt);
        return salt;
    }

    /**
     * Hashes a password using PBKDF2 with HmacSHA256.
     * @param password The password to hash.
     * @param salt The salt to use for hashing.
     * @return Base64 encoded string of the hashed password, or null on error.
     */
    public static String hashPassword(final String password, final byte[] salt) {
        try {
            KeySpec spec = new PBEKeySpec(password.toCharArray(), salt, PBKDF2_ITERATIONS, HASH_BYTE_SIZE * 8);
            SecretKeyFactory factory = SecretKeyFactory.getInstance(PBKDF2_ALGORITHM);
            byte[] hash = factory.generateSecret(spec).getEncoded();
            return Base64.encodeToString(hash, Base64.NO_WRAP);
        } catch (NoSuchAlgorithmException | InvalidKeySpecException e) {
            Log.e(TAG, "Error hashing password", e);
            return null;
        }
    }

    /**
     * Encrypts data using AES/GCM/NoPadding.
     * Requires a Base64 encoded key.
     * @param dataToEncrypt The string data to encrypt.
     * @param base64Key The Base64 encoded AES key.
     * @return Base64 encoded string of [IV + Ciphertext], or null on error.
     */
    public static String encrypt(String dataToEncrypt, String base64Key) {
        try {
            byte[] keyBytes = Base64.decode(base64Key, Base64.NO_WRAP);
            SecretKeySpec key = new SecretKeySpec(keyBytes, "AES");

            Cipher cipher = Cipher.getInstance(AES_ALGORITHM);
            byte[] iv = new byte[GCM_IV_LENGTH];
            SecureRandom random = new SecureRandom();
            random.nextBytes(iv);

            GCMParameterSpec gcmParameterSpec = new GCMParameterSpec(GCM_TAG_LENGTH, iv);
            cipher.init(Cipher.ENCRYPT_MODE, key, gcmParameterSpec);

            byte[] encryptedData = cipher.doFinal(dataToEncrypt.getBytes(StandardCharsets.UTF_8));

            // Combine IV and encrypted data for storage/transmission
            byte[] combinedIvAndCiphertext = new byte[iv.length + encryptedData.length];
            System.arraycopy(iv, 0, combinedIvAndCiphertext, 0, iv.length);
            System.arraycopy(encryptedData, 0, combinedIvAndCiphertext, iv.length, encryptedData.length);

            return Base64.encodeToString(combinedIvAndCiphertext, Base64.NO_WRAP);
        } catch (Exception e) {
            Log.e(TAG, "AES Encryption error", e);
            return null;
        }
    }

    /**
     * Decrypts data using AES/GCM/NoPadding.
     * Expects a Base64 encoded string of [IV + Ciphertext].
     * @param base64EncryptedData The Base64 encoded [IV + Ciphertext].
     * @param base64Key The Base64 encoded AES key.
     * @return The decrypted string, or null on error.
     */
    public static String decrypt(String base64EncryptedData, String base64Key) {
        try {
            byte[] keyBytes = Base64.decode(base64Key, Base64.NO_WRAP);
            SecretKeySpec key = new SecretKeySpec(keyBytes, "AES");

            byte[] combinedIvAndCiphertext = Base64.decode(base64EncryptedData, Base64.NO_WRAP);

            // Extract IV and Ciphertext
            byte[] iv = new byte[GCM_IV_LENGTH];
            byte[] ciphertext = new byte[combinedIvAndCiphertext.length - GCM_IV_LENGTH];
            System.arraycopy(combinedIvAndCiphertext, 0, iv, 0, iv.length);
            System.arraycopy(combinedIvAndCiphertext, iv.length, ciphertext, 0, ciphertext.length);

            Cipher cipher = Cipher.getInstance(AES_ALGORITHM);
            GCMParameterSpec gcmParameterSpec = new GCMParameterSpec(GCM_TAG_LENGTH, iv);
            cipher.init(Cipher.DECRYPT_MODE, key, gcmParameterSpec);

            byte[] decryptedData = cipher.doFinal(ciphertext);
            return new String(decryptedData, StandardCharsets.UTF_8);
        } catch (Exception e) {
            Log.e(TAG, "AES Decryption error", e);
            return null;
        }
    }

    // --- NEW METHODS FOR DIGITAL SIGNATURES ---

    /**
     * Generates an ECDSA KeyPair (Private and Public Key) and stores it in the Android Keystore.
     * The private key is hardware-backed if the device supports it.
     *
     * @param alias The alias under which to store the key pair in the Keystore.
     * This alias will be used to retrieve the keys later.
     * @return The generated KeyPair. The PrivateKey object is a handle to the key in Keystore.
     * Returns null if key generation fails.
     */
    public static KeyPair generateECKeyPairInKeystore(String alias) {
        try {
            KeyPairGenerator keyPairGenerator = KeyPairGenerator.getInstance(
                    EC_KEY_ALGORITHM, ANDROID_KEYSTORE_PROVIDER);

            KeyGenParameterSpec.Builder builder = new KeyGenParameterSpec.Builder(
                    alias,
                    KeyProperties.PURPOSE_SIGN | KeyProperties.PURPOSE_VERIFY) // Key can be used for signing and verification
                    .setAlgorithmParameterSpec(new ECGenParameterSpec(EC_CURVE_SPEC))
                    .setDigests(KeyProperties.DIGEST_SHA256) // Specify SHA-256 for the signature
                    // .setUserAuthenticationRequired(true) // Optional: Require user auth (e.g., fingerprint) to use the key
                    // .setUserAuthenticationValidityDurationSeconds(30) // If auth is required, how long it's valid
                    ;

            keyPairGenerator.initialize(builder.build());
            Log.i(TAG, "Generating EC KeyPair with alias: " + alias);
            return keyPairGenerator.generateKeyPair();
        } catch (Exception e) { // Catch a broader range of exceptions for Keystore operations
            Log.e(TAG, "Failed to generate EC KeyPair in Keystore for alias " + alias, e);
            return null;
        }
    }

    /**
     * Retrieves the PrivateKey from the Android Keystore.
     *
     * @param alias The alias of the key pair.
     * @return The PrivateKey, or null if the alias doesn't exist or it's not a private key.
     */
    public static PrivateKey getPrivateKeyFromKeystore(String alias) {
        try {
            KeyStore keyStore = KeyStore.getInstance(ANDROID_KEYSTORE_PROVIDER);
            keyStore.load(null); // Load the Keystore (no password needed for AndroidKeyStore)

            KeyStore.Entry entry = keyStore.getEntry(alias, null);
            if (entry == null) {
                Log.w(TAG, "No key found under alias: " + alias);
                return null;
            }
            if (!(entry instanceof KeyStore.PrivateKeyEntry)) {
                Log.w(TAG, "Not a private key entry under alias: " + alias);
                return null;
            }
            Log.i(TAG, "Retrieved PrivateKey for alias: " + alias);
            return ((KeyStore.PrivateKeyEntry) entry).getPrivateKey();
        } catch (Exception e) {
            Log.e(TAG, "Failed to retrieve PrivateKey from Keystore for alias " + alias, e);
            return null;
        }
    }

    /**
     * Retrieves the PublicKey from the Android Keystore (from the key pair's certificate).
     *
     * @param alias The alias of the key pair.
     * @return The PublicKey, or null if the alias doesn't exist or there's no certificate.
     */
    public static PublicKey getPublicKeyFromKeystore(String alias) {
        try {
            KeyStore keyStore = KeyStore.getInstance(ANDROID_KEYSTORE_PROVIDER);
            keyStore.load(null);

            KeyStore.Entry entry = keyStore.getEntry(alias, null);
            if (entry == null) {
                Log.w(TAG, "No key found under alias (for public key): " + alias);
                return null;
            }
            // For public key, we typically get it from the certificate associated with the private key entry
            if (keyStore.getCertificate(alias) == null) {
                 Log.w(TAG, "No certificate found for alias (for public key): " + alias);
                return null;
            }
            Log.i(TAG, "Retrieved PublicKey for alias: " + alias);
            return keyStore.getCertificate(alias).getPublicKey();
        } catch (Exception e) {
            Log.e(TAG, "Failed to retrieve PublicKey from Keystore for alias " + alias, e);
            return null;
        }
    }

    /**
     * Signs data using the provided PrivateKey (typically retrieved from Keystore).
     * Uses SHA256withECDSA algorithm.
     *
     * @param dataToSign The string data to be signed.
     * @param privateKey The PrivateKey to use for signing.
     * @return Base64 URL-safe encoded signature string, or null on error.
     */
    public static String signData(String dataToSign, PrivateKey privateKey) {
        if (privateKey == null || dataToSign == null) {
            Log.e(TAG, "Private key or data to sign is null.");
            return null;
        }
        try {
            Signature signature = Signature.getInstance(SIGNATURE_ALGORITHM);
            signature.initSign(privateKey);
            signature.update(dataToSign.getBytes(StandardCharsets.UTF_8));
            byte[] signatureBytes = signature.sign();
            // URL_SAFE is good for QR codes and web transmission. NO_WRAP avoids newlines.
            return Base64.encodeToString(signatureBytes, Base64.URL_SAFE | Base64.NO_WRAP);
        } catch (Exception e) {
            Log.e(TAG, "Error signing data", e);
            return null;
        }
    }

    /**
     * Verifies a signature against the original data using the PublicKey.
     * Uses SHA256withECDSA algorithm.
     *
     * @param originalData The original, unsigned data string (must be identical to what was signed).
     * @param signatureBase64 The Base64 URL-safe encoded signature string to verify.
     * @param publicKey The PublicKey to use for verification.
     * @return True if the signature is valid and matches the data, false otherwise.
     */
    public static boolean verifySignature(String originalData, String signatureBase64, PublicKey publicKey) {
        if (publicKey == null || originalData == null || signatureBase64 == null) {
            Log.e(TAG, "Public key, original data, or signature is null for verification.");
            return false;
        }
        try {
            Signature signature = Signature.getInstance(SIGNATURE_ALGORITHM);
            signature.initVerify(publicKey);
            signature.update(originalData.getBytes(StandardCharsets.UTF_8));
            byte[] signatureBytes = Base64.decode(signatureBase64, Base64.URL_SAFE); // Use URL_SAFE for decoding
            return signature.verify(signatureBytes);
        } catch (Exception e) {
            // This can happen for various reasons: malformed signature, wrong key, etc.
            Log.e(TAG, "Error verifying signature", e);
            return false;
        }
    }

    /**
     * Encodes a PublicKey to a Base64 string (X.509 format).
     * Useful for storing or transmitting the public key.
     * @param publicKey The PublicKey to encode.
     * @return Base64 encoded string of the public key, or null if input is null.
     */
    public static String encodePublicKey(PublicKey publicKey) {
        if (publicKey == null) return null;
        return Base64.encodeToString(publicKey.getEncoded(), Base64.NO_WRAP);
    }

    /**
     * Decodes a Base64 string (expected to be X.509 format) back into a PublicKey object.
     * @param encodedPublicKey Base64 encoded public key string.
     * @return PublicKey object, or null on error (e.g., invalid format, wrong algorithm).
     */
    public static PublicKey decodePublicKey(String encodedPublicKey) {
        if (encodedPublicKey == null) return null;
        try {
            byte[] publicKeyBytes = Base64.decode(encodedPublicKey, Base64.NO_WRAP);
            KeyFactory keyFactory = KeyFactory.getInstance("EC"); // IMPORTANT: Must match the key algorithm (EC for ECDSA)
            return keyFactory.generatePublic(new X509EncodedKeySpec(publicKeyBytes));
        } catch (NoSuchAlgorithmException | InvalidKeySpecException e) {
            Log.e(TAG, "Error decoding public key string", e);
            return null;
        } catch (IllegalArgumentException e) {
            Log.e(TAG, "Error decoding public key string - likely malformed Base64", e);
            return null;
        }
    }
}

