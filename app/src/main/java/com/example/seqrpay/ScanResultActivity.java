// File: ScanResultActivity.java
package com.example.seqrpay;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import com.google.ai.client.generativeai.GenerativeModel;
import com.google.ai.client.generativeai.java.GenerativeModelFutures;
import com.google.ai.client.generativeai.type.BlockThreshold;
import com.google.ai.client.generativeai.type.Content;
import com.google.ai.client.generativeai.type.GenerateContentResponse;
import com.google.ai.client.generativeai.type.GenerationConfig;
import com.google.ai.client.generativeai.type.HarmCategory;
import com.google.ai.client.generativeai.type.SafetySetting;
import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;

import org.json.JSONException;
import org.json.JSONObject;

import java.security.PublicKey;
import java.util.Collections;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.Executor;

public class ScanResultActivity extends AppCompatActivity {

    // Constants for intent extras (already defined in this class from previous versions)
    public static final String EXTRA_URL_TO_SCAN = "com.example.seqrpay.EXTRA_URL_TO_SCAN";
    public static final String EXTRA_PAYMENT_DATA = "com.example.seqrpay.EXTRA_PAYMENT_DATA"; // Raw QR content

    private static final String TAG = "ScanResultActivity";

    // UI Elements
    private ProgressBar progressBar;
    private LinearLayout resultLayout;
    private ImageView statusIcon;
    private TextView statusText;
    private TextView detailsText;

    // Signature Verification UI elements
    private TextView tvSignatureStatusLabel, tvSignatureStatusText;
    private TextView tvPaymentInfoLabel, tvPaymentInfoText;

    // Gemini AI Rating UI elements
    private TextView geminiRatingLabel; // Assuming R.id.gemini_rating_label exists
    private TextView geminiRatingText;

    // Action Buttons
    private Button proceedButton;
    private Button cancelButton;

    // Services
    private VirusTotalApi virusTotalApi;
    private AppExecutors appExecutors;
    private GenerativeModelFutures geminiModel;

    // Data from Intent
    private String payloadType;
    private String urlToScan; // Will be null for non-URL types
    private String originalPaymentData; // The full raw QR string

    // State variables
    private boolean signatureVerified = false; // For signed payments
    private boolean vtScanComplete = false;    // For URL scans
    private boolean geminiScanComplete = false; // For URL scans
    private boolean isVtSafe = false;          // For URL scans
    private boolean proceedAllowed = false;    // Overall flag

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_scan_result);

        // Initialize UI elements
        progressBar = findViewById(R.id.scan_progress_bar);
        resultLayout = findViewById(R.id.scan_result_layout);
        statusIcon = findViewById(R.id.scan_status_icon);
        statusText = findViewById(R.id.scan_status_text);
        detailsText = findViewById(R.id.scan_details_text);

        tvSignatureStatusLabel = findViewById(R.id.tv_signature_status_label);
        tvSignatureStatusText = findViewById(R.id.tv_signature_status_text);
        tvPaymentInfoLabel = findViewById(R.id.tv_payment_info_label);
        tvPaymentInfoText = findViewById(R.id.tv_payment_info_text);

        geminiRatingLabel = findViewById(R.id.gemini_rating_label);
        geminiRatingText = findViewById(R.id.gemini_rating_text);

        proceedButton = findViewById(R.id.btn_proceed_payment);
        cancelButton = findViewById(R.id.btn_scan_cancel);

        // Initialize Services
        virusTotalApi = new VirusTotalApi(this);
        appExecutors = AppExecutors.getInstance();
        initializeGemini();

        // Get Data from Intent
        Intent intent = getIntent();
        payloadType = intent.getStringExtra(QRScannerActivity.EXTRA_QR_PAYLOAD_TYPE);
        urlToScan = intent.getStringExtra(EXTRA_URL_TO_SCAN);
        originalPaymentData = intent.getStringExtra(EXTRA_PAYMENT_DATA);

        // Initial UI State
        showLoadingState(true);
        hideAllScanSpecificSections(); // Hide sections that are type-dependent

        // Process based on payload type
        if (payloadType == null) {
            Log.e(TAG, "Payload type is null. Cannot process QR.");
            updateOverallStatusUI(false, "Error: Invalid QR data received.", null);
            showLoadingState(false);
            checkCompletionAndFinalizeUI();
            return;
        }

        Log.d(TAG, "Processing Payload Type: " + payloadType);
        switch (payloadType) {
            case QRScannerActivity.PAYLOAD_TYPE_SIGNED_PAYMENT:
                handleSignedPayment(intent);
                // For signed payments, completion is determined within handleSignedPayment
                showLoadingState(false); // Hide loading as processing is synchronous here
                checkCompletionAndFinalizeUI();
                break;
            case QRScannerActivity.PAYLOAD_TYPE_URL:
                if (urlToScan != null && !urlToScan.isEmpty()) {
                    updateOverallStatusUI(true, getString(R.string.qr_scanning_url), "Checking URL with security services...");
                    // Make relevant sections visible for URL scan
                    geminiRatingLabel.setVisibility(View.VISIBLE);
                    geminiRatingText.setVisibility(View.VISIBLE);
                    geminiRatingText.setText(R.string.gemini_rating_loading);

                    startVirusTotalScan(urlToScan);
                    startGeminiScan(urlToScan);
                } else {
                    updateOverallStatusUI(false, "Error: URL payload type but no URL provided.", null);
                    vtScanComplete = true; geminiScanComplete = true; // Mark as done to show error
                    showLoadingState(false);
                    checkCompletionAndFinalizeUI();
                }
                break;
            // REMOVED: case QRScannerActivity.PAYLOAD_TYPE_OTHER:
            default: // Handles any unexpected payload types
                Log.w(TAG, "Received unknown or unexpected payload type: " + payloadType);
                updateOverallStatusUI(false, "Error: Unknown QR Content", "This QR code format is not recognized or supported.");
                proceedAllowed = false; // Do not allow proceeding for unknown types
                vtScanComplete = true; geminiScanComplete = true; // Mark scans as "done"
                showLoadingState(false);
                checkCompletionAndFinalizeUI();
                break;
        }

        // Button Listeners
        proceedButton.setOnClickListener(v -> proceedToPayment());
        cancelButton.setOnClickListener(v -> finish());
    }

    private void showLoadingState(boolean isLoading) {
        progressBar.setVisibility(isLoading ? View.VISIBLE : View.GONE);
        resultLayout.setVisibility(isLoading ? View.GONE : View.VISIBLE);
    }

    private void hideAllScanSpecificSections() {
        tvSignatureStatusLabel.setVisibility(View.GONE);
        tvSignatureStatusText.setVisibility(View.GONE);
        tvPaymentInfoLabel.setVisibility(View.GONE);
        tvPaymentInfoText.setVisibility(View.GONE);
        geminiRatingLabel.setVisibility(View.GONE);
        geminiRatingText.setVisibility(View.GONE);
    }

    private void initializeGemini() {
        String apiKey = BuildConfig.GEMINI_API_KEY; // Ensure this is in your local.properties & build.gradle
        if (apiKey == null || apiKey.isEmpty() || apiKey.equals("YOUR_API_KEY_HERE")) {
            Log.e(TAG, "Gemini API Key not configured in BuildConfig!");
            geminiScanComplete = true; // Mark as complete to not block UI if API key is missing
            return;
        }
        SafetySetting harassmentSafety = new SafetySetting(HarmCategory.HARASSMENT, BlockThreshold.ONLY_HIGH);
        // Add other safety settings as needed: HATE_SPEECH, SEXUALLY_EXPLICIT, DANGEROUS_CONTENT
        GenerationConfig.Builder configBuilder = new GenerationConfig.Builder();
        // configBuilder.temperature = 0.9f; // Example optional configurations
        // configBuilder.topK = 1;
        GenerativeModel googleAiModel = new GenerativeModel(
                "gemini-1.5-flash", // Or "gemini-pro" or other suitable model
                apiKey,
                configBuilder.build(),
                Collections.singletonList(harassmentSafety)
        );
        geminiModel = GenerativeModelFutures.from(googleAiModel);
    }

    private void handleSignedPayment(Intent intent) {
        // Make signature section visible
        tvSignatureStatusLabel.setVisibility(View.VISIBLE);
        tvSignatureStatusText.setVisibility(View.VISIBLE);
        tvPaymentInfoLabel.setVisibility(View.VISIBLE);
        tvPaymentInfoText.setVisibility(View.VISIBLE);

        String signedDataBlockStr = intent.getStringExtra(QRScannerActivity.EXTRA_SIGNED_DATA_BLOCK);
        String signatureBase64 = intent.getStringExtra(QRScannerActivity.EXTRA_SIGNATURE);
        String payeeUsername = intent.getStringExtra(QRScannerActivity.EXTRA_PAYEE_USERNAME);
        String amount = intent.getStringExtra(QRScannerActivity.EXTRA_AMOUNT);
        String currency = intent.getStringExtra(QRScannerActivity.EXTRA_CURRENCY);

        String paymentInfo = "Payee: " + (payeeUsername != null ? payeeUsername : "N/A") +
                "\nAmount: " + (amount != null ? amount : "N/A") +
                " " + (currency != null ? currency : "");
        tvPaymentInfoText.setText(paymentInfo);

        if (signedDataBlockStr == null || signatureBase64 == null || payeeUsername == null) {
            Log.e(TAG, "Missing data for signed payment verification.");
            updateOverallStatusUI(false, "Error: Incomplete Signed Payment Data", "Could not verify payment details.");
            tvSignatureStatusText.setText("Error: Incomplete Data");
            tvSignatureStatusText.setTextColor(ContextCompat.getColor(this, android.R.color.holo_red_dark));
            signatureVerified = false;
        } else {
            try {
                JSONObject dataToSignJson = new JSONObject(signedDataBlockStr);
                String canonicalDataToSign = createCanonicalString(dataToSignJson); // Use the same method as in GenerateQrActivity

                if (canonicalDataToSign == null) {
                    throw new Exception("Failed to create canonical string for verification.");
                }
                Log.d(TAG, "Canonical data for verification: " + canonicalDataToSign);

                PublicKey payeePublicKey = UserKeyPairManager.getPublicKeyForPayee(this, payeeUsername);

                if (payeePublicKey == null) {
                    Log.e(TAG, "Could not retrieve public key for payee: " + payeeUsername);
                    updateOverallStatusUI(false, "Security Alert", "Cannot verify payee: Public key not found.");
                    tvSignatureStatusText.setText("Error: Payee Key Missing");
                    tvSignatureStatusText.setTextColor(ContextCompat.getColor(this, android.R.color.holo_red_dark));
                    signatureVerified = false;
                } else {
                    signatureVerified = SecurityUtils.verifySignature(canonicalDataToSign, signatureBase64, payeePublicKey);
                    if (signatureVerified) {
                        Log.i(TAG, "Signature VERIFIED for payee: " + payeeUsername);
                        updateOverallStatusUI(true, "Payment QR Verified", paymentInfo); // Show payment info in main details
                        tvSignatureStatusText.setText("Valid");
                        tvSignatureStatusText.setTextColor(ContextCompat.getColor(this, android.R.color.holo_green_dark));
                    } else {
                        Log.w(TAG, "Signature INVALID for payee: " + payeeUsername);
                        updateOverallStatusUI(false, "Security Alert: Invalid Signature!", "The payment QR code could not be verified. It may have been tampered with or is not from the claimed payee.");
                        tvSignatureStatusText.setText("Invalid!");
                        tvSignatureStatusText.setTextColor(ContextCompat.getColor(this, android.R.color.holo_red_dark));
                    }
                }
            } catch (Exception e) {
                Log.e(TAG, "Error during signature verification process", e);
                updateOverallStatusUI(false, "Error: Signature Processing Failed", e.getMessage());
                tvSignatureStatusText.setText("Error: Verification Failed");
                tvSignatureStatusText.setTextColor(ContextCompat.getColor(this, android.R.color.holo_red_dark));
                signatureVerified = false;
            }
        }
        // For signed payments, VT and Gemini scans are considered "complete" as they are not applicable.
        vtScanComplete = true;
        geminiScanComplete = true;
        proceedAllowed = signatureVerified; // Allow proceeding only if signature is valid
    }

    private void startVirusTotalScan(String url) {
        virusTotalApi.scanUrl(url, new VirusTotalApi.ScanCallback() {
            @Override
            public void onResult(boolean isSafe, String message) {
                Log.d(TAG, "VirusTotal Result: isSafe=" + isSafe + ", message=" + message);
                isVtSafe = isSafe; // Store VT safety status specifically for URL scans
                updateOverallStatusUI(isSafe, isSafe ? getString(R.string.scan_result_safe) : getString(R.string.scan_result_unsafe), message);
                vtScanComplete = true;
                checkCompletionAndFinalizeUI();
            }

            @Override
            public void onError(String error) {
                Log.e(TAG, "VirusTotal Error: " + error);
                isVtSafe = false; // Treat errors as unsafe for caution
                updateOverallStatusUI(false, getString(R.string.scan_result_error), "VirusTotal: " + error);
                vtScanComplete = true;
                checkCompletionAndFinalizeUI();
            }
        });
    }

    private void startGeminiScan(String url) {
        if (geminiModel == null) {
            Log.w(TAG, "Gemini model not initialized, skipping Gemini scan.");
            updateGeminiDisplay(getString(R.string.gemini_rating_error) + " (Not initialized)");
            geminiScanComplete = true;
            checkCompletionAndFinalizeUI();
            return;
        }

        geminiRatingLabel.setVisibility(View.VISIBLE); // Ensure visible
        geminiRatingText.setVisibility(View.VISIBLE);
        geminiRatingText.setText(R.string.gemini_rating_loading);

        Content prompt = new Content.Builder()
                .addText("Analyze the trustworthiness of this URL for a secure payment app user. Is it safe, potentially risky, or malicious? Here is an example of a safe Kaspi QR Payment link: https://pay.kaspi.kz/pay/zdf7v35x Provide a very brief explanation (1-2 sentences max). URL: " + url)
                .build();
        Executor backgroundExecutor = appExecutors.diskIO(); // Use your AppExecutors
        ListenableFuture<GenerateContentResponse> future = geminiModel.generateContent(prompt);

        Futures.addCallback(future, new FutureCallback<GenerateContentResponse>() {
            @Override
            public void onSuccess(GenerateContentResponse result) {
                String geminiResponseText = "";
                try {
                    geminiResponseText = result.getText();
                    if (geminiResponseText == null || geminiResponseText.isEmpty()) {
                        geminiResponseText = getString(R.string.gemini_rating_error) + " (Empty Response)";
                    }
                    Log.d(TAG, "Gemini Result: " + geminiResponseText);
                } catch (Exception e) { // Catch any exception during text extraction
                    Log.e(TAG, "Error extracting text from Gemini response", e);
                    geminiResponseText = getString(R.string.gemini_rating_error) + " (Parse Error)";
                }
                final String finalResponse = geminiResponseText;
                appExecutors.mainThread().execute(() -> {
                    updateGeminiDisplay(finalResponse);
                    geminiScanComplete = true;
                    checkCompletionAndFinalizeUI();
                });
            }

            @Override
            public void onFailure(Throwable t) {
                Log.e(TAG, "Gemini Scan Error: " + t.getMessage(), t);
                appExecutors.mainThread().execute(() -> {
                    updateGeminiDisplay(getString(R.string.gemini_rating_error) + ": " + t.getMessage());
                    geminiScanComplete = true;
                    checkCompletionAndFinalizeUI();
                });
            }
        }, backgroundExecutor);
    }

    private void updateOverallStatusUI(boolean isSafe, String statusMessage, String detailsMessage) {
        statusText.setText(statusMessage);
        statusText.setVisibility(View.VISIBLE);

        if (detailsMessage != null && !detailsMessage.isEmpty()) {
            detailsText.setText(detailsMessage);
            detailsText.setVisibility(View.VISIBLE);
        } else {
            detailsText.setVisibility(View.GONE);
        }

        if (isSafe) {
            statusIcon.setImageResource(R.drawable.ic_check_circle_green);
            statusIcon.setColorFilter(ContextCompat.getColor(this, android.R.color.holo_green_dark));
        } else {
            statusIcon.setImageResource(R.drawable.ic_warning_red);
            statusIcon.setColorFilter(ContextCompat.getColor(this, android.R.color.holo_red_dark));
        }
        statusIcon.setVisibility(View.VISIBLE);
    }

    private void updateGeminiDisplay(String rating) {
        geminiRatingLabel.setVisibility(View.VISIBLE);
        geminiRatingText.setVisibility(View.VISIBLE);
        geminiRatingText.setText(rating);
    }

    private void checkCompletionAndFinalizeUI() {
        // This method is called when an async operation finishes or a synchronous path completes.
        // We only proceed to hide loading and show final button state if ALL relevant operations are done.

        if (QRScannerActivity.PAYLOAD_TYPE_URL.equals(payloadType)) {
            if (!vtScanComplete || !geminiScanComplete) {
                Log.d(TAG, "URL Scan: Waiting for all scans to complete (VT: " + vtScanComplete + ", Gemini: " + geminiScanComplete + ")");
                return; // Not all scans are done yet for URL type
            }
        }
        // For SIGNED_PAYMENT, vtScanComplete and geminiScanComplete were set to true earlier.

        showLoadingState(false); // Hide progress bar, show result layout
        Log.d(TAG, "All relevant processes complete. Finalizing UI for payload type: " + payloadType);

        // Determine overall proceed logic based on the payload type and results
        if (QRScannerActivity.PAYLOAD_TYPE_SIGNED_PAYMENT.equals(payloadType)) {
            proceedAllowed = signatureVerified; // Based on signature check performed in handleSignedPayment
        } else if (QRScannerActivity.PAYLOAD_TYPE_URL.equals(payloadType)) {
            proceedAllowed = isVtSafe; // For URLs, base on VirusTotal primarily
            if (isVtSafe && !isGeminiRatingPositive(geminiRatingText.getText().toString())) {
                // Optionally show a non-blocking warning if VT is safe but Gemini is negative
                Toast.makeText(this, "Note: URL scan clear, but AI suggests caution.", Toast.LENGTH_LONG).show();
            }
        }
        // The 'default' case in the main switch handles unknown types by setting proceedAllowed = false.
        // No need for an explicit 'else if PAYLOAD_TYPE_OTHER' here as it's covered by default.

        // Set visibility of the proceed button
        proceedButton.setVisibility(proceedAllowed ? View.VISIBLE : View.GONE);
    }

    private boolean isGeminiRatingPositive(String geminiResult) {
        if (geminiResult == null || geminiResult.isEmpty() ||
                geminiResult.startsWith(getString(R.string.gemini_rating_error)) ||
                geminiResult.equals(getString(R.string.gemini_rating_loading))) {
            return true; // Default to positive (or neutral) if error, loading, or no meaningful response
        }
        String lowerResult = geminiResult.toLowerCase();
        // Look for negative keywords - refine this based on typical responses
        return !lowerResult.contains("malicious") &&
                !lowerResult.contains("risky") &&
                !lowerResult.contains("unsafe") &&
                !lowerResult.contains("warning") &&
                !lowerResult.contains("caution");
    }

    private void proceedToPayment() {
        if (originalPaymentData != null && !originalPaymentData.isEmpty()) {
            PaymentConfirmationDialog dialog = new PaymentConfirmationDialog(this, originalPaymentData, payloadType);
            dialog.setOnDismissListener(d -> {
                // Optional: You might want to finish ScanResultActivity only if payment was confirmed
                // or always finish it. For now, always finish.
                finish();
            });
            dialog.show();
        } else {
            Toast.makeText(this, R.string.error_generic, Toast.LENGTH_SHORT).show();
            finish(); // Close if there's no data to proceed with
        }
    }

    /**
     * Creates a canonical string representation of a JSONObject for signing/verification.
     * Sorts keys alphabetically and concatenates key=value pairs with '&'.
     * This MUST be identical to the method used in GenerateQrActivity.
     */
    private String createCanonicalString(JSONObject json) {
        if (json == null) return null;
        try {
            Map<String, String> sortedMap = new TreeMap<>();
            java.util.Iterator<String> keys = json.keys();
            while (keys.hasNext()) {
                String key = keys.next();
                // Ensure values are strings; adjust if other types are directly in dataToSign
                sortedMap.put(key, json.getString(key));
            }
            StringBuilder sb = new StringBuilder();
            boolean first = true;
            for (Map.Entry<String, String> entry : sortedMap.entrySet()) {
                if (!first) {
                    sb.append("&");
                }
                // Basic URL encoding might be needed if values can contain '&' or '='
                // For simplicity, assuming values are simple for now.
                // sb.append(URLEncoder.encode(entry.getKey(), StandardCharsets.UTF_8.name()))
                //   .append("=")
                //   .append(URLEncoder.encode(entry.getValue(), StandardCharsets.UTF_8.name()));
                sb.append(entry.getKey()).append("=").append(entry.getValue());
                first = false;
            }
            return sb.toString();
        } catch (JSONException e) {
            Log.e(TAG, "Could not create canonical string from JSON for verification", e);
            return null;
        }
        // catch (UnsupportedEncodingException e) { // If using URLEncoder
        //     Log.e(TAG, "UTF-8 not supported for canonical string URLEncoding?", e);
        //     return null;
        // }
    }
}
