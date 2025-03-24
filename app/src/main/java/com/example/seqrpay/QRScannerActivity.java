package com.example.seqrpay;

import androidx.appcompat.app.AppCompatActivity;

import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.Toast;

import com.budiyev.android.codescanner.CodeScanner;
import com.budiyev.android.codescanner.CodeScannerView;
import com.budiyev.android.codescanner.DecodeCallback;
import com.google.zxing.Result;

public class QRScannerActivity extends AppCompatActivity {
    private CodeScanner codeScanner;
    private CodeScannerView scannerView;
    private Button btnCancel;
    private VirusTotalApi virusTotalApi;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_qrscanner);

        scannerView = findViewById(R.id.scanner_view);
        btnCancel = findViewById(R.id.btn_cancel);
        virusTotalApi = new VirusTotalApi(this);

        codeScanner = new CodeScanner(this, scannerView);
        codeScanner.setDecodeCallback(new DecodeCallback() {
            @Override
            public void onDecoded(final Result result) {
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        // Process the scanned QR code
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
                finish();
            }
        });
    }

    private void processQRCode(String qrContent) {
        // Check if the QR code contains a URL
        if (qrContent.startsWith("http://") || qrContent.startsWith("https://")) {
            // It's a URL, scan it with VirusTotal
            Toast.makeText(this, "Scanning URL for security threats...", Toast.LENGTH_SHORT).show();
            virusTotalApi.scanUrl(qrContent, new VirusTotalApi.ScanCallback() {
                @Override
                public void onResult(boolean isSafe, String message) {
                    if (isSafe) {
                        // URL is safe, proceed with payment
                        proceedToPayment(qrContent);
                    } else {
                        // URL is potentially malicious
                        Toast.makeText(QRScannerActivity.this, "Security Alert: " + message, Toast.LENGTH_LONG).show();
                    }
                }

                @Override
                public void onError(String error) {
                    Toast.makeText(QRScannerActivity.this, "Error scanning URL: " + error, Toast.LENGTH_LONG).show();
                }
            });
        } else {
            // It's not a URL, assume it's a payment code
            proceedToPayment(qrContent);
        }
    }

    private void proceedToPayment(String paymentData) {
        // Show payment confirmation dialog
        PaymentConfirmationDialog dialog = new PaymentConfirmationDialog(this, paymentData);
        dialog.show();
    }

    @Override
    protected void onResume() {
        super.onResume();
        codeScanner.startPreview();
    }

    @Override
    protected void onPause() {
        codeScanner.releaseResources();
        super.onPause();
    }
}