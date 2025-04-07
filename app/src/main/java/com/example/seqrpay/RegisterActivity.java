package com.example.seqrpay;

import androidx.appcompat.app.AppCompatActivity;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ProgressBar; // <<<=== ADDED
import android.widget.Toast;

public class RegisterActivity extends AppCompatActivity {
    private EditText etUsername, etPassword, etConfirmPassword;
    private Button btnRegister;
    private ProgressBar progressBar; // <<<=== ADDED
    private DatabaseHelper dbHelper;
    private AppExecutors appExecutors; // <<<=== ADDED

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_register);

        etUsername = findViewById(R.id.et_username);
        etPassword = findViewById(R.id.et_password);
        etConfirmPassword = findViewById(R.id.et_confirm_password);
        btnRegister = findViewById(R.id.btn_register);
        // Assumes <ProgressBar android:id="@+id/register_progress" .../> added to activity_register.xml
        progressBar = findViewById(R.id.register_progress); // <<<=== ADDED (Replace with your ID)

        dbHelper = DatabaseHelper.getInstance(this); // <<<=== MODIFIED
        appExecutors = AppExecutors.getInstance(); // <<<=== ADDED

        btnRegister.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                String username = etUsername.getText().toString().trim();
                String password = etPassword.getText().toString().trim();
                String confirmPassword = etConfirmPassword.getText().toString().trim();

                if (username.isEmpty() || password.isEmpty() || confirmPassword.isEmpty()) {
                    Toast.makeText(RegisterActivity.this, "Please fill all fields", Toast.LENGTH_SHORT).show();
                    return;
                }
                // Add more validation (e.g., username format, password strength) here

                if (!password.equals(confirmPassword)) {
                    Toast.makeText(RegisterActivity.this, "Passwords do not match", Toast.LENGTH_SHORT).show();
                    return;
                }

                // Show progress, disable button <<<=== ADDED
                showLoading(true);

                // --- MODIFIED: Execute DB add in background ---
                appExecutors.diskIO().execute(() -> {
                    // Runs on background thread
                    final long result = dbHelper.addUser(username, password);

                    // Post result back to main thread <<<=== ADDED
                    appExecutors.mainThread().execute(() -> {
                        // Runs on main thread
                        showLoading(false); // Hide progress
                        if (result > 0) {
                            Toast.makeText(RegisterActivity.this, "Registration successful", Toast.LENGTH_SHORT).show();
                            startActivity(new Intent(RegisterActivity.this, LoginActivity.class));
                            finish();
                        } else if (result == -2) { // Specific code for duplicate username
                            Toast.makeText(RegisterActivity.this, "Registration failed: Username already exists", Toast.LENGTH_SHORT).show();
                        } else { // General failure (result == -1 or other)
                            Toast.makeText(RegisterActivity.this, "Registration failed (Error code: " + result + ")", Toast.LENGTH_SHORT).show();
                        }
                    });
                });
            }
        });
    }

    // Helper method to show/hide progress bar and enable/disable button <<<=== ADDED
    private void showLoading(boolean isLoading) {
        if (isLoading) {
            progressBar.setVisibility(View.VISIBLE);
            btnRegister.setEnabled(false);
        } else {
            progressBar.setVisibility(View.GONE);
            btnRegister.setEnabled(true);
        }
    }
}