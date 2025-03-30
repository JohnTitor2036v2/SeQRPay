package com.example.seqrpay;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.util.Base64;
import android.util.Pair; // Required for returning salt+hash pair

public class DatabaseHelper extends SQLiteOpenHelper {
    private static final String DATABASE_NAME = "secure_payment.db";
    // Increment version when schema changes
    private static final int DATABASE_VERSION = 2; // <<<=== INCREMENTED VERSION

    // User table
    private static final String TABLE_USERS = "users";
    private static final String COLUMN_ID = "id";
    private static final String COLUMN_USERNAME = "username";
    private static final String COLUMN_PASSWORD_HASH = "password_hash"; // Renamed
    private static final String COLUMN_SALT = "salt"; // <<<=== ADDED SALT COLUMN
    // private static final String COLUMN_ENCRYPTED_KEY = "encrypted_key"; // <<<=== REMOVED

    // Create table query - updated
    private static final String CREATE_TABLE_USERS =
            "CREATE TABLE " + TABLE_USERS + "("
                    + COLUMN_ID + " INTEGER PRIMARY KEY AUTOINCREMENT,"
                    + COLUMN_USERNAME + " TEXT UNIQUE,"
                    + COLUMN_PASSWORD_HASH + " TEXT," // Renamed
                    + COLUMN_SALT + " TEXT"          // <<<=== ADDED
                    // + COLUMN_ENCRYPTED_KEY + " TEXT" // <<<=== REMOVED
                    + ")";

    public DatabaseHelper(Context context) {
        super(context, DATABASE_NAME, null, DATABASE_VERSION);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        db.execSQL(CREATE_TABLE_USERS);
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        // Basic upgrade policy: drop and recreate.
        // In a real app, you'd implement proper migration logic.
        if (oldVersion < 2) {
            // Handle migration from V1 (no salt) if needed, or just drop
            db.execSQL("DROP TABLE IF EXISTS " + TABLE_USERS);
            onCreate(db);
        }
        // Add more 'if (oldVersion < X)' blocks for future upgrades
    }

    // Add a new user with salt and PBKDF2 hash
    public long addUser(String username, String password) {
        SQLiteDatabase db = this.getWritableDatabase();
        ContentValues values = new ContentValues();

        byte[] salt = SecurityUtils.generateSalt();
        String hashedPassword = SecurityUtils.hashPassword(password, salt);

        // Check if hashing failed
        if (hashedPassword == null || salt == null) {
            db.close();
            return -1; // Indicate failure
        }

        values.put(COLUMN_USERNAME, username);
        values.put(COLUMN_PASSWORD_HASH, hashedPassword);
        values.put(COLUMN_SALT, Base64.encodeToString(salt, Base64.NO_WRAP));

        long result = db.insert(TABLE_USERS, null, values);
        db.close();
        return result;
    }

    // Get salt and hash for a user
    public Pair<String, String> getUserSaltAndHash(String username) {
        SQLiteDatabase db = this.getReadableDatabase();
        String[] columns = {COLUMN_SALT, COLUMN_PASSWORD_HASH};
        String selection = COLUMN_USERNAME + " = ?";
        String[] selectionArgs = {username};
        Pair<String, String> saltAndHash = null;

        Cursor cursor = db.query(
                TABLE_USERS,
                columns,
                selection,
                selectionArgs,
                null,
                null,
                null
        );

        if (cursor.moveToFirst()) {
            // Ensure getColumnIndexOrThrow is used or check for -1
            int saltIndex = cursor.getColumnIndex(COLUMN_SALT);
            int hashIndex = cursor.getColumnIndex(COLUMN_PASSWORD_HASH);
            if (saltIndex != -1 && hashIndex != -1) {
                String saltStr = cursor.getString(saltIndex);
                String hashStr = cursor.getString(hashIndex);
                if (saltStr != null && hashStr != null) {
                    saltAndHash = new Pair<>(saltStr, hashStr);
                }
            }
        }

        cursor.close();
        db.close();
        return saltAndHash;
    }


    // Check user login - Now verifies against stored salt and hash
    public boolean checkUser(String username, String password) {
        Pair<String, String> saltAndHash = getUserSaltAndHash(username);

        if (saltAndHash == null || saltAndHash.first == null || saltAndHash.second == null) {
            return false; // User not found or data missing
        }

        String storedSaltStr = saltAndHash.first;
        String storedHashStr = saltAndHash.second;

        byte[] salt = Base64.decode(storedSaltStr, Base64.NO_WRAP);

        // Hash the provided password with the stored salt
        String providedHashStr = SecurityUtils.hashPassword(password, salt);

        // Compare the newly generated hash with the stored hash
        // Use constant-time comparison if possible for extra security, but simple equals is okay here.
        return storedHashStr.equals(providedHashStr);
    }


    // --- REMOVED Insecure/Unused Methods ---
    // public String getUserEncryptionKey(String username, String hashedPassword) { ... } // <<<=== REMOVED
}