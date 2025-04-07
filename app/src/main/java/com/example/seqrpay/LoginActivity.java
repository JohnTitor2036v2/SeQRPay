package com.example.seqrpay;

import androidx.appcompat.app.AppCompatActivity;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ProgressBar; // <<<=== ADDED
import android.widget.Toast;

public class LoginActivity extends AppCompatActivity {
    private EditText etUsername, etPassword;
    private Button btnLogin;
    private ProgressBar progressBar; // <<<=== ADDED
    private DatabaseHelper dbHelper;
    private AppExecutors appExecutors; // <<<=== ADDED

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_login);

        etUsername = findViewById(R.id.et_username);
        etPassword = findViewById(R.id.et_password);
        btnLogin = findViewById(R.id.btn_login);
        // Initialize ProgressBar - assumes you add <ProgressBar android:id="@+id/login_progress" .../> to activity_login.xml
        progressBar = findViewById(R.id.login_progress); // <<<=== ADDED (Replace with your actual ID)

        // Get singleton instance of DatabaseHelper <<<=== MODIFIED
        dbHelper = DatabaseHelper.getInstance(this);
        // Get AppExecutors instance <<<=== ADDED
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

                // Show progress bar, disable button <<<=== ADDED
                showLoading(true);

                // --- MODIFIED: Execute DB check in background ---
                appExecutors.diskIO().execute(() -> {
                    // This runs on a background thread
                    final boolean isValidUser = dbHelper.checkUser(username, password);

                    // Post result back to main thread <<<=== ADDED
                    appExecutors.mainThread().execute(() -> {
                        // This runs on the main thread
                        showLoading(false); // Hide progress bar
                        if (isValidUser) {
                            // --- TODO: Fetch user ID after successful login for session ---
                            // long userId = dbHelper.getUserId(username); // Example - needs implementation
                            // Store userId/username in SharedPreferences or pass via Intent

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

    // Helper method to show/hide progress bar and enable/disable login button <<<=== ADDED
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