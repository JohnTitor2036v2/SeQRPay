package com.example.seqrpay;

import androidx.appcompat.app.AppCompatActivity;

import android.content.Intent;
import android.content.SharedPreferences; // <<<=== ADD IMPORT
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.Toast;

public class LoginActivity extends AppCompatActivity {
    private EditText etUsername, etPassword;
    private Button btnLogin;
    private ProgressBar progressBar;
    private DatabaseHelper dbHelper;
    private AppExecutors appExecutors;

    // Constants for SharedPreferences
    public static final String SHARED_PREFS_NAME = "SeQRPayPrefs"; // <<<=== ADD CONSTANT
    public static final String KEY_LOGGED_IN_USERNAME = "loggedInUsername"; // <<<=== ADD CONSTANT


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_login);

        etUsername = findViewById(R.id.et_username);
        etPassword = findViewById(R.id.et_password);
        btnLogin = findViewById(R.id.btn_login);
        progressBar = findViewById(R.id.login_progress);

        dbHelper = DatabaseHelper.getInstance(this);
        appExecutors = AppExecutors.getInstance();


        btnLogin.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                String username = etUsername.getText().toString().trim();
                String password = etPassword.getText().toString().trim();

                if (username.isEmpty() || password.isEmpty()) {
                    Toast.makeText(LoginActivity.this, "Please fill all fields", Toast.LENGTH_SHORT).show();
                    return;
                }
                showLoading(true);

                appExecutors.diskIO().execute(() -> {
                    final boolean isValidUser = dbHelper.checkUser(username, password);
                    appExecutors.mainThread().execute(() -> {
                        showLoading(false);
                        if (isValidUser) {
                            // --- SAVE USERNAME TO SharedPreferences ---
                            SharedPreferences prefs = getSharedPreferences(SHARED_PREFS_NAME, MODE_PRIVATE); // <<<=== ADDED
                            SharedPreferences.Editor editor = prefs.edit(); // <<<=== ADDED
                            editor.putString(KEY_LOGGED_IN_USERNAME, username); // <<<=== ADDED
                            editor.apply(); // <<<=== ADDED

                            Toast.makeText(LoginActivity.this, "Login Successful", Toast.LENGTH_SHORT).show(); // Added for feedback
                            startActivity(new Intent(LoginActivity.this, DashboardActivity.class));
                            finish();
                        } else {
                            Toast.makeText(LoginActivity.this, "Invalid credentials", Toast.LENGTH_SHORT).show();
                        }
                    });
                });
            }
        });
    }

    private void showLoading(boolean isLoading) {
        if (isLoading) {
            progressBar.setVisibility(View.VISIBLE);
            btnLogin.setEnabled(false);
        } else {
            progressBar.setVisibility(View.GONE);
            btnLogin.setEnabled(true);
        }
    }
}

