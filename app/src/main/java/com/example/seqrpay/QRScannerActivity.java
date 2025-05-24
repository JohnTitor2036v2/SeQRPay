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

import java.util.regex.Pattern; // For more advanced URL pattern matching

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
            codeScanner.startPreview();
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
                intent.putExtra(ScanResultActivity.EXTRA_URL_TO_SCAN, (String) null); // No external URL to scan

            } else {
                // Not our specific signed format, treat as a potential URL or other data
                handleNonSignedPayload(intent, qrContent);
            }
        } catch (JSONException e) {
            // Not a valid JSON, so it must be a URL or other plain text data.
            Log.w(TAG, "QR content is not a valid JSON. Treating as potential URL/Other.");
            handleNonSignedPayload(intent, qrContent);
        }

        startActivity(intent);
        finish(); // Finish QRScannerActivity after launching the result activity
    }

    /**
     * Handles payloads that are not our signed JSON format.
     * It checks if the content is likely a URL or hostname and prepares the intent accordingly.
     * @param intent The intent to be sent to ScanResultActivity.
     * @param qrContent The raw content from the QR code.
     */
    private void handleNonSignedPayload(Intent intent, String qrContent) {
        // Improved heuristic to check if the content is likely a URL or hostname.
        // It must contain at least one dot, have no whitespace, and have a valid top-level domain (e.g., .com, .kz, .pt).
        // This is a more robust check than just looking for "http".
        // Pattern.matches looks for a full string match.
        boolean isLikelyUrl = Pattern.matches("^[a-zA-Z0-9\\-]+(\\.[a-zA-Z0-9\\-]+)+$", qrContent.trim());


        if (isLikelyUrl) {
            Log.i(TAG, "Identified as a URL-like QR code: " + qrContent);
            intent.putExtra(EXTRA_QR_PAYLOAD_TYPE, PAYLOAD_TYPE_URL);

            // Prepare the URL for scanning. Prepend "https://" if no protocol is specified.
            String urlToScan = qrContent.trim();
            if (!urlToScan.toLowerCase().startsWith("http://") && !urlToScan.toLowerCase().startsWith("https://")) {
                urlToScan = "https://" + urlToScan;
                Log.d(TAG, "Prepended 'https://' to hostname. Full URL to scan: " + urlToScan);
            }
            intent.putExtra(ScanResultActivity.EXTRA_URL_TO_SCAN, urlToScan);
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
        // This assumes camera permission is requested and granted at an earlier point (e.g., in MainActivity).
        codeScanner.startPreview();
    }

    @Override
    protected void onPause() {
        codeScanner.releaseResources();
        super.onPause();
    }
}