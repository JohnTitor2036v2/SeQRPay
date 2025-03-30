package com.example.seqrpay;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.FormBody;
// import okhttp3.HttpUrl; // Unused import
// import okhttp3.MediaType; // Unused import
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

public class VirusTotalApi {
    private static final String TAG = "VirusTotalApi";

    // --- REMOVED HARDCODED API KEY ---
    // private static final String API_KEY = "YOUR_API_KEY_HERE"; // <<<=== REMOVED

    // Get API Key securely from BuildConfig (See instructions below)
    private static final String API_KEY = BuildConfig.VIRUSTOTAL_API_KEY;

    private static final String SCAN_URL_ENDPOINT = "https://www.virustotal.com/api/v3/urls";
    private static final String GET_REPORT_ENDPOINT = "https://www.virustotal.com/api/v3/analyses/";

    private final OkHttpClient client;
    private final Handler mainHandler;
    private final Context context; // Keep context if needed for other things (e.g., resources)

    public interface ScanCallback {
        void onResult(boolean isSafe, String message);
        void onError(String error);
    }

    public VirusTotalApi(Context context) {
        this.context = context.getApplicationContext(); // Use application context if possible
        this.client = new OkHttpClient.Builder()
                .connectTimeout(30, TimeUnit.SECONDS)
                .readTimeout(30, TimeUnit.SECONDS)
                .writeTimeout(30, TimeUnit.SECONDS)
                .build();
        this.mainHandler = new Handler(Looper.getMainLooper());

        // Check if API key is set properly
        if (API_KEY == null || API_KEY.isEmpty() || API_KEY.equals("YOUR_API_KEY_HERE")) {
            Log.e(TAG, "VirusTotal API Key is not set correctly in BuildConfig!");
            // Consider disabling functionality or showing an error to the user
        }
    }

    public void scanUrl(final String url, final ScanCallback callback) {
        if (API_KEY == null || API_KEY.isEmpty() || API_KEY.equals("YOUR_API_KEY_HERE")) {
            runOnMainThread(() -> callback.onError("API Key not configured."));
            return;
        }

        // Step 1: Submit URL for scanning
        RequestBody formBody = new FormBody.Builder()
                .add("url", url)
                .build();

        Request request = new Request.Builder()
                .url(SCAN_URL_ENDPOINT)
                .addHeader("x-apikey", API_KEY) // Use the key from BuildConfig
                .post(formBody)
                .build();

        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                final String errorMsg = "Network error: " + e.getMessage();
                Log.e(TAG, errorMsg, e); // Log exception too
                runOnMainThread(() -> callback.onError(errorMsg));
            }

            @Override
            public void onResponse(Call call, Response response) { // Removed 'throws IOException' - handled below
                // Ensure response body is closed to prevent resource leaks
                try (Response resp = response) {
                    if (!resp.isSuccessful()) {
                        final String errorMsg = "Server error: " + resp.code() + " " + resp.message();
                        Log.e(TAG, errorMsg + " Body: " + resp.body().string()); // Log error body
                        runOnMainThread(() -> callback.onError(errorMsg));
                        return;
                    }

                    String responseBody = resp.body().string(); // Read body once
                    JSONObject jsonResponse = new JSONObject(responseBody);
                    // Defensive coding: Check if 'data' and 'id' exist
                    if (jsonResponse.has("data") && jsonResponse.getJSONObject("data").has("id")) {
                        String analysisId = jsonResponse.getJSONObject("data").getString("id");

                        // Step 2: Wait a moment for analysis and then get the report
                        // Consider a more robust polling mechanism
                        mainHandler.postDelayed(() -> getAnalysisReport(analysisId, callback), 5000); // Increased delay slightly

                    } else {
                        final String errorMsg = "Error parsing scan response: Missing 'data' or 'id'.";
                        Log.e(TAG, errorMsg + " Response: " + responseBody);
                        runOnMainThread(() -> callback.onError(errorMsg));
                    }

                } catch (IOException | JSONException e) { // Catch IOException here
                    final String errorMsg = "Error processing scan response: " + e.getMessage();
                    Log.e(TAG, errorMsg, e);
                    runOnMainThread(() -> callback.onError(errorMsg));
                }
            }
        });
    }

    private void getAnalysisReport(String analysisId, final ScanCallback callback) {
        if (API_KEY == null || API_KEY.isEmpty() || API_KEY.equals("YOUR_API_KEY_HERE")) {
            runOnMainThread(() -> callback.onError("API Key not configured."));
            return;
        }

        Request request = new Request.Builder()
                .url(GET_REPORT_ENDPOINT + analysisId)
                .addHeader("x-apikey", API_KEY) // Use the key from BuildConfig
                .get()
                .build();

        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                final String errorMsg = "Network error getting report: " + e.getMessage();
                Log.e(TAG, errorMsg, e);
                runOnMainThread(() -> callback.onError(errorMsg));
            }

            @Override
            public void onResponse(Call call, Response response) { // Removed 'throws IOException'
                try (Response resp = response) {
                    if (!resp.isSuccessful()) {
                        final String errorMsg = "Server error getting report: " + resp.code() + " " + resp.message();
                        Log.e(TAG, errorMsg + " Body: " + resp.body().string()); // Log error body
                        runOnMainThread(() -> callback.onError(errorMsg));
                        return;
                    }

                    String responseBody = resp.body().string(); // Read body once
                    JSONObject jsonResponse = new JSONObject(responseBody);

                    // Defensive coding
                    if (!jsonResponse.has("data") || !jsonResponse.getJSONObject("data").has("attributes")) {
                        final String errorMsg = "Error parsing report: Missing 'data' or 'attributes'.";
                        Log.e(TAG, errorMsg + " Response: " + responseBody);
                        runOnMainThread(() -> callback.onError(errorMsg));
                        return;
                    }

                    JSONObject attributes = jsonResponse.getJSONObject("data").getJSONObject("attributes");

                    // Get status
                    String status = attributes.optString("status", "unknown"); // Use optString for safety

                    if (status.equals("completed")) {
                        if (!attributes.has("stats")) {
                            final String errorMsg = "Error parsing report: Missing 'stats'.";
                            Log.e(TAG, errorMsg + " Response: " + responseBody);
                            runOnMainThread(() -> callback.onError(errorMsg));
                            return;
                        }
                        // Check the stats section for malicious results
                        JSONObject stats = attributes.getJSONObject("stats");
                        int malicious = stats.optInt("malicious", 0);
                        int suspicious = stats.optInt("suspicious", 0);
                        // int harmless = stats.optInt("harmless", 0); // Not strictly needed for safety decision
                        // int undetected = stats.optInt("undetected", 0);

                        // Determine if the URL is safe based on the results
                        boolean isSafe = (malicious == 0 && suspicious == 0); // Simplified safety check

                        final boolean finalIsSafe = isSafe;
                        String message;

                        if (isSafe) {
                            message = "URL scan complete. No threats detected.";
                        } else {
                            message = "Security Alert: URL flagged as potentially unsafe ("
                                    + malicious + " malicious, " + suspicious + " suspicious detections).";
                        }

                        final String finalMessage = message;
                        runOnMainThread(() -> callback.onResult(finalIsSafe, finalMessage));

                    } else if (status.equals("queued") || status.equals("inprogress")) {
                        // Analysis not completed yet, retry after a delay
                        Log.i(TAG, "Analysis status: " + status + ". Retrying report fetch for " + analysisId);
                        mainHandler.postDelayed(() -> getAnalysisReport(analysisId, callback), 5000); // Retry delay
                    }
                    else {
                        // Handle unexpected status (e.g., failed)
                        final String errorMsg = "Analysis status: " + status + ". Unable to get results.";
                        Log.w(TAG, errorMsg + " Response: " + responseBody);
                        runOnMainThread(() -> callback.onError(errorMsg));
                    }

                } catch (IOException | JSONException e) {
                    final String errorMsg = "Error processing report response: " + e.getMessage();
                    Log.e(TAG, errorMsg, e);
                    runOnMainThread(() -> callback.onError(errorMsg));
                }
            }
        });
    }

    // Helper method to run callbacks on the main thread
    private void runOnMainThread(Runnable runnable) {
        mainHandler.post(runnable);
    }

    // --- REMOVED Simplified test method ---
    // public void testScanUrl(final String url, final ScanCallback callback) { ... }
}