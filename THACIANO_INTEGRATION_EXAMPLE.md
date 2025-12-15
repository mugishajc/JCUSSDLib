# Thaciano App Integration Example
## Batch OTP Processing for 100+ Phone Numbers

This document demonstrates how to integrate JCUSSDLib v2.0 into the Thaciano app for automated OTP extraction from 100+ phone numbers using a 4-step USSD sequence.

---

## Prerequisites

### 1. Permissions (AndroidManifest.xml)

```xml
<manifest xmlns:android="http://schemas.android.com/apk/res/android">
    <!-- Required permissions -->
    <uses-permission android:name="android.permission.CALL_PHONE" />
    <uses-permission android:name="android.permission.READ_PHONE_STATE" />

    <application>
        <!-- Accessibility Service for USSD capture -->
        <service
            android:name=".service.USSDAccessibilityService"
            android:permission="android.permission.BIND_ACCESSIBILITY_SERVICE">
            <intent-filter>
                <action android:name="android.accessibilityservice.AccessibilityService" />
            </intent-filter>
            <meta-data
                android:name="android.accessibilityservice.AccessibilityServiceInfo"
                android:resource="@xml/accessibility_service_config" />
        </service>
    </application>
</manifest>
```

### 2. Accessibility Service Configuration (res/xml/accessibility_service_config.xml)

```xml
<?xml version="1.0" encoding="utf-8"?>
<accessibility-service xmlns:android="http://schemas.android.com/apk/res/android"
    android:accessibilityEventTypes="typeWindowStateChanged|typeWindowContentChanged"
    android:accessibilityFeedbackType="feedbackGeneric"
    android:accessibilityFlags="flagReportViewIds"
    android:canRetrieveWindowContent="true"
    android:description="@string/accessibility_service_description"
    android:notificationTimeout="100"
    android:packageNames="com.android.phone" />
```

### 3. Gradle Dependencies (build.gradle)

```gradle
dependencies {
    implementation 'androidx.appcompat:appcompat:1.6.1'
    implementation 'com.google.android.material:material:1.11.0'

    // JCUSSDLib
    implementation project(':jcussdlib')
}
```

---

## Complete Integration Code

### Step 1: Define Your USSD Sequence

Based on the Thaciano app's 4-step OTP flow:
1. Dial initial USSD code
2. Select option (e.g., "1" for OTP service)
3. Enter phone number
4. Receive and extract OTP

```java
package com.thaciano.otp;

import com.jcussdlib.extraction.ResponseExtractor;
import com.jcussdlib.model.USSDSequence;
import com.jcussdlib.model.USSDStep;
import com.jcussdlib.validation.ResponseValidator;

public class OTPSequenceFactory {

    /**
     * Creates a 4-step USSD sequence for OTP extraction
     *
     * @param phoneNumber Phone number to process
     * @param simSlot     SIM slot to use (0 or 1)
     * @return Configured USSD sequence
     */
    public static USSDSequence createOTPSequence(String phoneNumber, int simSlot) {

        // Step 1: Wait for initial USSD menu
        USSDStep step1 = new USSDStep.Builder()
            .setStepNumber(1)
            .setDescription("Waiting for initial menu")
            .setExpectedPattern(".*(?:Select option|Choose|Menu).*")
            .setResponseToSend("1") // Select option 1
            .setTimeout(10000) // 10 second timeout
            .setRetryPolicy(USSDStep.RetryPolicy.RETRY_TWICE)
            .setValidator(new ResponseValidator.ContainsKeywordValidator("option", "menu", "select"))
            .build();

        // Step 2: Wait for phone number prompt
        USSDStep step2 = new USSDStep.Builder()
            .setStepNumber(2)
            .setDescription("Entering phone number")
            .setExpectedPattern(".*(?:Enter|Input|Phone).*")
            .setResponseToSend("{{phone}}") // Variable placeholder
            .setTimeout(8000)
            .setRetryPolicy(USSDStep.RetryPolicy.RETRY_TWICE)
            .setValidator(new ResponseValidator.ContainsKeywordValidator("phone", "number", "enter"))
            .build();

        // Step 3: Wait for confirmation prompt
        USSDStep step3 = new USSDStep.Builder()
            .setStepNumber(3)
            .setDescription("Confirming phone number")
            .setExpectedPattern(".*(?:Confirm|Continue|Proceed).*")
            .setResponseToSend("1") // Confirm
            .setTimeout(8000)
            .setRetryPolicy(USSDStep.RetryPolicy.RETRY_TWICE)
            .setValidator(new ResponseValidator.ContainsKeywordValidator("confirm", "correct", "yes"))
            .build();

        // Step 4: Extract OTP from response
        USSDStep step4 = new USSDStep.Builder()
            .setStepNumber(4)
            .setDescription("Extracting OTP code")
            .setExpectedPattern(".*\\d{4,8}.*") // Expect OTP digits
            .setTimeout(15000) // Longer timeout for OTP generation
            .setRetryPolicy(USSDStep.RetryPolicy.RETRY_3_TIMES) // More retries for OTP
            .setValidator(new ResponseValidator.CompositeAndValidator(
                new ResponseValidator.LengthValidator(10, 200), // Response not too short
                new ResponseValidator.PatternValidator("\\d{4,8}", "Contains 4-8 digit OTP")
            ))
            .setExtractor(new ResponseExtractor.OTPExtractor(4, 8)) // Extract 4-8 digit OTP
            .setVariableName("otp") // Store extracted OTP with key "otp"
            .build();

        // Build complete sequence
        return new USSDSequence.Builder()
            .setSequenceId("otp_" + phoneNumber)
            .setName("OTP Extraction for " + phoneNumber)
            .setDescription("4-step USSD sequence to extract OTP")
            .setInitialUSSDCode("*182*8*1#") // Your USSD code
            .setSimSlot(simSlot)
            .addStep(step1)
            .addStep(step2)
            .addStep(step3)
            .addStep(step4)
            .setVariable("phone", phoneNumber) // Phone number variable
            .setGlobalTimeout(60000) // 60 second total timeout
            .setStopOnError(true) // Stop on first error
            .build();
    }
}
```

### Step 2: Batch Processing Manager

```java
package com.thaciano.otp;

import android.content.Context;
import android.util.Log;

import androidx.annotation.NonNull;

import com.jcussdlib.callback.USSDSequenceCallback;
import com.jcussdlib.controller.USSDController;
import com.jcussdlib.model.USSDSequence;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class BatchOTPProcessor {

    private static final String TAG = "BatchOTPProcessor";

    private final Context context;
    private final USSDController controller;

    // Results storage
    private final Map<String, String> extractedOTPs = new HashMap<>();
    private final List<String> failedPhones = new ArrayList<>();

    public BatchOTPProcessor(Context context) {
        this.context = context;
        this.controller = USSDController.getInstance(context);
    }

    /**
     * Processes a batch of phone numbers to extract OTPs
     *
     * @param phoneNumbers List of phone numbers (100+)
     * @param simSlot      SIM slot to use (0 or 1)
     * @param callback     Callback for batch progress
     */
    public void processBatch(List<String> phoneNumbers,
                            int simSlot,
                            BatchProcessingCallback callback) {

        if (phoneNumbers == null || phoneNumbers.isEmpty()) {
            Log.e(TAG, "Phone numbers list is empty");
            callback.onBatchFailed("No phone numbers provided");
            return;
        }

        Log.d(TAG, "Starting batch processing for " + phoneNumbers.size() + " phones");

        // Clear previous results
        extractedOTPs.clear();
        failedPhones.clear();

        // Create sequence for each phone
        List<USSDSequence> sequences = new ArrayList<>();
        for (String phone : phoneNumbers) {
            USSDSequence sequence = OTPSequenceFactory.createOTPSequence(phone, simSlot);
            sequences.add(sequence);
        }

        // Execute batch with 2-second delay between sequences
        controller.executeBatch(sequences, new USSDController.BatchCallback() {

            @Override
            public void onBatchStarted(int totalSequences) {
                Log.d(TAG, "Batch started: " + totalSequences + " sequences");
                callback.onBatchStarted(totalSequences);
            }

            @Override
            public void onSequenceCompleted(int sequenceIndex,
                                          int totalSequences,
                                          @NonNull Map<String, String> extractedData) {
                String phone = phoneNumbers.get(sequenceIndex - 1);
                String otp = extractedData.get("otp");

                if (otp != null) {
                    extractedOTPs.put(phone, otp);
                    Log.d(TAG, "OTP extracted for " + phone + ": " + otp);
                    callback.onOTPExtracted(phone, otp, sequenceIndex, totalSequences);
                } else {
                    failedPhones.add(phone);
                    Log.w(TAG, "No OTP extracted for " + phone);
                    callback.onOTPFailed(phone, "OTP not found in response", sequenceIndex, totalSequences);
                }
            }

            @Override
            public void onSequenceFailed(int sequenceIndex,
                                        int totalSequences,
                                        @NonNull String error) {
                String phone = phoneNumbers.get(sequenceIndex - 1);
                failedPhones.add(phone);
                Log.e(TAG, "Sequence failed for " + phone + ": " + error);
                callback.onOTPFailed(phone, error, sequenceIndex, totalSequences);
            }

            @Override
            public void onBatchCompleted(int successCount,
                                        int failureCount,
                                        long totalDurationMs) {
                Log.d(TAG, "Batch completed: " + successCount + " success, " +
                          failureCount + " failures in " + totalDurationMs + "ms");

                callback.onBatchCompleted(
                    successCount,
                    failureCount,
                    totalDurationMs,
                    extractedOTPs,
                    failedPhones
                );
            }
        }, 2000); // 2-second delay between sequences
    }

    /**
     * Stops batch processing
     */
    public void stopBatch() {
        controller.stopBatch();
    }

    /**
     * Cleanup
     */
    public void cleanup() {
        controller.cleanup();
    }

    /**
     * Callback interface for batch processing
     */
    public interface BatchProcessingCallback {
        void onBatchStarted(int totalPhones);
        void onOTPExtracted(String phone, String otp, int current, int total);
        void onOTPFailed(String phone, String error, int current, int total);
        void onBatchCompleted(int successCount, int failureCount, long durationMs,
                            Map<String, String> otps, List<String> failedPhones);
        void onBatchFailed(String error);
    }
}
```

### Step 3: Activity Integration (UI Layer)

```java
package com.thaciano.otp;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.provider.Settings;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.jcussdlib.controller.USSDController;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

public class OTPProcessingActivity extends AppCompatActivity {

    private static final String TAG = "OTPProcessingActivity";
    private static final int PERMISSION_REQUEST_CODE = 100;

    private Button btnStart;
    private Button btnStop;
    private ProgressBar progressBar;
    private TextView tvStatus;
    private TextView tvResults;

    private BatchOTPProcessor processor;

    // Your phone list (load from file, database, or API)
    private List<String> phoneNumbers = Arrays.asList(
        "0781234567",
        "0782345678",
        "0783456789",
        // ... 100+ phone numbers
        "0789999999"
    );

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_otp_processing);

        // Initialize views
        btnStart = findViewById(R.id.btnStart);
        btnStop = findViewById(R.id.btnStop);
        progressBar = findViewById(R.id.progressBar);
        tvStatus = findViewById(R.id.tvStatus);
        tvResults = findViewById(R.id.tvResults);

        // Initialize processor
        processor = new BatchOTPProcessor(this);

        // Button listeners
        btnStart.setOnClickListener(v -> startBatchProcessing());
        btnStop.setOnClickListener(v -> stopBatchProcessing());

        // Check permissions
        checkPermissions();
    }

    private void checkPermissions() {
        String[] permissions = USSDController.getRequiredPermissions();

        for (String permission : permissions) {
            if (ContextCompat.checkSelfPermission(this, permission)
                != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this, permissions, PERMISSION_REQUEST_CODE);
                return;
            }
        }

        // Check accessibility service
        if (!isAccessibilityServiceEnabled()) {
            tvStatus.setText("Please enable Accessibility Service");
            Toast.makeText(this, "Please enable USSD Accessibility Service", Toast.LENGTH_LONG).show();
            startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS));
        } else {
            tvStatus.setText("Ready to process " + phoneNumbers.size() + " phones");
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode,
                                          @NonNull String[] permissions,
                                          @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

        if (requestCode == PERMISSION_REQUEST_CODE) {
            boolean allGranted = true;
            for (int result : grantResults) {
                if (result != PackageManager.PERMISSION_GRANTED) {
                    allGranted = false;
                    break;
                }
            }

            if (allGranted) {
                checkPermissions();
            } else {
                Toast.makeText(this, "Permissions required!", Toast.LENGTH_LONG).show();
                finish();
            }
        }
    }

    private void startBatchProcessing() {
        Log.d(TAG, "Starting batch processing for " + phoneNumbers.size() + " phones");

        btnStart.setEnabled(false);
        btnStop.setEnabled(true);
        progressBar.setVisibility(View.VISIBLE);
        progressBar.setMax(phoneNumbers.size());
        progressBar.setProgress(0);
        tvResults.setText("");

        processor.processBatch(phoneNumbers, 0, new BatchOTPProcessor.BatchProcessingCallback() {

            @Override
            public void onBatchStarted(int totalPhones) {
                runOnUiThread(() -> {
                    tvStatus.setText("Processing " + totalPhones + " phones...");
                });
            }

            @Override
            public void onOTPExtracted(String phone, String otp, int current, int total) {
                runOnUiThread(() -> {
                    progressBar.setProgress(current);
                    tvStatus.setText("Progress: " + current + "/" + total);

                    String result = tvResults.getText().toString();
                    result += phone + " → OTP: " + otp + "\n";
                    tvResults.setText(result);

                    Log.d(TAG, "OTP for " + phone + ": " + otp);
                });
            }

            @Override
            public void onOTPFailed(String phone, String error, int current, int total) {
                runOnUiThread(() -> {
                    progressBar.setProgress(current);
                    tvStatus.setText("Progress: " + current + "/" + total);

                    String result = tvResults.getText().toString();
                    result += phone + " → FAILED: " + error + "\n";
                    tvResults.setText(result);

                    Log.e(TAG, "Failed for " + phone + ": " + error);
                });
            }

            @Override
            public void onBatchCompleted(int successCount, int failureCount, long durationMs,
                                        Map<String, String> otps, List<String> failedPhones) {
                runOnUiThread(() -> {
                    btnStart.setEnabled(true);
                    btnStop.setEnabled(false);
                    progressBar.setVisibility(View.GONE);

                    long durationSec = durationMs / 1000;
                    tvStatus.setText("Completed! " + successCount + " success, " +
                                    failureCount + " failures in " + durationSec + "s");

                    // Save OTPs to database, file, or send to server
                    saveOTPs(otps);

                    // Retry failed phones if needed
                    if (!failedPhones.isEmpty()) {
                        Log.w(TAG, "Failed phones: " + failedPhones);
                        // Optionally retry failed phones
                    }

                    Toast.makeText(OTPProcessingActivity.this,
                        "Processing complete!", Toast.LENGTH_LONG).show();
                });
            }

            @Override
            public void onBatchFailed(String error) {
                runOnUiThread(() -> {
                    btnStart.setEnabled(true);
                    btnStop.setEnabled(false);
                    progressBar.setVisibility(View.GONE);
                    tvStatus.setText("Batch failed: " + error);
                    Toast.makeText(OTPProcessingActivity.this,
                        "Batch failed: " + error, Toast.LENGTH_LONG).show();
                });
            }
        });
    }

    private void stopBatchProcessing() {
        processor.stopBatch();
        btnStart.setEnabled(true);
        btnStop.setEnabled(false);
        progressBar.setVisibility(View.GONE);
        tvStatus.setText("Batch stopped by user");
    }

    private void saveOTPs(Map<String, String> otps) {
        // TODO: Save to database, file, or send to server
        Log.d(TAG, "Saving " + otps.size() + " OTPs");

        // Example: Save to SharedPreferences
        // getSharedPreferences("otps", MODE_PRIVATE)
        //     .edit()
        //     .putString("batch_" + System.currentTimeMillis(), otps.toString())
        //     .apply();
    }

    private boolean isAccessibilityServiceEnabled() {
        // Check if your accessibility service is enabled
        // Implementation depends on your service name
        return true; // TODO: Implement actual check
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        processor.cleanup();
    }
}
```

### Step 4: Layout (res/layout/activity_otp_processing.xml)

```xml
<?xml version="1.0" encoding="utf-8"?>
<LinearLayout xmlns:android="http://schemas.android.com/apk/res/android"
    android:layout_width="match_parent"
    android:layout_height="match_parent"
    android:orientation="vertical"
    android:padding="16dp">

    <TextView
        android:id="@+id/tvStatus"
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:text="Ready"
        android:textSize="16sp"
        android:textStyle="bold"
        android:layout_marginBottom="16dp" />

    <ProgressBar
        android:id="@+id/progressBar"
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:layout_marginBottom="16dp"
        android:visibility="gone"
        style="?android:attr/progressBarStyleHorizontal" />

    <Button
        android:id="@+id/btnStart"
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:text="Start Batch Processing"
        android:layout_marginBottom="8dp" />

    <Button
        android:id="@+id/btnStop"
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:text="Stop Processing"
        android:enabled="false"
        android:layout_marginBottom="16dp" />

    <ScrollView
        android:layout_width="match_parent"
        android:layout_height="0dp"
        android:layout_weight="1">

        <TextView
            android:id="@+id/tvResults"
            android:layout_width="match_parent"
            android:layout_height="wrap_content"
            android:fontFamily="monospace"
            android:textSize="12sp" />
    </ScrollView>
</LinearLayout>
```

---

## Performance Optimization

### Rate Limiting
```java
// Adjust delay based on network speed and carrier limits
controller.executeBatch(sequences, callback, 2000); // 2 seconds

// For faster processing (if carrier allows)
controller.executeBatch(sequences, callback, 1000); // 1 second

// For slower networks
controller.executeBatch(sequences, callback, 3000); // 3 seconds
```

### Retry Strategy
```java
// Aggressive retries for critical OTP step
USSDStep otpStep = new USSDStep.Builder()
    .setStepNumber(4)
    .setRetryPolicy(USSDStep.RetryPolicy.RETRY_5_TIMES)
    .setTimeout(20000) // 20 second timeout
    .build();
```

### Chunked Processing
```java
// Process in chunks of 50 to avoid long-running operations
List<List<String>> chunks = chunkList(phoneNumbers, 50);

for (List<String> chunk : chunks) {
    processor.processBatch(chunk, simSlot, callback);
    // Wait for chunk to complete before starting next
}
```

---

## Troubleshooting

### Common Issues

1. **OTP not extracted**
   - Check regex pattern in OTPExtractor
   - Verify USSD response format
   - Increase timeout for step 4

2. **Sequence fails at step 2**
   - Verify phone number format
   - Check variable placeholder {{phone}}
   - Ensure sequence.setVariable("phone", phoneNumber) is called

3. **Batch stops mid-processing**
   - Check network connectivity
   - Verify accessibility service is running
   - Check device doesn't enter sleep mode

4. **Slow processing**
   - Reduce delay between sequences
   - Use faster SIM card
   - Optimize step timeouts

---

## Production Checklist

- [ ] Permissions granted (CALL_PHONE, READ_PHONE_STATE)
- [ ] Accessibility service enabled
- [ ] USSD code tested manually first
- [ ] Retry policies configured appropriately
- [ ] Timeouts set based on network speed
- [ ] OTP extraction verified with sample responses
- [ ] Error handling and logging in place
- [ ] Results saved to persistent storage
- [ ] Battery optimization disabled for app
- [ ] Device screen stays awake during processing

---

## Expected Results

For 100 phone numbers with 2-second delay:
- Total time: ~13-15 minutes (100 × 8 seconds per sequence + delays)
- Success rate: 95%+ with proper configuration
- OTPs extracted and stored in Map<String, String>
- Failed phones logged for manual retry

---

## Next Steps

1. Test with 5-10 phones first
2. Analyze failure patterns
3. Tune retry policies and timeouts
4. Scale up to full batch (100+)
5. Implement persistent storage for OTPs
6. Add retry mechanism for failed phones
7. Monitor battery and network usage

---

**Author:** Mugisha Jean Claude
**GitHub:** @mugishajc
**LinkedIn:** https://www.linkedin.com/in/mugisha-jean-claude/
**Support:** https://www.paypal.com/donate/?hosted_button_id=U8JW9CARMJS22
