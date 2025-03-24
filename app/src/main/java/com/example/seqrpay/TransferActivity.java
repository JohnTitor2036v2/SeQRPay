package com.example.seqrpay;

import androidx.appcompat.app.AppCompatActivity;

import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

public class TransferActivity extends AppCompatActivity {
    private EditText etRecipient, etAmount, etNotes;
    private Button btnTransfer, btnCancel;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_transfer);

        etRecipient = findViewById(R.id.et_recipient);
        etAmount = findViewById(R.id.et_amount);
        etNotes = findViewById(R.id.et_notes);
        btnTransfer = findViewById(R.id.btn_transfer);
        btnCancel = findViewById(R.id.btn_cancel);

        btnTransfer.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                String recipient = etRecipient.getText().toString().trim();
                String amountStr = etAmount.getText().toString().trim();
                String notes = etNotes.getText().toString().trim();

                if (recipient.isEmpty() || amountStr.isEmpty()) {
                    Toast.makeText(TransferActivity.this, "Please fill all required fields", Toast.LENGTH_SHORT).show();
                    return;
                }

                try {
                    double amount = Double.parseDouble(amountStr);
                    // Perform transfer logic here
                    Toast.makeText(TransferActivity.this, "Transfer of $" + amount + " to " + recipient + " successful", Toast.LENGTH_SHORT).show();
                    finish();
                } catch (NumberFormatException e) {
                    Toast.makeText(TransferActivity.this, "Please enter a valid amount", Toast.LENGTH_SHORT).show();
                }
            }
        });

        btnCancel.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                finish();
            }
        });
    }
}