package com.example.seqrpay;

import androidx.appcompat.app.AppCompatActivity;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

public class DashboardActivity extends AppCompatActivity {
    private Button btnScan, btnTransfer, btnHistory;
    private TextView tvBalance;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_dashboard);

        btnScan = findViewById(R.id.btn_scan);
        btnTransfer = findViewById(R.id.btn_transfer);
        btnHistory = findViewById(R.id.btn_history);
        tvBalance = findViewById(R.id.tv_balance);

        // Set a dummy balance
        tvBalance.setText("Balance: $1,000.00");

        btnScan.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startActivity(new Intent(DashboardActivity.this, QRScannerActivity.class));
            }
        });

        btnTransfer.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startActivity(new Intent(DashboardActivity.this, TransferActivity.class));
            }
        });

        btnHistory.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startActivity(new Intent(DashboardActivity.this, TransactionHistoryActivity.class));
            }
        });
    }
}