package com.example.seqrpay;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

public class DatabaseHelper extends SQLiteOpenHelper {
    private static final String DATABASE_NAME = "secure_payment.db";
    private static final int DATABASE_VERSION = 1;

    // User table
    private static final String TABLE_USERS = "users";
    private static final String COLUMN_ID = "id";
    private static final String COLUMN_USERNAME = "username";
    private static final String COLUMN_PASSWORD = "password";
    private static final String COLUMN_ENCRYPTED_KEY = "encrypted_key";

    // Create table query
    private static final String CREATE_TABLE_USERS =
            "CREATE TABLE " + TABLE_USERS + "("
                    + COLUMN_ID + " INTEGER PRIMARY KEY AUTOINCREMENT,"
                    + COLUMN_USERNAME + " TEXT UNIQUE,"
                    + COLUMN_PASSWORD + " TEXT,"
                    + COLUMN_ENCRYPTED_KEY + " TEXT"
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
        db.execSQL("DROP TABLE IF EXISTS " + TABLE_USERS);
        onCreate(db);
    }

    // Add a new user
    public long addUser(String username, String hashedPassword) {
        SQLiteDatabase db = this.getWritableDatabase();
        ContentValues values = new ContentValues();

        // Generate a random encryption key for this user
        String encryptionKey = SecurityUtils.generateEncryptionKey();
        // Encrypt the key with the user's password (in a real app, you'd use a more secure method)
        String encryptedKey = SecurityUtils.encryptKey(encryptionKey, hashedPassword);

        values.put(COLUMN_USERNAME, username);
        values.put(COLUMN_PASSWORD, hashedPassword);
        values.put(COLUMN_ENCRYPTED_KEY, encryptedKey);

        long result = db.insert(TABLE_USERS, null, values);
        db.close();
        return result;
    }

    // Check user login
    public boolean checkUser(String username, String hashedPassword) {
        SQLiteDatabase db = this.getReadableDatabase();
        String[] columns = {COLUMN_ID};
        String selection = COLUMN_USERNAME + " = ? AND " + COLUMN_PASSWORD + " = ?";
        String[] selectionArgs = {username, hashedPassword};

        Cursor cursor = db.query(
                TABLE_USERS,
                columns,
                selection,
                selectionArgs,
                null,
                null,
                null
        );

        int count = cursor.getCount();
        cursor.close();
        db.close();

        return count > 0;
    }

    // Get user's encryption key
    public String getUserEncryptionKey(String username, String hashedPassword) {
        SQLiteDatabase db = this.getReadableDatabase();
        String[] columns = {COLUMN_ENCRYPTED_KEY};
        String selection = COLUMN_USERNAME + " = ? AND " + COLUMN_PASSWORD + " = ?";
        String[] selectionArgs = {username, hashedPassword};

        Cursor cursor = db.query(
                TABLE_USERS,
                columns,
                selection,
                selectionArgs,
                null,
                null,
                null
        );

        String encryptedKey = null;
        if (cursor.moveToFirst()) {
            encryptedKey = cursor.getString(cursor.getColumnIndex(COLUMN_ENCRYPTED_KEY));
        }

        cursor.close();
        db.close();

        if (encryptedKey != null) {
            // Decrypt the key with the user's password
            return SecurityUtils.decryptKey(encryptedKey, hashedPassword);
        }

        return null;
    }
}
