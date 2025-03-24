package com.example.secureqrpayment;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import android.os.Bundle;
import android.view.View;
import android.widget.Button;

import java.util.ArrayList;
import java.util.List;

public class TransactionHistoryActivity extends AppCompatActivity {
    private RecyclerView recyclerView;
    private Button btnBack;
    private TransactionAdapter adapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_transaction_history);

        recyclerView = findViewById(R.id.recycler_view);
        btnBack = findViewById(R.id.btn_back);

        // Set up RecyclerView
        recyclerView.setLayoutManager(new LinearLayoutManager(this));

        // Load dummy transaction data
        List<Transaction> transactions = getDummyTransactions();
        adapter = new TransactionAdapter(transactions);
        recyclerView.setAdapter(adapter);

        btnBack.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                finish();
            }
        });
    }

    private List<Transaction> getDummyTransactions() {
        List<Transaction> transactions = new ArrayList<>();
        transactions.add(new Transaction("Payment to Coffee Shop", "-$5.50", "2025-03-22"));
        transactions.add(new Transaction("Transfer from John", "+$25.00", "2025-03-21"));
        transactions.add(new Transaction("Grocery Store", "-$32.47", "2025-03-20"));
        transactions.add(new Transaction("Salary Deposit", "+$1,200.00", "2025-03-15"));
        transactions.add(new Transaction("Rent Payment", "-$800.00", "2025-03-01"));
        return transactions;
    }
}
