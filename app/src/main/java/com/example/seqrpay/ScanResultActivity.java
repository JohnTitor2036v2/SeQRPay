package com.example.seqrpay;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

public class ScanResultActivity extends AppCompatActivity {

    public static final String EXTRA_URL_TO_SCAN = "com.example.seqrpay.EXTRA_URL_TO_SCAN";
    public static final String EXTRA_PAYMENT_DATA = "com.example.seqrpay.EXTRA_PAYMENT_DATA"; // Also pass original data

    private ProgressBar progressBar;
    private LinearLayout resultLayout;
    private ImageView statusIcon;
    private TextView statusText;
    private TextView detailsText;
    private Button proceedButton;
    private Button cancelButton;

    private VirusTotalApi virusTotalApi;
    private String urlToScan;
    private String originalPaymentData; // Store the original QR data

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_scan_result);

        progressBar = findViewById(R.id.scan_progress_bar);
        resultLayout = findViewById(R.id.scan_result_layout);
        statusIcon = findViewById(R.id.scan_status_icon);
        statusText = findViewById(R.id.scan_status_text);
        detailsText = findViewById(R.id.scan_details_text);
        proceedButton = findViewById(R.id.btn_proceed_payment);
        cancelButton = findViewById(R.id.btn_scan_cancel);

        virusTotalApi = new VirusTotalApi(this); // Instantiate VirusTotalApi here

        // Get URL and original data from Intent extras
        urlToScan = getIntent().getStringExtra(EXTRA_URL_TO_SCAN);
        originalPaymentData = getIntent().getStringExtra(EXTRA_PAYMENT_DATA); // Get original data

        if (urlToScan == null || urlToScan.isEmpty()) {
            // Handle error - URL not passed correctly
            showErrorResult(getString(R.string.scan_error_no_url));
            return;
        }

        // Start scanning immediately
        statusText.setText(R.string.qr_scanning_url); // Initial status
        progressBar.setVisibility(View.VISIBLE);
        resultLayout.setVisibility(View.GONE); // Hide result view initially

        virusTotalApi.scanUrl(urlToScan, new VirusTotalApi.ScanCallback() {
            @Override
            public void onResult(boolean isSafe, String message) {
                // Ensure UI updates run on the main thread (already handled by VirusTotalApi's runOnMainThread)
                showScanResult(isSafe, message);
            }

            @Override
            public void onError(String error) {
                // Ensure UI updates run on the main thread
                showErrorResult(error);
            }
        });

        // Set up button listeners
        proceedButton.setOnClickListener(v -> proceedToPayment());
        cancelButton.setOnClickListener(v -> finish()); // Simply close this activity
    }

    private void showScanResult(boolean isSafe, String message) {
        progressBar.setVisibility(View.GONE);
        resultLayout.setVisibility(View.VISIBLE);

        detailsText.setText(message); // Show detailed message from VirusTotal

        if (isSafe) {
            statusIcon.setImageResource(R.drawable.ic_check_circle_green); // Replace with your safe icon
            statusIcon.setColorFilter(ContextCompat.getColor(this, android.R.color.holo_green_dark)); // Tint if needed
            statusText.setText(R.string.scan_result_safe);
            proceedButton.setVisibility(View.VISIBLE); // Show proceed button only if safe
        } else {
            statusIcon.setImageResource(R.drawable.ic_warning_red); // Replace with your unsafe icon
            statusIcon.setColorFilter(ContextCompat.getColor(this, android.R.color.holo_red_dark)); // Tint if needed
            statusText.setText(R.string.scan_result_unsafe);
            proceedButton.setVisibility(View.GONE); // Keep proceed button hidden
        }
    }

    private void showErrorResult(String error) {
        progressBar.setVisibility(View.GONE);
        resultLayout.setVisibility(View.VISIBLE);

        statusIcon.setImageResource(R.drawable.ic_warning_red); // Use warning icon for errors too
        statusIcon.setColorFilter(ContextCompat.getColor(this, android.R.color.holo_red_dark));
        statusText.setText(R.string.scan_result_error);
        detailsText.setText(getString(R.string.scan_error_details_prefix) + error); // Prefix error message
        proceedButton.setVisibility(View.GONE);
    }

    private void proceedToPayment() {
        // Use the originalPaymentData received from the QR code
        if (originalPaymentData != null && !originalPaymentData.isEmpty()) {
            // Option 1: Show PaymentConfirmationDialog directly from here
            PaymentConfirmationDialog dialog = new PaymentConfirmationDialog(this, originalPaymentData);
            dialog.show();
            // Optional: Finish this activity after showing the dialog or based on dialog result
            // dialog.setOnDismissListener(d -> finish());

            // Option 2: Return result to QRScannerActivity (if needed)
            // Intent resultIntent = new Intent();
            // resultIntent.putExtra("PAYMENT_DATA", originalPaymentData);
            // setResult(RESULT_OK, resultIntent);
            // finish();

            // For now, let's just show the dialog and finish this activity
            dialog.setOnDismissListener(d -> finish());

        } else {
            // Should not happen if launched correctly, but handle anyway
            Toast.makeText(this, R.string.error_generic, Toast.LENGTH_SHORT).show();
            finish();
        }
    }
}