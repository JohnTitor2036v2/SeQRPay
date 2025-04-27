package com.example.seqrpay;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log; // Added Log
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

// Import Gemini classes (Make sure SDK dependency is added)
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

import java.util.Collections;
import java.util.concurrent.Executor; // Use java.util.concurrent.Executor

public class ScanResultActivity extends AppCompatActivity {

    public static final String EXTRA_URL_TO_SCAN = "com.example.seqrpay.EXTRA_URL_TO_SCAN";
    public static final String EXTRA_PAYMENT_DATA = "com.example.seqrpay.EXTRA_PAYMENT_DATA";

    private static final String TAG = "ScanResultActivity"; // Logging Tag

    private ProgressBar progressBar;
    private LinearLayout resultLayout;
    // VirusTotal UI
    private ImageView vtStatusIcon;
    private TextView vtStatusText;
    private TextView vtDetailsText;
    // Gemini UI
    private TextView geminiRatingLabel;
    private TextView geminiRatingText;
    // Buttons
    private Button proceedButton;
    private Button cancelButton;

    private VirusTotalApi virusTotalApi;
    private AppExecutors appExecutors; // For background tasks
    private GenerativeModelFutures geminiModel; // Gemini Futures API

    private String urlToScan;
    private String originalPaymentData;

    // State tracking for parallel calls
    private boolean vtScanComplete = false;
    private boolean geminiScanComplete = false;
    private boolean isVtSafe = false;
    private boolean proceedAllowed = false; // Master flag for proceeding

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_scan_result);

        // --- Initialize UI ---
        progressBar = findViewById(R.id.scan_progress_bar);
        resultLayout = findViewById(R.id.scan_result_layout);
        // VirusTotal UI
        vtStatusIcon = findViewById(R.id.scan_status_icon); // Renamed ID in thought, assume scan_status_icon
        vtStatusText = findViewById(R.id.scan_status_text); // Renamed ID in thought, assume scan_status_text
        vtDetailsText = findViewById(R.id.scan_details_text); // Renamed ID in thought, assume scan_details_text
        // Gemini UI
        geminiRatingLabel = findViewById(R.id.gemini_rating_label);
        geminiRatingText = findViewById(R.id.gemini_rating_text);
        // Buttons
        proceedButton = findViewById(R.id.btn_proceed_payment);
        cancelButton = findViewById(R.id.btn_scan_cancel);

        // --- Initialize Services ---
        virusTotalApi = new VirusTotalApi(this);
        appExecutors = AppExecutors.getInstance(); // Get executor instance
        initializeGemini(); // Initialize Gemini Model

        // --- Get Data from Intent ---
        urlToScan = getIntent().getStringExtra(EXTRA_URL_TO_SCAN);
        originalPaymentData = getIntent().getStringExtra(EXTRA_PAYMENT_DATA);

        // --- Initial UI State ---
        progressBar.setVisibility(View.VISIBLE);
        resultLayout.setVisibility(View.GONE);
        geminiRatingText.setText(R.string.gemini_rating_loading); // Show loading for Gemini too

        // --- Start Scans (if URL exists) ---
        if (urlToScan != null && !urlToScan.isEmpty()) {
            Log.d(TAG, "Starting scans for URL: " + urlToScan);
            startVirusTotalScan(urlToScan);
            startGeminiScan(urlToScan);
        } else {
            // No URL to scan (maybe just payment data)
            // Decide how to handle: Show "Safe" immediately? Show different UI?
            Log.w(TAG, "No URL provided to scan.");
            // For now, treat as safe but without scan details
            updateVirusTotalResult(true, "No URL provided for scanning.");
            updateGeminiResult("N/A - No URL provided.");
            checkCompletionAndShowResult(); // Update UI based on default state
        }

        // --- Button Listeners ---
        proceedButton.setOnClickListener(v -> proceedToPayment());
        cancelButton.setOnClickListener(v -> finish());
    }

    private void initializeGemini() {
        // Make sure GEMINI_API_KEY is set in BuildConfig via local.properties
        String apiKey = BuildConfig.GEMINI_API_KEY;
        if (apiKey == null || apiKey.isEmpty() || apiKey.equals("YOUR_API_KEY_HERE")) {
            Log.e(TAG, "Gemini API Key not configured in BuildConfig!");
            // Handle error - maybe disable Gemini feature
            geminiRatingText.setText(R.string.gemini_rating_error);
            geminiScanComplete = true; // Mark as 'complete' to not block UI
            return;
        }

        // Basic safety settings - adjust as needed
        SafetySetting harassmentSafety = new SafetySetting(HarmCategory.HARASSMENT, BlockThreshold.ONLY_HIGH);
        // Add other categories if needed: HATE_SPEECH, SEXUALLY_EXPLICIT, DANGEROUS_CONTENT
        GenerationConfig.Builder configBuilder = new GenerationConfig.Builder();
        // configBuilder.temperature = 0.9f; // Example config
        // configBuilder.topK = 1;
        // configBuilder.topP = 1f;
        // configBuilder.maxOutputTokens = 2048;

        GenerativeModel googleAiModel = new GenerativeModel(
                "gemini-1.5-flash", // Or another suitable model
                apiKey,
                configBuilder.build(),
                Collections.singletonList(harassmentSafety) // Add more safety settings if needed
        );
        geminiModel = GenerativeModelFutures.from(googleAiModel);
    }

    private void startVirusTotalScan(String url) {
        virusTotalApi.scanUrl(url, new VirusTotalApi.ScanCallback() {
            @Override
            public void onResult(boolean isSafe, String message) {
                Log.d(TAG, "VirusTotal Result: isSafe=" + isSafe + ", message=" + message);
                isVtSafe = isSafe; // Store VT safety status
                updateVirusTotalResult(isSafe, message);
                vtScanComplete = true;
                checkCompletionAndShowResult();
            }

            @Override
            public void onError(String error) {
                Log.e(TAG, "VirusTotal Error: " + error);
                isVtSafe = false; // Treat errors as unsafe for caution
                updateVirusTotalResult(false, getString(R.string.scan_result_error) + ": " + error);
                vtScanComplete = true;
                checkCompletionAndShowResult();
            }
        });
    }

    private void startGeminiScan(String url) {
        if (geminiModel == null) {
            Log.e(TAG, "Gemini model not initialized, skipping scan.");
            updateGeminiResult(getString(R.string.gemini_rating_error)); // Show error in UI
            geminiScanComplete = true;
            checkCompletionAndShowResult();
            return;
        }

        // Construct the prompt
        Content prompt = new Content.Builder()
                .addText("Analyze the trustworthiness of this URL for a secure payment app user. Is it safe, potentially risky, or malicious? Provide a very brief explanation (1-2 sentences max). URL: " + url)
                .build();

        // Use the background executor from AppExecutors
        Executor backgroundExecutor = appExecutors.diskIO();
        ListenableFuture<GenerateContentResponse> future = geminiModel.generateContent(prompt);

        Futures.addCallback(future, new FutureCallback<GenerateContentResponse>() {
            @Override
            public void onSuccess(GenerateContentResponse result) {
                // This callback might not be on the main thread, use AppExecutors to post UI update
                String geminiResponseText = "";
                try {
                    // Basic text extraction, might need more robust parsing depending on model output
                    geminiResponseText = result.getText();
                    Log.d(TAG, "Gemini Result: " + geminiResponseText);
                } catch (Exception e) {
                    Log.e(TAG, "Error extracting text from Gemini response", e);
                    geminiResponseText = getString(R.string.gemini_rating_error) + " (Parse Error)";
                }
                final String finalResponse = geminiResponseText;
                appExecutors.mainThread().execute(() -> {
                    updateGeminiResult(finalResponse);
                    geminiScanComplete = true;
                    checkCompletionAndShowResult();
                });
            }

            @Override
            public void onFailure(Throwable t) {
                Log.e(TAG, "Gemini Error: " + t.getMessage(), t);
                appExecutors.mainThread().execute(() -> {
                    updateGeminiResult(getString(R.string.gemini_rating_error));
                    geminiScanComplete = true;
                    checkCompletionAndShowResult();
                });
            }
        }, backgroundExecutor); // Ensure the callback itself is processed by the background executor first
    }

    // --- UI Update Methods (run on main thread) ---

    private void updateVirusTotalResult(boolean isSafe, String message) {
        // Update VirusTotal specific UI elements
        vtDetailsText.setText(message);
        if (isSafe) {
            vtStatusIcon.setImageResource(R.drawable.ic_check_circle_green);
            vtStatusIcon.setColorFilter(ContextCompat.getColor(this, android.R.color.holo_green_dark));
            vtStatusText.setText(R.string.scan_result_safe);
        } else {
            vtStatusIcon.setImageResource(R.drawable.ic_warning_red);
            vtStatusIcon.setColorFilter(ContextCompat.getColor(this, android.R.color.holo_red_dark));
            vtStatusText.setText(isVtSafe ? R.string.scan_result_safe : R.string.scan_result_unsafe); // Use stored safety status
            if(message.startsWith(getString(R.string.scan_result_error))){ // Check if it was an error message
                vtStatusText.setText(R.string.scan_result_error);
            }
        }
    }

    private void updateGeminiResult(String rating) {
        // Update Gemini specific UI elements
        geminiRatingText.setText(rating);
        // You could add logic here to parse Gemini's response for keywords like "safe", "risky", "malicious"
        // and potentially set another icon or color for the Gemini rating text.
    }


    // Checks if both scans are done and updates the final UI state
    private void checkCompletionAndShowResult() {
        if (vtScanComplete && geminiScanComplete) {
            Log.d(TAG, "Both scans complete. Updating final UI.");
            progressBar.setVisibility(View.GONE);
            resultLayout.setVisibility(View.VISIBLE);

            // --- Determine overall safety and if proceed is allowed ---
            // Example Logic: Require VirusTotal to be safe. Gemini is advisory.
            proceedAllowed = isVtSafe; // Base decision on VirusTotal result primarily

            // Modify logic based on your requirements. E.g., require both?
            // proceedAllowed = isVtSafe && isGeminiRatingPositive(geminiRatingText.getText().toString());

            if (proceedAllowed) {
                proceedButton.setVisibility(View.VISIBLE);
            } else {
                proceedButton.setVisibility(View.GONE);
                // Maybe show an extra warning if VT was safe but Gemini was negative?
                if (isVtSafe && !isGeminiRatingPositive(geminiRatingText.getText().toString())) {
                    Toast.makeText(this, "Note: VirusTotal scan clear, but Gemini suggests caution.", Toast.LENGTH_LONG).show();
                }
            }
        } else {
            Log.d(TAG, "Waiting for scans to complete (VT: " + vtScanComplete + ", Gemini: " + geminiScanComplete + ")");
            // Keep progress bar visible, result layout hidden until both are done
            progressBar.setVisibility(View.VISIBLE);
            resultLayout.setVisibility(View.GONE);
        }
    }

    // Helper to interpret Gemini's text result (very basic example)
    private boolean isGeminiRatingPositive(String geminiResult) {
        if (geminiResult == null) return true; // Default to okay if error/no result
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
            PaymentConfirmationDialog dialog = new PaymentConfirmationDialog(this, originalPaymentData);
            dialog.show();
            dialog.setOnDismissListener(d -> finish());
        } else {
            Toast.makeText(this, R.string.error_generic, Toast.LENGTH_SHORT).show();
            finish();
        }
    }
}