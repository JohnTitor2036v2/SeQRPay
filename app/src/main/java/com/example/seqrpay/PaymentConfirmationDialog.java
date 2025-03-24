package com.example.seqrpay;

import android.app.Dialog;
import android.content.Context;
import android.os.Bundle;
import android.view.View;
import android.view.Window;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

public class PaymentConfirmationDialog extends Dialog {
    private String paymentData;
    private Button btnConfirm, btnCancel;
    private TextView tvPaymentDetails;

    public PaymentConfirmationDialog(Context context, String paymentData) {
        super(context);
        this.paymentData = paymentData;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        setContentView(R.layout.dialog_payment_confirmation);

        tvPaymentDetails = findViewById(R.id.tv_payment_details);
        btnConfirm = findViewById(R.id.btn_confirm);
        btnCancel = findViewById(R.id.btn_cancel);

        // Parse payment data and display
        parseAndDisplayPaymentData();

        btnConfirm.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                // Process payment
                Toast.makeText(getContext(), "Payment successful!", Toast.LENGTH_SHORT).show();
                dismiss();
            }
        });

        btnCancel.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Toast.makeText(getContext(), "Payment cancelled", Toast.LENGTH_SHORT).show();
                dismiss();
            }
        });
    }

    private void parseAndDisplayPaymentData() {
        // In a real app, you would parse the QR code data here
        // For now, just display the raw data or a simplified version

        // Example format: PAYMENT|MERCHANT|AMOUNT|REFERENCE
        String[] parts = paymentData.split("\\|");
        StringBuilder details = new StringBuilder();

        if (parts.length >= 3 && parts[0].equals("PAYMENT")) {
            details.append("Merchant: ").append(parts[1]).append("\n");
            details.append("Amount: $").append(parts[2]).append("\n");
            if (parts.length >= 4) {
                details.append("Reference: ").append(parts[3]);
            }
        } else {
            // If not in expected format, just show raw data
            details.append("Payment Data: ").append(paymentData);
        }

        tvPaymentDetails.setText(details.toString());
    }
}
