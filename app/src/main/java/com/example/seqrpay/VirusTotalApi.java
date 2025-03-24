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
import okhttp3.HttpUrl;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

public class VirusTotalApi {
    private static final String TAG = "VirusTotalApi";
    private static final String API_KEY = "YOUR_VIRUS_TOTAL_API_KEY"; // Replace with your actual API key
    private static final String SCAN_URL_ENDPOINT = "https://www.virustotal.com/api/v3/urls";
    private static final String GET_REPORT_ENDPOINT = "https://www.virustotal.com/api/v3/analyses/";

    private final OkHttpClient client;
    private final Handler mainHandler;
    private final Context context;

    public interface ScanCallback {
        void onResult(boolean isSafe, String message);
        void onError(String error);
    }

    public VirusTotalApi(Context context) {
        this.context = context;
        this.client = new OkHttpClient.Builder()
                .connectTimeout(30, TimeUnit.SECONDS)
                .readTimeout(30, TimeUnit.SECONDS)
                .writeTimeout(30, TimeUnit.SECONDS)
                .build();
        this.mainHandler = new Handler(Looper.getMainLooper());
    }

    public void scanUrl(final String url, final ScanCallback callback) {
        // Step 1: Submit URL for scanning
        RequestBody formBody = new FormBody.Builder()
                .add("url", url)
                .build();

        Request request = new Request.Builder()
                .url(SCAN_URL_ENDPOINT)
                .addHeader("x-apikey", API_KEY)
                .post(formBody)
                .build();

        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                final String errorMsg = "Network error: " + e.getMessage();
                Log.e(TAG, errorMsg);
                runOnMainThread(() -> callback.onError(errorMsg));
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                if (!response.isSuccessful()) {
                    final String errorMsg = "Server error: " + response.code() + " " + response.message();
                    Log.e(TAG, errorMsg);
                    runOnMainThread(() -> callback.onError(errorMsg));
                    return;
                }

                try {
                    String responseBody = response.body().string();
                    JSONObject jsonResponse = new JSONObject(responseBody);
                    String analysisId = jsonResponse.getJSONObject("data").getString("id");

                    // Step 2: Wait a moment for analysis and then get the report
                    // In a real app, you might want to implement a polling mechanism or webhook
                    mainHandler.postDelayed(() -> getAnalysisReport(analysisId, callback), 3000);

                } catch (JSONException e) {
                    final String errorMsg = "Error parsing response: " + e.getMessage();
                    Log.e(TAG, errorMsg);
                    runOnMainThread(() -> callback.onError(errorMsg));
                }
            }
        });
    }

    private void getAnalysisReport(String analysisId, final ScanCallback callback) {
        Request request = new Request.Builder()
                .url(GET_REPORT_ENDPOINT + analysisId)
                .addHeader("x-apikey", API_KEY)
                .get()
                .build();

        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                final String errorMsg = "Network error getting report: " + e.getMessage();
                Log.e(TAG, errorMsg);
                runOnMainThread(() -> callback.onError(errorMsg));
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                if (!response.isSuccessful()) {
                    final String errorMsg = "Server error getting report: " + response.code() + " " + response.message();
                    Log.e(TAG, errorMsg);
                    runOnMainThread(() -> callback.onError(errorMsg));
                    return;
                }

                try {
                    String responseBody = response.body().string();
                    JSONObject jsonResponse = new JSONObject(responseBody);
                    JSONObject attributes = jsonResponse.getJSONObject("data").getJSONObject("attributes");

                    // Get status
                    String status = attributes.getString("status");

                    if (status.equals("completed")) {
                        // Check the stats section for malicious results
                        JSONObject stats = attributes.getJSONObject("stats");
                        int malicious = stats.getInt("malicious");
                        int suspicious = stats.getInt("suspicious");
                        int harmless = stats.getInt("harmless");
                        int undetected = stats.getInt("undetected");

                        // Determine if the URL is safe based on the results
                        boolean isSafe = (malicious == 0 && suspicious == 0) ||
                                (harmless > 0 && malicious == 0);

                        final boolean finalIsSafe = isSafe;
                        String message;

                        if (isSafe) {
                            message = "URL appears to be safe.";
                        } else {
                            message = "URL may be malicious. Detected by " + malicious +
                                    " security vendors as malicious and " + suspicious +
                                    " as suspicious.";
                        }

                        final String finalMessage = message;
                        runOnMainThread(() -> callback.onResult(finalIsSafe, finalMessage));

                    } else {
                        // Analysis not completed yet, implement polling or retry logic
                        // For simplicity, we'll just notify the user
                        runOnMainThread(() -> callback.onError("Analysis not yet complete. Please try again later."));
                    }

                } catch (JSONException e) {
                    final String errorMsg = "Error parsing report: " + e.getMessage();
                    Log.e(TAG, errorMsg);
                    runOnMainThread(() -> callback.onError(errorMsg));
                }
            }
        });
    }

    // Helper method to run callbacks on the main thread
    private void runOnMainThread(Runnable runnable) {
        mainHandler.post(runnable);
    }

    // Simplified method for testing/demo without actual API call
    public void testScanUrl(final String url, final ScanCallback callback) {
        // Simulate network delay
        mainHandler.postDelayed(() -> {
            // Simple URL validation - in a real app you'd use the actual API
            boolean isSafe = !url.contains("malware") &&
                    !url.contains("phishing") &&
                    !url.contains("suspicious") &&
                    (url.startsWith("https://") ||
                            url.contains("trusted-domain.com"));

            String message = isSafe ?
                    "URL appears to be safe." :
                    "URL may be unsafe. Please proceed with caution.";

            callback.onResult(isSafe, message);
        }, 1500);
    }
}
