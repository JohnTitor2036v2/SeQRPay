package com.example.seqrpay;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import java.security.KeyPair;
import java.security.PrivateKey;
import java.security.PublicKey;

/**
 * Manages user-specific ECDSA key pairs for digital signatures.
 * - Ensures a key pair exists for a user.
 * - Stores the public key in SharedPreferences for local retrieval.
 * - Private keys are managed by and stored in the Android Keystore via SecurityUtils.
 */
public class UserKeyPairManager {

    private static final String TAG = "UserKeyPairManager";
    private static final String PREFS_NAME = "UserKeyPairPrefs";
    // Prefix for SharedPreferences key to store the user's encoded public key.
    private static final String PUBLIC_KEY_PREF_PREFIX = "user_public_key_";
    // Prefix for the alias used in Android Keystore for the user's key pair.
    public static final String KEYSTORE_ALIAS_PREFIX = "seqrpay_user_key_"; // Made public for potential external use/reference

    /**
     * Ensures that an ECDSA key pair exists for the given username.
     * If a key pair does not exist (either public key not in SharedPreferences or private/public key not in Keystore),
     * a new one is generated using SecurityUtils and stored.
     * The public key is stored in SharedPreferences (Base64 encoded).
     * The private key is stored in the Android Keystore.
     *
     * @param context The application context.
     * @param username The username for whom to ensure the key pair exists.
     */
    public static void ensureKeyPairExists(Context context, String username) {
        if (username == null || username.isEmpty()) {
            Log.e(TAG, "Username cannot be null or empty for key pair management.");
            return;
        }

        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        String prefKeyForPublicKey = PUBLIC_KEY_PREF_PREFIX + username;
        String keystoreAlias = KEYSTORE_ALIAS_PREFIX + username;

        boolean needsGeneration = false;
        if (!prefs.contains(prefKeyForPublicKey)) {
            Log.i(TAG, "Public key not found in SharedPreferences for user: " + username);
            needsGeneration = true;
        } else {
            // Check if Keystore still has the keys, SharedPreferences might be out of sync or keys were cleared
            try {
                if (SecurityUtils.getPrivateKeyFromKeystore(keystoreAlias) == null ||
                    SecurityUtils.getPublicKeyFromKeystore(keystoreAlias) == null) {
                    Log.w(TAG, "Key not found in Keystore despite being in SharedPreferences for user: " + username + ". Regenerating.");
                    needsGeneration = true;
                }
            } catch (Exception e) {
                Log.e(TAG, "Error checking Keystore for user " + username + ", assuming regeneration is needed.", e);
                needsGeneration = true;
            }
        }

        if (needsGeneration) {
            Log.i(TAG, "Attempting to generate new key pair for user: " + username);
            try {
                KeyPair keyPair = SecurityUtils.generateECKeyPairInKeystore(keystoreAlias);
                if (keyPair != null && keyPair.getPublic() != null) {
                    String encodedPublicKey = SecurityUtils.encodePublicKey(keyPair.getPublic());
                    if (encodedPublicKey != null) {
                        prefs.edit().putString(prefKeyForPublicKey, encodedPublicKey).apply();
                        Log.i(TAG, "Successfully generated and stored new key pair for user: " + username);
                    } else {
                        Log.e(TAG, "Failed to encode public key for user: " + username);
                    }
                } else {
                    Log.e(TAG, "Generated KeyPair or PublicKey is null for user: " + username);
                }
            } catch (Exception e) {
                Log.e(TAG, "Critical error generating/storing key pair for user: " + username, e);
                // Depending on the app's requirements, you might want to throw a custom exception
                // or have a more robust error handling mechanism here.
            }
        } else {
            Log.d(TAG, "Key pair already exists for user: " + username);
        }
    }

    /**
     * Retrieves the user's PrivateKey from the Android Keystore.
     *
     * @param username The username whose private key is to be retrieved.
     * @return The PrivateKey, or null if not found or an error occurs.
     */
    public static PrivateKey getUserPrivateKey(String username) {
        if (username == null || username.isEmpty()) {
            Log.e(TAG, "Username cannot be null or empty for retrieving private key.");
            return null;
        }
        String keystoreAlias = KEYSTORE_ALIAS_PREFIX + username;
        try {
            return SecurityUtils.getPrivateKeyFromKeystore(keystoreAlias);
        } catch (Exception e) {
            Log.e(TAG, "Error retrieving private key from Keystore for user: " + username, e);
            return null;
        }
    }

    /**
     * Retrieves the user's PublicKey.
     * It first tries to get the Base64 encoded public key from SharedPreferences and decode it.
     * If not found or if decoding fails, it attempts to retrieve the PublicKey directly from the Keystore as a fallback.
     *
     * @param context The application context.
     * @param username The username whose public key is to be retrieved.
     * @return The PublicKey, or null if not found or an error occurs.
     */
    public static PublicKey getUserPublicKey(Context context, String username) {
        if (username == null || username.isEmpty()) {
            Log.e(TAG, "Username cannot be null or empty for retrieving public key.");
            return null;
        }

        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        String prefKeyForPublicKey = PUBLIC_KEY_PREF_PREFIX + username;
        String encodedPublicKeyFromPrefs = prefs.getString(prefKeyForPublicKey, null);

        if (encodedPublicKeyFromPrefs != null) {
            try {
                PublicKey publicKey = SecurityUtils.decodePublicKey(encodedPublicKeyFromPrefs);
                if (publicKey != null) {
                    Log.d(TAG, "Retrieved public key from SharedPreferences for user: " + username);
                    return publicKey;
                } else {
                    Log.w(TAG, "Failed to decode public key from SharedPreferences for user: " + username + ". Attempting Keystore fallback.");
                }
            } catch (Exception e) {
                Log.e(TAG, "Error decoding public key from SharedPreferences for user: " + username, e);
                // Fall through to Keystore retrieval
            }
        } else {
             Log.d(TAG, "Public key not found in SharedPreferences for user: " + username + ". Attempting Keystore fallback.");
        }

        // Fallback: Try to get directly from Keystore
        String keystoreAlias = KEYSTORE_ALIAS_PREFIX + username;
        try {
            PublicKey publicKeyFromKeystore = SecurityUtils.getPublicKeyFromKeystore(keystoreAlias);
            if (publicKeyFromKeystore != null) {
                Log.i(TAG, "Retrieved public key directly from Keystore for user: " + username);
                // Optionally, update SharedPreferences if it was missing or failed to decode
                String encodedFromKeystore = SecurityUtils.encodePublicKey(publicKeyFromKeystore);
                if (encodedFromKeystore != null && (encodedPublicKeyFromPrefs == null || !encodedFromKeystore.equals(encodedPublicKeyFromPrefs))) {
                    prefs.edit().putString(prefKeyForPublicKey, encodedFromKeystore).apply();
                    Log.i(TAG, "Updated SharedPreferences with public key from Keystore for user: " + username);
                }
                return publicKeyFromKeystore;
            }
        } catch (Exception e) {
            Log.e(TAG, "Error retrieving public key from Keystore for user: " + username, e);
        }

        Log.e(TAG, "Failed to retrieve public key for user: " + username + " from all sources.");
        return null;
    }

    /**
     * Retrieves the public key for a given payee username.
     *
     * IMPORTANT: In a real-world application, this method should securely fetch
     * the payee's public key from a trusted server/directory associated with their username.
     * Using local SharedPreferences or Keystore for an *arbitrary* payeeUsername
     * is insecure as it doesn't verify the true owner of that username.
     *
     * For this local example, it will try to fetch as if the payee is a local user.
     * This is a simplification and NOT for production use with untrusted payees.
     *
     * @param context The application context.
     * @param payeeUsername The username of the payee.
     * @return The PublicKey of the payee, or null if not found or an error occurs.
     */
    public static PublicKey getPublicKeyForPayee(Context context, String payeeUsername) {
        Log.w(TAG, "Fetching public key for payee '" + payeeUsername + "' using local retrieval. " +
                   "REMINDER: This is a placeholder and needs a secure server lookup in production.");
        // This is a simplified approach for local testing.
        // In a real app, you would query your backend server for the payee's public key.
        return getUserPublicKey(context, payeeUsername);
    }
}
