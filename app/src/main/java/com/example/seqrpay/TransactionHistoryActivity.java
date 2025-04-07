package com.example.seqrpay;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.ProgressBar; // <<<=== ADDED
import android.widget.Toast;    // <<<=== ADDED

import java.util.ArrayList;
import java.util.List;

public class TransactionHistoryActivity extends AppCompatActivity {
    private RecyclerView recyclerView;
    private Button btnBack;
    private ProgressBar progressBar; // <<<=== ADDED
    private TransactionAdapter adapter;
    private DatabaseHelper dbHelper; // <<<=== ADDED
    private AppExecutors appExecutors; // <<<=== ADDED
    private List<Transaction> transactionList = new ArrayList<>(); // <<<=== ADDED Instance variable

    // --- TODO: Get the logged-in user's ID (e.g., from Intent or SharedPreferences) ---
    private long currentUserId = 1; // <<<=== PLACEHOLDER - Replace with actual logged-in user ID

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_transaction_history);

        recyclerView = findViewById(R.id.recycler_view);
        btnBack = findViewById(R.id.btn_back);
        // Assumes <ProgressBar android:id="@+id/history_progress" .../> added
        progressBar = findViewById(R.id.history_progress); // <<<=== ADDED (Replace with your ID)

        dbHelper = DatabaseHelper.getInstance(this); // <<<=== ADDED
        appExecutors = AppExecutors.getInstance(); // <<<=== ADDED

        // Set up RecyclerView
        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        adapter = new TransactionAdapter(transactionList); // Use instance variable
        recyclerView.setAdapter(adapter);

        // Load transaction data from DB in background <<<=== MODIFIED
        loadTransactionData();

        btnBack.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                finish();
            }
        });
    }

    private void loadTransactionData() {
        showLoading(true); // <<<=== ADDED
        appExecutors.diskIO().execute(() -> {
            // Background thread
            final List<Transaction> fetchedTransactions = dbHelper.getTransactions(currentUserId);

            appExecutors.mainThread().execute(() -> {
                // Main thread
                showLoading(false);
                if (fetchedTransactions != null) {
                    transactionList.clear();
                    transactionList.addAll(fetchedTransactions);
                    adapter.notifyDataSetChanged(); // Update RecyclerView
                    if (transactionList.isEmpty()){
                        Toast.makeText(this,"No transactions found.", Toast.LENGTH_SHORT).show();
                    }
                } else {
                    // Handle error case
                    Toast.makeText(TransactionHistoryActivity.this, "Error loading transaction history", Toast.LENGTH_SHORT).show();
                }
            });
        });
    }

    // Helper method to show/hide progress bar <<<=== ADDED
    private void showLoading(boolean isLoading) {
        if (isLoading) {
            progressBar.setVisibility(View.VISIBLE);
            recyclerView.setVisibility(View.GONE); // Hide list while loading
        } else {
            progressBar.setVisibility(View.GONE);
            recyclerView.setVisibility(View.VISIBLE); // Show list again
        }
    }

    // --- REMOVED Dummy data method ---
    // private List<Transaction> getDummyTransactions() { ... }
}