package com.example.seqrpay;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat; // Keep for permissions if needed
import androidx.core.content.ContextCompat;

import android.Manifest; // Keep for permissions if needed
import android.content.Intent; // <<<=== ADDED
import android.content.pm.PackageManager; // Keep for permissions if needed
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.Toast; // Keep for permissions if needed

import com.budiyev.android.codescanner.CodeScanner;
import com.budiyev.android.codescanner.CodeScannerView;
import com.budiyev.android.codescanner.DecodeCallback;
import com.google.zxing.Result;

public class QRScannerActivity extends AppCompatActivity {
    private CodeScanner codeScanner;
    private CodeScannerView scannerView;
    private Button btnCancel;
    // private VirusTotalApi virusTotalApi; // <<<=== REMOVED (Moved to ScanResultActivity)

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_qrscanner);

        scannerView = findViewById(R.id.scanner_view);
        btnCancel = findViewById(R.id.btn_cancel);
        // virusTotalApi = new VirusTotalApi(this); // <<<=== REMOVED

        codeScanner = new CodeScanner(this, scannerView);
        codeScanner.setDecodeCallback(new DecodeCallback() {
            @Override
            public void onDecoded(final Result result) {
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        // Process the scanned QR code <<<=== MODIFIED below
                        processQRCode(result.getText());
                    }
                });
            }
        });

        scannerView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                codeScanner.startPreview();
            }
        });

        btnCancel.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                finish(); // Close scanner
            }
        });

        // --- Optional: Request Camera permission here if not done in MainActivity ---
        // checkCameraPermission();
    }

    private void processQRCode(String qrContent) {
        // Always start the ScanResultActivity. It will handle the logic.
        Intent intent = new Intent(QRScannerActivity.this, ScanResultActivity.class);

        // Pass both the URL (if applicable) and the original QR content
        if (qrContent.startsWith("http://") || qrContent.startsWith("https://")) {
            intent.putExtra(ScanResultActivity.EXTRA_URL_TO_SCAN, qrContent);
        } else {
            // If it's not a URL, pass null or empty string for URL_TO_SCAN,
            // ScanResultActivity should handle this (e.g., show as safe immediately or show different UI)
            // OR potentially skip ScanResultActivity entirely if no scan is needed.
            // Let's assume for now we still show ScanResultActivity but pass null URL.
            intent.putExtra(ScanResultActivity.EXTRA_URL_TO_SCAN, (String) null);
            // Alternatively, skip ScanResultActivity for non-URLs:
            // proceedToPaymentDirectly(qrContent);
            // return;
        }
        // Always pass the original data for potential payment processing
        intent.putExtra(ScanResultActivity.EXTRA_PAYMENT_DATA, qrContent);

        startActivity(intent);

        // Finish QRScannerActivity after launching the result activity,
        // so pressing back on the result screen doesn't return to the scanner.
        finish();


        // <<<=== REMOVED OLD LOGIC ===>>>
        /*
        // Check if the QR code contains a URL
        if (qrContent.startsWith("http://") || qrContent.startsWith("https://")) {
            // It's a URL, scan it with VirusTotal
            Toast.makeText(this, R.string.qr_scanning_url, Toast.LENGTH_SHORT).show(); // Use string resource
            virusTotalApi.scanUrl(qrContent, new VirusTotalApi.ScanCallback() {
                @Override
                public void onResult(boolean isSafe, String message) {
                    if (isSafe) {
                        // URL is safe, proceed with payment
                        proceedToPayment(qrContent);
                    } else {
                        // URL is potentially malicious
                        String alertMsg = getString(R.string.qr_scan_alert_prefix) + message;
                        Toast.makeText(QRScannerActivity.this, alertMsg, Toast.LENGTH_LONG).show();
                    }
                }

                @Override
                public void onError(String error) {
                     String errorMsg = getString(R.string.qr_scan_error_prefix) + error;
                    Toast.makeText(QRScannerActivity.this, errorMsg, Toast.LENGTH_LONG).show();
                }
            });
        } else {
            // It's not a URL, assume it's a payment code
            proceedToPayment(qrContent);
        }
        */
    }

    // <<<=== REMOVED OLD METHOD ===>>>
    /*
    private void proceedToPayment(String paymentData) {
        // Show payment confirmation dialog
        PaymentConfirmationDialog dialog = new PaymentConfirmationDialog(this, paymentData);
        dialog.show();
        // Maybe finish(); here? Depends on desired flow.
    }
    */

    // Optional: Direct payment if QR is not a URL (alternative to showing ScanResultActivity)
    /*
    private void proceedToPaymentDirectly(String paymentData) {
        PaymentConfirmationDialog dialog = new PaymentConfirmationDialog(this, paymentData);
        dialog.show();
        dialog.setOnDismissListener(d -> finish()); // Finish scanner after dialog dismiss
    }
    */


    @Override
    protected void onResume() {
        super.onResume();
        codeScanner.startPreview(); // Start preview on resume
    }

    @Override
    protected void onPause() {
        codeScanner.releaseResources(); // Release resources on pause
        super.onPause();
    }

    // --- Optional: Permission handling ---
    /*
    private static final int REQUEST_CAMERA_PERMISSION = 101;
    private void checkCameraPermission() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.CAMERA}, REQUEST_CAMERA_PERMISSION);
        } else {
             // Permission already granted, start preview if needed (though onResume handles it)
            // codeScanner.startPreview();
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_CAMERA_PERMISSION) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                 Toast.makeText(this, "Camera permission granted", Toast.LENGTH_SHORT).show();
                 codeScanner.startPreview(); // Start preview after permission granted
            } else {
                Toast.makeText(this, "Camera permission required for QR scanning", Toast.LENGTH_LONG).show();
                finish(); // Close scanner if permission denied
            }
        }
    }
    */
}