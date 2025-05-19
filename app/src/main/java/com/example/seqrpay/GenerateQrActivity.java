package com.example.seqrpay;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.material.textfield.TextInputEditText;
import com.google.zxing.BarcodeFormat;
import com.google.zxing.MultiFormatWriter;
import com.google.zxing.WriterException;
import com.google.zxing.common.BitMatrix;

import org.json.JSONException;
import org.json.JSONObject;

import java.security.PrivateKey;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;
import java.util.TreeMap; // For canonical string
import java.util.Map; // For canonical string

public class GenerateQrActivity extends AppCompatActivity {

    private static final String TAG = "GenerateQrActivity";

    private TextView tvPayeeUsername, tvQrPayloadDebug;
    private TextInputEditText etAmount, etCurrency;
    private Button btnGenerateQr;
    private ImageView ivQrCode;

    private String currentLoggedInUsername;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_generate_qr);

        // Initialize UI elements
        tvPayeeUsername = findViewById(R.id.tv_payee_username);
        etAmount = findViewById(R.id.et_amount);
        etCurrency = findViewById(R.id.et_currency); // Assuming you added this ID in XML
        btnGenerateQr = findViewById(R.id.btn_generate_qr);
        ivQrCode = findViewById(R.id.iv_qr_code);
        tvQrPayloadDebug = findViewById(R.id.tv_qr_payload_debug);

        // Retrieve the logged-in username
        SharedPreferences prefs = getSharedPreferences(LoginActivity.SHARED_PREFS_NAME, MODE_PRIVATE);
        currentLoggedInUsername = prefs.getString(LoginActivity.KEY_LOGGED_IN_USERNAME, null);

        if (currentLoggedInUsername == null || currentLoggedInUsername.isEmpty()) {
            Toast.makeText(this, "Error: Not logged in.", Toast.LENGTH_LONG).show();
            Log.e(TAG, "Current logged in username is null or empty.");
            finish(); // Close activity if no user is logged in
            return;
        }

        tvPayeeUsername.setText(currentLoggedInUsername);

        // Ensure key pair exists for the current user. This might take a moment if generating.
        // Consider running this in a background thread if it causes UI lag on first run.
        // For simplicity here, it's on the main thread.
        UserKeyPairManager.ensureKeyPairExists(this, currentLoggedInUsername);

        btnGenerateQr.setOnClickListener(v -> generateSignedQrCode());
    }

    private void generateSignedQrCode() {
        String amountStr = etAmount.getText().toString().trim();
        String currencyStr = etCurrency.getText().toString().trim().toUpperCase();

        if (amountStr.isEmpty()) {
            etAmount.setError("Amount cannot be empty");
            etAmount.requestFocus();
            return;
        }
        if (currencyStr.isEmpty() || currencyStr.length() != 3) {
            etCurrency.setError("Enter a valid 3-letter currency code");
            etCurrency.requestFocus();
            return;
        }
        try {
            double amountDouble = Double.parseDouble(amountStr);
            if (amountDouble <= 0) {
                etAmount.setError("Amount must be positive");
                return;
            }
        } catch (NumberFormatException e) {
            etAmount.setError("Invalid amount format");
            return;
        }


        PrivateKey privateKey = UserKeyPairManager.getUserPrivateKey(currentLoggedInUsername);
        if (privateKey == null) {
            Toast.makeText(this, "Error: Could not retrieve private key for signing.", Toast.LENGTH_LONG).show();
            Log.e(TAG, "Private key is null for user: " + currentLoggedInUsername);
            return;
        }

        // 1. Prepare data for signing
        JSONObject dataToSignJson = new JSONObject();
        String timestamp = getIso8601Timestamp();
        try {
            dataToSignJson.put("payeeUsername", currentLoggedInUsername);
            dataToSignJson.put("amount", amountStr);
            dataToSignJson.put("currency", currencyStr);
            dataToSignJson.put("timestamp", timestamp);
            // Add any other fields you want to be part of the signed data
        } catch (JSONException e) {
            Log.e(TAG, "Error creating JSON for dataToSign", e);
            Toast.makeText(this, "Error preparing data.", Toast.LENGTH_SHORT).show();
            return;
        }

        // 2. Create Canonical String from dataToSignJson for reliable signature
        // A common way is to sort keys alphabetically and concatenate key=value pairs
        String canonicalDataToSign = createCanonicalString(dataToSignJson);
        if (canonicalDataToSign == null) {
            Log.e(TAG, "Failed to create canonical string for signing.");
            Toast.makeText(this, "Error preparing signature data.", Toast.LENGTH_SHORT).show();
            return;
        }
        Log.d(TAG, "Canonical data to sign: " + canonicalDataToSign);


        // 3. Sign the canonical data
        String signatureBase64;
        try {
            signatureBase64 = SecurityUtils.signData(canonicalDataToSign, privateKey);
            if (signatureBase64 == null) {
                throw new Exception("Signing returned null");
            }
        } catch (Exception e) {
            Log.e(TAG, "Error signing data", e);
            Toast.makeText(this, "Error signing QR data.", Toast.LENGTH_LONG).show();
            return;
        }
        Log.d(TAG, "Generated Signature: " + signatureBase64);

        // 4. Construct the full QR code payload
        JSONObject qrPayloadJson = new JSONObject();
        try {
            qrPayloadJson.put("type", "paymentRequest");
            qrPayloadJson.put("version", "1.0");
            qrPayloadJson.put("payeeUsername", currentLoggedInUsername); // Redundant but can be useful for quick display
            qrPayloadJson.put("amount", amountStr); // Redundant
            qrPayloadJson.put("currency", currencyStr); // Redundant
            qrPayloadJson.put("timestamp", timestamp); // Redundant for display, but part of outer object too
            qrPayloadJson.put("dataToSign", dataToSignJson); // The actual signed data block
            qrPayloadJson.put("signatureAlgorithm", SecurityUtils.SIGNATURE_ALGORITHM); // Good practice to include
            qrPayloadJson.put("signature", signatureBase64);
        } catch (JSONException e) {
            Log.e(TAG, "Error creating final QR payload JSON", e);
            Toast.makeText(this, "Error creating QR payload.", Toast.LENGTH_SHORT).show();
            return;
        }

        String finalQrPayload = qrPayloadJson.toString();
        Log.i(TAG, "Final QR Payload: " + finalQrPayload);
        tvQrPayloadDebug.setText("QR Payload: " + finalQrPayload);
        tvQrPayloadDebug.setVisibility(View.VISIBLE);


        // 5. Generate and display QR code image
        try {
            Bitmap bitmap = encodeAsBitmap(finalQrPayload, 600, 600); // Adjust size as needed
            if (bitmap != null) {
                ivQrCode.setImageBitmap(bitmap);
                ivQrCode.setBackgroundColor(ContextCompat.getColor(this, android.R.color.transparent)); // Remove placeholder background
            } else {
                Toast.makeText(this, "Failed to generate QR code image.", Toast.LENGTH_SHORT).show();
                ivQrCode.setImageResource(R.drawable.ic_warning_red); // Show an error icon
            }
        } catch (WriterException e) {
            Log.e(TAG, "Error generating QR code bitmap", e);
            Toast.makeText(this, "Error displaying QR code.", Toast.LENGTH_SHORT).show();
            ivQrCode.setImageResource(R.drawable.ic_warning_red);
        }
    }

    private String getIso8601Timestamp() {
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US);
        sdf.setTimeZone(TimeZone.getTimeZone("UTC"));
        return sdf.format(new Date());
    }

    /**
     * Creates a canonical string representation of a JSONObject for signing.
     * Sorts keys alphabetically and concatenates key=value pairs with '&'.
     * Example: {"b": "valB", "a": "valA"} -> "a=valA&b=valB"
     * This ensures that the string to sign is always the same if the data is the same,
     * regardless of JSON key order.
     *
     * @param json The JSONObject to canonicalize.
     * @return The canonical string, or null on error.
     */
    private String createCanonicalString(JSONObject json) {
        if (json == null) return null;
        try {
            // Use TreeMap to automatically sort keys alphabetically
            Map<String, String> sortedMap = new TreeMap<>();
            java.util.Iterator<String> keys = json.keys();
            while (keys.hasNext()) {
                String key = keys.next();
                sortedMap.put(key, json.getString(key));
            }

            StringBuilder sb = new StringBuilder();
            boolean first = true;
            for (Map.Entry<String, String> entry : sortedMap.entrySet()) {
                if (!first) {
                    sb.append("&");
                }
                sb.append(entry.getKey()).append("=").append(entry.getValue());
                first = false;
            }
            return sb.toString();
        } catch (JSONException e) {
            Log.e(TAG, "Could not create canonical string from JSON", e);
            return null;
        }
    }


    /**
     * Encodes a string into a QR code bitmap.
     *
     * @param content The string content to encode.
     * @param width   The desired width of the QR code bitmap.
     * @param height  The desired height of the QR code bitmap.
     * @return The generated QR code as a Bitmap, or null on error.
     * @throws WriterException If encoding fails.
     */
    private Bitmap encodeAsBitmap(String content, int width, int height) throws WriterException {
        if (content == null || content.isEmpty()) {
            return null;
        }
        MultiFormatWriter writer = new MultiFormatWriter();
        BitMatrix result;
        try {
            result = writer.encode(content, BarcodeFormat.QR_CODE, width, height, null);
        } catch (IllegalArgumentException iae) {
            // Unsupported format
            Log.e(TAG, "IllegalArgumentException during QR encoding", iae);
            return null;
        }

        int w = result.getWidth();
        int h = result.getHeight();
        int[] pixels = new int[w * h];
        for (int y = 0; y < h; y++) {
            int offset = y * w;
            for (int x = 0; x < w; x++) {
                pixels[offset + x] = result.get(x, y) ? Color.BLACK : Color.WHITE;
            }
        }

        Bitmap bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        bitmap.setPixels(pixels, 0, w, 0, 0, w, h);
        return bitmap;
    }
}

