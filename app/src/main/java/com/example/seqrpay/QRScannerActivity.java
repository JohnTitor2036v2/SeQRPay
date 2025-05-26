package com.example.seqrpay;

import androidx.appcompat.app.AppCompatActivity;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;

import com.budiyev.android.codescanner.CodeScanner;
import com.budiyev.android.codescanner.CodeScannerView;
import com.budiyev.android.codescanner.DecodeCallback;
import com.google.zxing.Result;

import org.json.JSONObject;
import org.json.JSONException;

import java.util.regex.Pattern; // Keep for reference, but regex check will be removed

public class QRScannerActivity extends AppCompatActivity {
    private static final String TAG = "QRScannerActivity"; // Logging Tag

    private CodeScanner codeScanner;
    private CodeScannerView scannerView;
    private Button btnCancel;

    // Constants for passing data to ScanResultActivity
    public static final String EXTRA_QR_PAYLOAD_TYPE = "com.example.seqrpay.EXTRA_QR_PAYLOAD_TYPE";
    public static final String PAYLOAD_TYPE_SIGNED_PAYMENT = "SIGNED_PAYMENT";
    public static final String PAYLOAD_TYPE_URL = "URL";
    // PAYLOAD_TYPE_OTHER is no longer explicitly used, as all non-signed are treated as URL
    // public static final String PAYLOAD_TYPE_OTHER = "OTHER";


    public static final String EXTRA_SIGNED_DATA_BLOCK = "com.example.seqrpay.EXTRA_SIGNED_DATA_BLOCK";
    public static final String EXTRA_SIGNATURE = "com.example.seqrpay.EXTRA_SIGNATURE";
    public static final String EXTRA_PAYEE_USERNAME = "com.example.seqrpay.EXTRA_PAYEE_USERNAME";
    public static final String EXTRA_AMOUNT = "com.example.seqrpay.EXTRA_AMOUNT";
    public static final String EXTRA_CURRENCY = "com.example.seqrpay.EXTRA_CURRENCY";


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
        if (qrContent == null || qrContent.trim().isEmpty()) {
            Log.w(TAG, "Scanned QR code is empty or null.");
            // Optionally show a toast to the user
            // Toast.makeText(this, "Scanned QR code is empty.", Toast.LENGTH_SHORT).show();
            // Restart the preview to allow for another scan.
            if (codeScanner != null) { // Ensure codeScanner is not null
                codeScanner.startPreview();
            }
            return;
        }

        Log.d(TAG, "QR Scanned Content: " + qrContent);
        Intent intent = new Intent(QRScannerActivity.this, ScanResultActivity.class);

        // Always pass the original full QR content.
        intent.putExtra(ScanResultActivity.EXTRA_PAYMENT_DATA, qrContent);

        try {
            // Attempt to parse as our custom signed JSON payload first
            JSONObject qrJson = new JSONObject(qrContent);
            String type = qrJson.optString("type");

            if ("paymentRequest".equals(type) && qrJson.has("dataToSign") && qrJson.has("signature")) {
                Log.i(TAG, "Identified as a signed payment request QR.");
                intent.putExtra(EXTRA_QR_PAYLOAD_TYPE, PAYLOAD_TYPE_SIGNED_PAYMENT);

                JSONObject dataToSign = qrJson.getJSONObject("dataToSign");
                String signature = qrJson.getString("signature");
                String payeeUsername = dataToSign.optString("payeeUsername", qrJson.optString("payeeUsername"));
                String amount = dataToSign.optString("amount", qrJson.optString("amount"));
                String currency = dataToSign.optString("currency", qrJson.optString("currency"));

                intent.putExtra(EXTRA_SIGNED_DATA_BLOCK, dataToSign.toString());
                intent.putExtra(EXTRA_SIGNATURE, signature);
                intent.putExtra(EXTRA_PAYEE_USERNAME, payeeUsername);
                intent.putExtra(EXTRA_AMOUNT, amount);
                intent.putExtra(EXTRA_CURRENCY, currency);
                intent.putExtra(ScanResultActivity.EXTRA_URL_TO_SCAN, (String) null); // No external URL to scan for this type

            } else {
                // Not our specific signed format, treat as a URL by default
                handleNonSignedPayloadAsUrl(intent, qrContent);
            }
        } catch (JSONException e) {
            // Not a valid JSON, so it must be treated as a URL.
            Log.w(TAG, "QR content is not a valid JSON. Treating as URL.");
            handleNonSignedPayloadAsUrl(intent, qrContent);
        }

        startActivity(intent);
        finish(); // Finish QRScannerActivity after launching the result activity
    }

    /**
     * Handles payloads that are not our signed JSON format.
     * It now treats all such payloads as potential URLs.
     * @param intent The intent to be sent to ScanResultActivity.
     * @param qrContent The raw content from the QR code.
     */
    private void handleNonSignedPayloadAsUrl(Intent intent, String qrContent) {
        Log.i(TAG, "Treating non-signed QR content as a URL: " + qrContent);
        intent.putExtra(EXTRA_QR_PAYLOAD_TYPE, PAYLOAD_TYPE_URL);

        // Prepare the content for scanning as a URL. Prepend "https://" if no protocol is specified.
        String urlToScan = qrContent.trim();
        // Check if it starts with http:// or https:// (case-insensitive)
        if (!urlToScan.toLowerCase().matches("^https?://.*")) {
            urlToScan = "https://" + urlToScan;
            Log.d(TAG, "Prepended 'https://' to content. Full string to scan as URL: " + urlToScan);
        }
        intent.putExtra(ScanResultActivity.EXTRA_URL_TO_SCAN, urlToScan);
    }


    @Override
    protected void onResume() {
        super.onResume();
        // This assumes camera permission is requested and granted at an earlier point (e.g., in MainActivity).
        if (codeScanner != null) { // Ensure codeScanner is not null
            codeScanner.startPreview();
        }
    }

    @Override
    protected void onPause() {
        if (codeScanner != null) { // Ensure codeScanner is not null
            codeScanner.releaseResources();
        }
        super.onPause();
    }
}
