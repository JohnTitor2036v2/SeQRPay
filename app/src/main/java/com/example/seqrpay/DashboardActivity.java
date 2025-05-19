package com.example.seqrpay;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager; // For RecyclerView
import androidx.recyclerview.widget.RecyclerView;

import android.content.Intent;
import android.content.SharedPreferences; // For getting logged-in user if needed for transactions
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList; // For dummy transactions
import java.util.List;     // For dummy transactions

public class DashboardActivity extends AppCompatActivity {
    private static final String TAG = "DashboardActivity";

    // UI Elements
    private Button btnScan, btnTransfer, btnHistory;
    private Button btnGenerateQrReceivePayment;
    private TextView tvBalance;
    private RecyclerView rvRecentTransactions;
    private TransactionAdapter transactionAdapter;
    private List<Transaction> recentTransactionsList;

    // Database and Executors
    private DatabaseHelper dbHelper;
    private AppExecutors appExecutors;
    private long currentUserId = -1; // To store the logged-in user's ID

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_dashboard);

        // Initialize UI Elements
        btnScan = findViewById(R.id.btn_scan);
        btnTransfer = findViewById(R.id.btn_transfer);
        btnHistory = findViewById(R.id.btn_history);
        btnGenerateQrReceivePayment = findViewById(R.id.btn_generate_qr_receive_payment);
        tvBalance = findViewById(R.id.tv_balance);
        rvRecentTransactions = findViewById(R.id.rv_recent_transactions);

        // Initialize Database and Executors
        dbHelper = DatabaseHelper.getInstance(this);
        appExecutors = AppExecutors.getInstance();

        // Retrieve logged-in user's username and then ID
        SharedPreferences prefs = getSharedPreferences(LoginActivity.SHARED_PREFS_NAME, MODE_PRIVATE);
        String currentUsername = prefs.getString(LoginActivity.KEY_LOGGED_IN_USERNAME, null);

        if (currentUsername != null) {
            // You'll need a method in DatabaseHelper to get user ID by username
            // For now, let's assume it exists or add a placeholder.
            // currentUserId = dbHelper.getUserIdByUsername(currentUsername); // Example
            Log.d(TAG, "Logged in as: " + currentUsername);
            // For testing, if getUserIdByUsername is not implemented, you might hardcode for now
            // or handle it. For simplicity, we'll proceed assuming you can get the ID.
            // If not, transaction loading might not work correctly.
        } else {
            Log.e(TAG, "No logged-in username found. Dashboard features might be limited.");
            Toast.makeText(this, "Error: User session not found.", Toast.LENGTH_LONG).show();
            // Optionally, redirect to login:
            // startActivity(new Intent(DashboardActivity.this, LoginActivity.class));
            // finish();
            // return;
        }


        // Set up RecyclerView for recent transactions
        recentTransactionsList = new ArrayList<>();
        transactionAdapter = new TransactionAdapter(recentTransactionsList);
        rvRecentTransactions.setLayoutManager(new LinearLayoutManager(this));
        rvRecentTransactions.setAdapter(transactionAdapter);

        // Load dashboard data (balance, recent transactions)
        loadDashboardData();

        // Set Click Listeners for buttons
        btnScan.setOnClickListener(v ->
                startActivity(new Intent(DashboardActivity.this, QRScannerActivity.class))
        );

        btnTransfer.setOnClickListener(v ->
                startActivity(new Intent(DashboardActivity.this, TransferActivity.class))
        );

        btnHistory.setOnClickListener(v ->
                startActivity(new Intent(DashboardActivity.this, TransactionHistoryActivity.class))
        );

        btnGenerateQrReceivePayment.setOnClickListener(v ->
                startActivity(new Intent(DashboardActivity.this, GenerateQrActivity.class))
        );
    }

    @Override
    protected void onResume() {
        super.onResume();
        // Refresh data when the activity resumes, e.g., after a transaction
        loadDashboardData();
    }

    private void loadDashboardData() {
        // TODO: Implement actual logic to load balance from DatabaseHelper
        // For now, using a placeholder.
        tvBalance.setText("Balance: $1,234.56"); // Placeholder

        // TODO: Implement logic to load recent transactions for currentUserId from DatabaseHelper
        // This should run on a background thread.
        // For now, using dummy data.
        loadDummyRecentTransactions();
    }

    private void loadDummyRecentTransactions() {
        // This is placeholder data. Replace with actual database query.
        appExecutors.diskIO().execute(() -> {
            // Simulate fetching a few recent transactions
            // In a real app, query dbHelper.getTransactions(currentUserId, limit: 5, offset: 0) or similar
            List<Transaction> dummyList = new ArrayList<>();
            dummyList.add(new Transaction("Payment Received", "+$50.00", "2025-05-20"));
            dummyList.add(new Transaction("Grocery Store", "-$25.50", "2025-05-19"));
            dummyList.add(new Transaction("Transfer to Bob", "-$100.00", "2025-05-18"));

            appExecutors.mainThread().execute(() -> {
                recentTransactionsList.clear();
                recentTransactionsList.addAll(dummyList);
                transactionAdapter.notifyDataSetChanged();
                if (recentTransactionsList.isEmpty()) {
                    // Optionally show a "No recent transactions" message
                }
            });
        });
    }
}

