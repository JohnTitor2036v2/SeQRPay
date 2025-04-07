package com.example.seqrpay;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.util.Base64;
import android.util.Log; // Added
import android.util.Pair;

import java.util.ArrayList; // Needed for getTransactions
import java.util.List;      // Needed for getTransactions

public class DatabaseHelper extends SQLiteOpenHelper {
    private static final String TAG = "DatabaseHelper"; // Added
    private static final String DATABASE_NAME = "secure_payment.db";
    private static final int DATABASE_VERSION = 2; // Assuming version 2 from previous security update

    // --- Singleton Instance ---
    private static DatabaseHelper instance = null; // <<<=== ADDED

    // User table
    private static final String TABLE_USERS = "users";
    private static final String COLUMN_ID = "id";
    private static final String COLUMN_USERNAME = "username";
    private static final String COLUMN_PASSWORD_HASH = "password_hash";
    private static final String COLUMN_SALT = "salt";

    // --- ADD Transaction Table (Example) ---
    private static final String TABLE_TRANSACTIONS = "transactions";
    private static final String COLUMN_TX_ID = "tx_id";
    private static final String COLUMN_TX_USER_ID = "user_id"; // Foreign key to users table
    private static final String COLUMN_TX_DESCRIPTION = "description";
    private static final String COLUMN_TX_AMOUNT = "amount"; // Store as String "+/-X.XX" or use REAL type
    private static final String COLUMN_TX_DATE = "date";     // Store as TEXT (ISO8601) or INTEGER (Unix time)

    // Create table queries
    private static final String CREATE_TABLE_USERS =
            "CREATE TABLE " + TABLE_USERS + "("
                    + COLUMN_ID + " INTEGER PRIMARY KEY AUTOINCREMENT,"
                    + COLUMN_USERNAME + " TEXT UNIQUE,"
                    + COLUMN_PASSWORD_HASH + " TEXT,"
                    + COLUMN_SALT + " TEXT"
                    + ")";

    // <<<=== ADDED Transaction Table Creation ===>>>
    private static final String CREATE_TABLE_TRANSACTIONS =
            "CREATE TABLE " + TABLE_TRANSACTIONS + "("
                    + COLUMN_TX_ID + " INTEGER PRIMARY KEY AUTOINCREMENT,"
                    + COLUMN_TX_USER_ID + " INTEGER,"
                    + COLUMN_TX_DESCRIPTION + " TEXT,"
                    + COLUMN_TX_AMOUNT + " TEXT," // Or REAL
                    + COLUMN_TX_DATE + " TEXT,"    // Or INTEGER
                    + "FOREIGN KEY(" + COLUMN_TX_USER_ID + ") REFERENCES " + TABLE_USERS + "(" + COLUMN_ID + ")"
                    + ")";


    // --- Singleton getInstance method ---
    public static synchronized DatabaseHelper getInstance(Context context) { // <<<=== ADDED
        if (instance == null) {
            // Use application context to avoid memory leaks
            instance = new DatabaseHelper(context.getApplicationContext());
        }
        return instance;
    }

    // Make constructor private for singleton <<<=== MODIFIED
    private DatabaseHelper(Context context) {
        super(context, DATABASE_NAME, null, DATABASE_VERSION);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        Log.i(TAG, "Creating database tables..."); // Added Log
        db.execSQL(CREATE_TABLE_USERS);
        db.execSQL(CREATE_TABLE_TRANSACTIONS); // <<<=== ADDED
        Log.i(TAG, "Database tables created."); // Added Log
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        Log.w(TAG, "Upgrading database from version " + oldVersion + " to " + newVersion); // Added Log
        // Basic upgrade policy: drop and recreate. Implement proper migration for production.
        db.execSQL("DROP TABLE IF EXISTS " + TABLE_TRANSACTIONS); // <<<=== ADDED
        db.execSQL("DROP TABLE IF EXISTS " + TABLE_USERS);
        onCreate(db);
    }

    // Add a new user (remains synchronous, called from background thread)
    public long addUser(String username, String password) {
        SQLiteDatabase db = this.getWritableDatabase();
        ContentValues values = new ContentValues();
        long result = -1; // Default to failure

        byte[] salt = SecurityUtils.generateSalt();
        String hashedPassword = SecurityUtils.hashPassword(password, salt);

        if (hashedPassword != null && salt != null) {
            values.put(COLUMN_USERNAME, username);
            values.put(COLUMN_PASSWORD_HASH, hashedPassword);
            values.put(COLUMN_SALT, Base64.encodeToString(salt, Base64.NO_WRAP));
            try {
                result = db.insertOrThrow(TABLE_USERS, null, values); // Use insertOrThrow for better error info
            } catch (android.database.sqlite.SQLiteConstraintException e) {
                Log.w(TAG, "Constraint violation adding user (likely duplicate username): " + username);
                result = -2; // Indicate duplicate username / constraint violation
            } catch (Exception e) {
                Log.e(TAG, "Error adding user: " + username, e);
                result = -1; // General error
            }
        } else {
            Log.e(TAG, "Failed to hash password for user: " + username);
        }
        // db.close(); // Don't close if using singleton instance managed elsewhere
        return result;
    }

    // Get salt and hash (remains synchronous)
    public Pair<String, String> getUserSaltAndHash(String username) {
        SQLiteDatabase db = this.getReadableDatabase();
        String[] columns = {COLUMN_SALT, COLUMN_PASSWORD_HASH};
        String selection = COLUMN_USERNAME + " = ?";
        String[] selectionArgs = {username};
        Pair<String, String> saltAndHash = null;
        Cursor cursor = null; // Initialize cursor

        try { // Add try-finally for cursor
            cursor = db.query(
                    TABLE_USERS, columns, selection, selectionArgs, null, null, null
            );

            if (cursor != null && cursor.moveToFirst()) {
                int saltIndex = cursor.getColumnIndex(COLUMN_SALT);
                int hashIndex = cursor.getColumnIndex(COLUMN_PASSWORD_HASH);
                if (saltIndex != -1 && hashIndex != -1) {
                    String saltStr = cursor.getString(saltIndex);
                    String hashStr = cursor.getString(hashIndex);
                    if (saltStr != null && hashStr != null) {
                        saltAndHash = new Pair<>(saltStr, hashStr);
                    }
                } else {
                    Log.e(TAG, "Column index not found for salt or hash.");
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Error getting salt/hash for user: " + username, e);
        } finally {
            if (cursor != null) {
                cursor.close();
            }
            // db.close(); // Don't close if using singleton
        }
        return saltAndHash;
    }

    // Check user (remains synchronous)
    public boolean checkUser(String username, String password) {
        Pair<String, String> saltAndHash = getUserSaltAndHash(username);

        if (saltAndHash == null || saltAndHash.first == null || saltAndHash.second == null) {
            return false;
        }
        String storedSaltStr = saltAndHash.first;
        String storedHashStr = saltAndHash.second;
        byte[] salt = Base64.decode(storedSaltStr, Base64.NO_WRAP);
        String providedHashStr = SecurityUtils.hashPassword(password, salt);

        return storedHashStr.equals(providedHashStr);
    }

    // --- Example: Add Transaction Method (synchronous) ---
    // This needs user ID - you'd fetch this after login and pass it around or store in session
    public boolean addTransaction(long userId, String description, String amount, String date) {
        SQLiteDatabase db = this.getWritableDatabase();
        ContentValues values = new ContentValues();
        values.put(COLUMN_TX_USER_ID, userId);
        values.put(COLUMN_TX_DESCRIPTION, description);
        values.put(COLUMN_TX_AMOUNT, amount);
        values.put(COLUMN_TX_DATE, date); // Use a consistent date format (e.g., ISO8601 YYYY-MM-DD HH:MM:SS)

        long result = -1;
        try {
            result = db.insertOrThrow(TABLE_TRANSACTIONS, null, values);
        } catch (Exception e) {
            Log.e(TAG, "Error adding transaction for user " + userId, e);
        }
        // db.close();
        return result != -1;
    }

    // --- Example: Get Transactions Method (synchronous) ---
    // Again, needs user ID
    public List<Transaction> getTransactions(long userId) {
        List<Transaction> transactions = new ArrayList<>();
        SQLiteDatabase db = this.getReadableDatabase();
        Cursor cursor = null;
        String[] columns = {COLUMN_TX_DESCRIPTION, COLUMN_TX_AMOUNT, COLUMN_TX_DATE};
        String selection = COLUMN_TX_USER_ID + " = ?";
        String[] selectionArgs = {String.valueOf(userId)};
        String orderBy = COLUMN_TX_DATE + " DESC"; // Order by date descending

        try {
            cursor = db.query(TABLE_TRANSACTIONS, columns, selection, selectionArgs, null, null, orderBy);
            if (cursor != null && cursor.moveToFirst()) {
                int descIndex = cursor.getColumnIndex(COLUMN_TX_DESCRIPTION);
                int amountIndex = cursor.getColumnIndex(COLUMN_TX_AMOUNT);
                int dateIndex = cursor.getColumnIndex(COLUMN_TX_DATE);

                if (descIndex == -1 || amountIndex == -1 || dateIndex == -1) {
                    Log.e(TAG, "Transaction column index error.");
                    return transactions; // Return empty list
                }

                do {
                    String description = cursor.getString(descIndex);
                    String amount = cursor.getString(amountIndex);
                    String date = cursor.getString(dateIndex);
                    transactions.add(new Transaction(description, amount, date));
                } while (cursor.moveToNext());
            }
        } catch (Exception e) {
            Log.e(TAG, "Error fetching transactions for user " + userId, e);
        } finally {
            if (cursor != null) {
                cursor.close();
            }
            // db.close();
        }
        return transactions;
    }

    // --- Add methods for getting/setting balance if needed ---

}