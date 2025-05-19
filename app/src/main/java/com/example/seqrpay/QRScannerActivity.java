package com.example.seqrpay;

import androidx.appcompat.app.AppCompatActivity;
// AndroidX Core imports kept for consistency, though not directly used in this snippet for permissions
// import androidx.core.app.ActivityCompat;
// import androidx.core.content.ContextCompat;

// import android.Manifest; // Kept for consistency
import android.content.Intent;
import android.content.pm.PackageManager; // Kept for consistency
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.Toast; // Kept for consistency

import com.budiyev.android.codescanner.CodeScanner;
import com.budiyev.android.codescanner.CodeScannerView;
import com.budiyev.android.codescanner.DecodeCallback;
import com.google.zxing.Result;

import org.json.JSONObject;
import org.json.JSONException;

public class QRScannerActivity extends AppCompatActivity {
    private static final String TAG = "QRScannerActivity"; // Logging Tag

    private CodeScanner codeScanner;
    private CodeScannerView scannerView;
    private Button btnCancel;

    // Constants for passing data to ScanResultActivity
    public static final String EXTRA_QR_PAYLOAD_TYPE = "com.example.seqrpay.EXTRA_QR_PAYLOAD_TYPE";
    public static final String PAYLOAD_TYPE_SIGNED_PAYMENT = "SIGNED_PAYMENT";
    public static final String PAYLOAD_TYPE_URL = "URL";
    public static final String PAYLOAD_TYPE_OTHER = "OTHER";

    public static final String EXTRA_SIGNED_DATA_BLOCK = "com.example.seqrpay.EXTRA_SIGNED_DATA_BLOCK";
    public static final String EXTRA_SIGNATURE = "com.example.seqrpay.EXTRA_SIGNATURE";
    public static final String EXTRA_PAYEE_USERNAME = "com.example.seqrpay.EXTRA_PAYEE_USERNAME";
    public static final String EXTRA_AMOUNT = "com.example.seqrpay.EXTRA_AMOUNT";
    public static final String EXTRA_CURRENCY = "com.example.seqrpay.EXTRA_CURRENCY";
    // EXTRA_URL_TO_SCAN and EXTRA_PAYMENT_DATA are already defined in ScanResultActivity,
    // but we can use them directly here for clarity or redefine if preferred.
    // For now, let's assume ScanResultActivity.EXTRA_URL_TO_SCAN and ScanResultActivity.EXTRA_PAYMENT_DATA are accessible.


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_qrscanner);

        scannerView = findViewById(R.id.scanner_view);
        btnCancel = findViewById(R.id.btn_cancel);

        codeScanner = new CodeScanner(this, scannerView);
        codeScanner.setDecodeCallback(new DecodeCallback() {
            @Override
            public void onDecoded(final Result result) {
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
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
    }

    private void processQRCode(String qrContent) {
        Log.d(TAG, "QR Scanned Content: " + qrContent);
        Intent intent = new Intent(QRScannerActivity.this, ScanResultActivity.class);

        // Always pass the original full QR content.
        // ScanResultActivity.EXTRA_PAYMENT_DATA is used for this.
        intent.putExtra(ScanResultActivity.EXTRA_PAYMENT_DATA, qrContent);

        try {
            // Attempt to parse as our custom signed JSON payload
            JSONObject qrJson = new JSONObject(qrContent);
            String type = qrJson.optString("type");

            if ("paymentRequest".equals(type) && qrJson.has("dataToSign") && qrJson.has("signature")) {
                Log.i(TAG, "Identified as a signed payment request QR.");
                intent.putExtra(EXTRA_QR_PAYLOAD_TYPE, PAYLOAD_TYPE_SIGNED_PAYMENT);

                JSONObject dataToSign = qrJson.getJSONObject("dataToSign");
                String signature = qrJson.getString("signature");
                // Get payeeUsername, amount, currency preferably from dataToSign for integrity,
                // but they might also be in the outer JSON for quick display.
                // For verification, what's in dataToSign is paramount.
                String payeeUsername = dataToSign.optString("payeeUsername", qrJson.optString("payeeUsername"));
                String amount = dataToSign.optString("amount", qrJson.optString("amount"));
                String currency = dataToSign.optString("currency", qrJson.optString("currency"));

                intent.putExtra(EXTRA_SIGNED_DATA_BLOCK, dataToSign.toString()); // Pass the dataToSign block as a string
                intent.putExtra(EXTRA_SIGNATURE, signature);
                intent.putExtra(EXTRA_PAYEE_USERNAME, payeeUsername);
                intent.putExtra(EXTRA_AMOUNT, amount); // For display/confirmation
                intent.putExtra(EXTRA_CURRENCY, currency); // For display/confirmation

                // For signed payments, the "URL to scan" is not applicable in the VirusTotal sense.
                // We can pass null or the payload itself if ScanResultActivity needs a non-null value.
                intent.putExtra(ScanResultActivity.EXTRA_URL_TO_SCAN, (String) null); // No external URL to scan

            } else {
                // Not our specific signed format, treat as a potential URL or other data
                handleNonSignedPayload(intent, qrContent);
            }
        } catch (JSONException e) {
            // Not a valid JSON or not our format, treat as a potential URL or other data
            Log.w(TAG, "QR content is not a valid JSON or not our signed format. Treating as potential URL/Other. Error: " + e.getMessage());
            handleNonSignedPayload(intent, qrContent);
        }

        startActivity(intent);
        finish(); // Finish QRScannerActivity after launching the result activity
    }

    private void handleNonSignedPayload(Intent intent, String qrContent) {
        // Check if it's a URL for VirusTotal/Gemini scan
        if (qrContent.startsWith("http://") || qrContent.startsWith("https://")) {
            Log.i(TAG, "Identified as a URL QR code.");
            intent.putExtra(EXTRA_QR_PAYLOAD_TYPE, PAYLOAD_TYPE_URL);
            intent.putExtra(ScanResultActivity.EXTRA_URL_TO_SCAN, qrContent);
        } else {
            // It's some other data, not a URL, not our signed format.
            Log.i(TAG, "Identified as an OTHER type QR code.");
            intent.putExtra(EXTRA_QR_PAYLOAD_TYPE, PAYLOAD_TYPE_OTHER);
            intent.putExtra(ScanResultActivity.EXTRA_URL_TO_SCAN, (String) null); // No URL to scan
        }
    }


    @Override
    protected void onResume() {
        super.onResume();
        // Check for camera permission before starting preview
        // This assumes camera permission is requested in MainActivity or similar entry point.
        // If not, you should add permission request logic here or in MainActivity.
        // For example: if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED)
        codeScanner.startPreview();
        // else { request permission }
    }

    @Override
    protected void onPause() {
        codeScanner.releaseResources();
        super.onPause();
    }

    // Optional: Permission handling (if not handled globally)
    /*
    private static final int REQUEST_CAMERA_PERMISSION = 101; // Example request code
    private void checkAndRequestCameraPermission() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.CAMERA}, REQUEST_CAMERA_PERMISSION);
        } else {
            codeScanner.startPreview();
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_CAMERA_PERMISSION) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                Toast.makeText(this, "Camera permission granted", Toast.LENGTH_SHORT).show();
                codeScanner.startPreview();
            } else {
                Toast.makeText(this, "Camera permission is required to scan QR codes", Toast.LENGTH_LONG).show();
                finish(); // Or handle more gracefully
            }
        }
    }
    */
}

