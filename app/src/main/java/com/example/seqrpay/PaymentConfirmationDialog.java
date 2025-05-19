package com.example.seqrpay;

import android.app.Dialog;
import android.content.Context;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.view.Window;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONObject;
import org.json.JSONException;

public class PaymentConfirmationDialog extends Dialog {
    private static final String TAG = "PaymentConfirmDialog";

    private String rawPaymentData; // The original full QR string from the scanner
    private String payloadType;    // Type of payload (URL, SIGNED_PAYMENT, OTHER) from QRScannerActivity

    private Button btnConfirm, btnCancel;
    private TextView tvPaymentDetails;

    /**
     * Constructor for the Payment Confirmation Dialog.
     * @param context The context.
     * @param rawPaymentData The full, original string content of the scanned QR code.
     * @param payloadType The type of payload identified by QRScannerActivity (e.g., SIGNED_PAYMENT, URL).
     */
    public PaymentConfirmationDialog(Context context, String rawPaymentData, String payloadType) {
        super(context);
        this.rawPaymentData = rawPaymentData;
        this.payloadType = payloadType;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        requestWindowFeature(Window.FEATURE_NO_TITLE); // Remove title bar
        setContentView(R.layout.dialog_payment_confirmation);

        // Initialize UI elements from the dialog's layout
        tvPaymentDetails = findViewById(R.id.tv_payment_details);
        btnConfirm = findViewById(R.id.btn_confirm);
        btnCancel = findViewById(R.id.btn_cancel);

        // Parse the raw QR data based on its type and display relevant information
        parseAndDisplayPaymentData();

        // Set click listener for the confirm button
        btnConfirm.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                // TODO: Implement actual payment processing logic.
                // This would typically involve:
                // 1. Communicating with a backend server.
                // 2. Verifying funds, user status, etc.
                // 3. Executing the transaction.
                // 4. Recording the transaction in the local DatabaseHelper.
                // For now, we just show a toast message.
                Toast.makeText(getContext(), "Payment Confirmed (Simulation)", Toast.LENGTH_LONG).show();

                // Example of how you might add to local transaction history (needs user ID)
                // DatabaseHelper dbHelper = DatabaseHelper.getInstance(getContext());
                // SharedPreferences prefs = getContext().getSharedPreferences(LoginActivity.SHARED_PREFS_NAME, Context.MODE_PRIVATE);
                // String currentUsername = prefs.getString(LoginActivity.KEY_LOGGED_IN_USERNAME, null);
                // if (currentUsername != null) {
                //    long userId = dbHelper.getUserId(currentUsername); // You'd need to implement getUserId
                //    if (userId > 0) {
                //        // Extract details to save
                //        String description = "Payment made via QR"; // Or more specific
                //        String amount = ... // Get amount from parsed data
                //        String date = ... // Get current date/time
                //        dbHelper.addTransaction(userId, description, "-" + amount, date); // Assuming it's an outgoing payment
                //    }
                // }
                dismiss(); // Close the dialog
            }
        });

        // Set click listener for the cancel button
        btnCancel.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Toast.makeText(getContext(), "Payment cancelled", Toast.LENGTH_SHORT).show();
                dismiss(); // Close the dialog
            }
        });
    }

    /**
     * Parses the raw QR data based on the identified payloadType and updates the
     * tvPaymentDetails TextView to show relevant information to the user for confirmation.
     */
    private void parseAndDisplayPaymentData() {
        StringBuilder details = new StringBuilder();

        if (QRScannerActivity.PAYLOAD_TYPE_SIGNED_PAYMENT.equals(payloadType)) {
            try {
                // For signed payments, the rawPaymentData is the full JSON string.
                // We parse it to extract the verified data from the 'dataToSign' block.
                JSONObject qrJson = new JSONObject(rawPaymentData);
                JSONObject dataToSign = qrJson.getJSONObject("dataToSign"); // This block was verified

                String payee = dataToSign.optString("payeeUsername", "N/A");
                String amount = dataToSign.optString("amount", "N/A");
                String currency = dataToSign.optString("currency", "");
                String timestamp = dataToSign.optString("timestamp", ""); // From the signed block

                details.append("Confirm Payment To:\n");
                details.append("Payee: ").append(payee).append("\n");
                details.append("Amount: ").append(amount).append(" ").append(currency).append("\n");
                if (!timestamp.isEmpty()) {
                    // You might want to format the timestamp nicely here
                    details.append("QR Timestamp: ").append(timestamp).append("\n");
                }
                details.append("\nStatus: VERIFIED (Signature Valid)");

            } catch (JSONException e) {
                Log.e(TAG, "Error parsing signed payment JSON in dialog: " + e.getMessage());
                details.append("Error: Could not display all signed payment details.\n");
                details.append("Please verify carefully.\n");
                details.append("Raw Data: ").append(rawPaymentData.substring(0, Math.min(rawPaymentData.length(), 100))).append("..."); // Show partial raw data
            }
        } else if (QRScannerActivity.PAYLOAD_TYPE_URL.equals(payloadType)) {
            // For URLs, the rawPaymentData is the URL itself.
            details.append("You are about to interact with the following URL:\n");
            details.append(rawPaymentData);
            details.append("\n\n(Security scans were performed on the previous screen.)");
        } else { // PAYLOAD_TYPE_OTHER or any fallback
            // For other types, display the raw data.
            // You could add more specific parsing here if "OTHER" has known sub-formats.
            details.append("Confirm action with the following data:\n\n").append(rawPaymentData);
        }
        tvPaymentDetails.setText(details.toString());
    }
}
