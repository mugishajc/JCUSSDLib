package com.jcussdlib.matcher;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import androidx.annotation.NonNull;

import com.jcussdlib.callback.USSDSequenceCallback;
import com.jcussdlib.controller.USSDController;
import com.jcussdlib.extraction.ResponseExtractor;
import com.jcussdlib.model.USSDSequence;
import com.jcussdlib.model.USSDStep;
import com.jcussdlib.validation.ResponseValidator;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Brute-Force OTP Matcher - Senior Engineer Implementation
 * <p>
 * Matches phone numbers with OTPs by sequentially trying each OTP
 * until a successful match is detected via USSD response analysis.
 * </p>
 *
 * <p>Use Case: Thaciano App</p>
 * <pre>
 * Input:
 *   - Phone list: ["0781111111", "0782222222", ...]
 *   - OTP pool: ["123456", "234567", ..., "999999"] (1000+ OTPs)
 *   - USSD code: *348*{pin}#
 *
 * Algorithm:
 *   For each phone in list:
 *     1. Dial *348*{pin}#
 *     2. Select option "1"
 *     3. Enter phone number
 *     4. Try OTP #1, #2, #3... until success detected
 *     5. Save (phone, matched_OTP) pair
 *     6. Move to next phone
 *
 * Output:
 *   Phone: 0781111111 → OTP: 456789 ✓ (234 attempts)
 *   Phone: 0782222222 → OTP: 123456 ✓ (1 attempt)
 * </pre>
 *
 * <p>Thread Safety: Safe for sequential processing (one matcher instance per batch)</p>
 *
 * <p>Performance:</p>
 * <ul>
 *   <li>Best case: 100 phones × 1 attempt × 8s = 13 minutes</li>
 *   <li>Average: 100 phones × 500 attempts × 8s = 111 hours</li>
 *   <li>Optimize: Reduce timeouts, use multiple devices in parallel</li>
 * </ul>
 *
 * @author Mugisha Jean Claude
 * @version 2.0.0
 * @since 2.0.0
 */
public class OTPBruteForceMatcher {

    private static final String TAG = "OTPBruteForceMatcher";

    // Core dependencies
    private final Context context;
    private final USSDController controller;
    private final Handler mainHandler;

    // Configuration
    private final String savedPin;
    private final String ussdCode;
    private final int simSlot;

    // Results storage
    private final Map<String, MatchResult> phoneOTPMatches = new HashMap<>();
    private final List<String> unmatchedPhones = new ArrayList<>();

    // Processing state
    private final AtomicBoolean isProcessing = new AtomicBoolean(false);
    private final AtomicInteger currentPhoneIndex = new AtomicInteger(0);
    private final AtomicInteger currentOTPIndex = new AtomicInteger(0);

    /**
     * Result of successful OTP match for a phone number
     */
    public static class MatchResult {
        public final String phone;
        public final String matchedOTP;
        public final int attemptsCount;
        public final long durationMs;
        public final long timestamp;

        public MatchResult(String phone, String matchedOTP, int attemptsCount, long durationMs) {
            this.phone = phone;
            this.matchedOTP = matchedOTP;
            this.attemptsCount = attemptsCount;
            this.durationMs = durationMs;
            this.timestamp = System.currentTimeMillis();
        }

        @Override
        public String toString() {
            return phone + " → " + matchedOTP +
                   " (tried " + attemptsCount + " OTPs in " + (durationMs / 1000) + "s)";
        }
    }

    /**
     * Callback interface for brute-force matching progress
     */
    public interface MatchingCallback {
        /**
         * Called when matching process starts
         *
         * @param totalPhones Total number of phones to process
         * @param totalOTPs   Total number of OTPs in pool
         */
        void onMatchingStarted(int totalPhones, int totalOTPs);

        /**
         * Called when starting to process a new phone
         *
         * @param phone       Phone number being processed
         * @param phoneIndex  Current phone index (1-based)
         * @param totalPhones Total phones
         */
        void onPhoneStarted(String phone, int phoneIndex, int totalPhones);

        /**
         * Called before trying each OTP
         *
         * @param phone         Phone number
         * @param otp           OTP being tried
         * @param attemptNumber Attempt number for this phone (1-based)
         */
        void onOTPAttempt(String phone, String otp, int attemptNumber);

        /**
         * Called when an OTP successfully matches
         *
         * @param phone         Phone number
         * @param otp           Matched OTP
         * @param attemptNumber Number of attempts it took
         */
        void onOTPSuccess(String phone, String otp, int attemptNumber);

        /**
         * Called when an OTP fails to match
         *
         * @param phone        Phone number
         * @param otp          Failed OTP
         * @param errorMessage Error or rejection reason
         */
        void onOTPFailure(String phone, String otp, String errorMessage);

        /**
         * Called when a phone is successfully matched with an OTP
         *
         * @param result Match result with timing data
         */
        void onPhoneCompleted(MatchResult result);

        /**
         * Called when all OTPs tried but no match found for phone
         *
         * @param phone  Phone number
         * @param reason Failure reason
         */
        void onPhoneFailed(String phone, String reason);

        /**
         * Called when all phones have been processed
         *
         * @param matches        Map of phone → MatchResult for successful matches
         * @param failed         List of phones that didn't match any OTP
         * @param totalDurationMs Total processing time
         */
        void onAllPhonesCompleted(Map<String, MatchResult> matches,
                                 List<String> failed,
                                 long totalDurationMs);
    }

    /**
     * Constructor with default USSD code template
     *
     * @param context   Application context
     * @param savedPin  PIN to use in USSD code (e.g., "1234")
     */
    public OTPBruteForceMatcher(@NonNull Context context, @NonNull String savedPin) {
        this(context, savedPin, "*348*{pin}#", 0);
    }

    /**
     * Full constructor
     *
     * @param context          Application context
     * @param savedPin         PIN for USSD code
     * @param ussdCodeTemplate USSD code template with {pin} placeholder
     * @param simSlot          SIM slot (0 for SIM1, 1 for SIM2, -1 for default)
     */
    public OTPBruteForceMatcher(@NonNull Context context,
                                @NonNull String savedPin,
                                @NonNull String ussdCodeTemplate,
                                int simSlot) {
        if (context == null) {
            throw new IllegalArgumentException("Context cannot be null");
        }
        if (savedPin == null || savedPin.trim().isEmpty()) {
            throw new IllegalArgumentException("PIN cannot be null or empty");
        }

        this.context = context.getApplicationContext();
        this.controller = USSDController.getInstance(context);
        this.mainHandler = new Handler(Looper.getMainLooper());
        this.savedPin = savedPin;
        this.ussdCode = ussdCodeTemplate.replace("{pin}", savedPin);
        this.simSlot = simSlot;

        Log.d(TAG, "OTPBruteForceMatcher initialized with USSD: " + ussdCode + " on SIM slot " + simSlot);
    }

    /**
     * Starts brute-force OTP matching process
     *
     * @param phoneList List of phone numbers to process
     * @param otpPool   Pool of OTPs to try (can be 1000+)
     * @param callback  Progress and result callback
     */
    public void startMatching(@NonNull List<String> phoneList,
                             @NonNull List<String> otpPool,
                             @NonNull MatchingCallback callback) {

        if (phoneList == null || phoneList.isEmpty()) {
            throw new IllegalArgumentException("Phone list cannot be null or empty");
        }
        if (otpPool == null || otpPool.isEmpty()) {
            throw new IllegalArgumentException("OTP pool cannot be null or empty");
        }
        if (callback == null) {
            throw new IllegalArgumentException("Callback cannot be null");
        }

        if (isProcessing.get()) {
            Log.w(TAG, "Matching already in progress");
            return;
        }

        // Reset state
        isProcessing.set(true);
        phoneOTPMatches.clear();
        unmatchedPhones.clear();
        currentPhoneIndex.set(0);

        Log.d(TAG, "Starting OTP matching: " + phoneList.size() + " phones × " + otpPool.size() + " OTPs");

        final long startTime = System.currentTimeMillis();

        // Notify start on main thread
        mainHandler.post(() -> callback.onMatchingStarted(phoneList.size(), otpPool.size()));

        // Start processing first phone
        processNextPhone(phoneList, otpPool, callback, startTime);
    }

    /**
     * Processes next phone in the list
     */
    private void processNextPhone(@NonNull List<String> phoneList,
                                  @NonNull List<String> otpPool,
                                  @NonNull MatchingCallback callback,
                                  long overallStartTime) {

        if (!isProcessing.get()) {
            Log.d(TAG, "Processing stopped by user");
            return;
        }

        int phoneIndex = currentPhoneIndex.get();

        if (phoneIndex >= phoneList.size()) {
            // All phones processed - complete
            long totalDuration = System.currentTimeMillis() - overallStartTime;
            isProcessing.set(false);

            Log.d(TAG, "All phones processed: " + phoneOTPMatches.size() + " matched, " +
                      unmatchedPhones.size() + " unmatched in " + (totalDuration / 1000) + "s");

            mainHandler.post(() -> callback.onAllPhonesCompleted(
                new HashMap<>(phoneOTPMatches),
                new ArrayList<>(unmatchedPhones),
                totalDuration
            ));
            return;
        }

        String currentPhone = phoneList.get(phoneIndex);
        Log.d(TAG, "Processing phone " + (phoneIndex + 1) + "/" + phoneList.size() + ": " + currentPhone);

        mainHandler.post(() -> callback.onPhoneStarted(currentPhone, phoneIndex + 1, phoneList.size()));

        // Reset OTP index for new phone
        currentOTPIndex.set(0);

        // Start trying OTPs for this phone
        long phoneStartTime = System.currentTimeMillis();
        tryNextOTP(currentPhone, phoneList, otpPool, callback, overallStartTime, phoneStartTime);
    }

    /**
     * Tries next OTP from pool for current phone
     */
    private void tryNextOTP(@NonNull String phone,
                           @NonNull List<String> phoneList,
                           @NonNull List<String> otpPool,
                           @NonNull MatchingCallback callback,
                           long overallStartTime,
                           long phoneStartTime) {

        if (!isProcessing.get()) {
            return;
        }

        int otpIndex = currentOTPIndex.get();

        if (otpIndex >= otpPool.size()) {
            // All OTPs exhausted, no match found
            Log.w(TAG, "No matching OTP found for " + phone + " after " + otpPool.size() + " attempts");
            unmatchedPhones.add(phone);

            mainHandler.post(() -> callback.onPhoneFailed(
                phone,
                "No matching OTP found after " + otpPool.size() + " attempts"
            ));

            // Move to next phone
            currentPhoneIndex.incrementAndGet();
            processNextPhone(phoneList, otpPool, callback, overallStartTime);
            return;
        }

        String currentOTP = otpPool.get(otpIndex);
        int attemptNumber = otpIndex + 1;

        Log.d(TAG, phone + " → trying OTP #" + attemptNumber + "/" + otpPool.size() + ": " + currentOTP);

        mainHandler.post(() -> callback.onOTPAttempt(phone, currentOTP, attemptNumber));

        // Create sequence to test this OTP
        USSDSequence sequence = createOTPTestSequence(phone, currentOTP);

        // Execute sequence
        controller.executeSequence(sequence, new USSDSequenceCallback() {

            @Override
            public void onSequenceStarted(@NonNull String sessionId, @NonNull String sequenceName, int totalSteps) {
                Log.d(TAG, "Sequence started: " + sessionId);
            }

            @Override
            public void onSequenceCompleted(@NonNull Map<String, String> extractedData, long totalDurationMs) {
                // Check if OTP was successful
                String successIndicator = extractedData.get("success");

                if ("true".equals(successIndicator)) {
                    // SUCCESS! OTP matched
                    long phoneDuration = System.currentTimeMillis() - phoneStartTime;
                    MatchResult result = new MatchResult(phone, currentOTP, attemptNumber, phoneDuration);
                    phoneOTPMatches.put(phone, result);

                    Log.d(TAG, "✓ OTP MATCH FOUND: " + result);

                    mainHandler.post(() -> {
                        callback.onOTPSuccess(phone, currentOTP, attemptNumber);
                        callback.onPhoneCompleted(result);
                    });

                    // Move to next phone
                    currentPhoneIndex.incrementAndGet();
                    processNextPhone(phoneList, otpPool, callback, overallStartTime);

                } else {
                    // OTP failed - try next
                    String errorMsg = extractedData.get("error_message");
                    if (errorMsg == null) {
                        errorMsg = "OTP validation failed";
                    }

                    final String finalErrorMsg = errorMsg;
                    mainHandler.post(() -> callback.onOTPFailure(phone, currentOTP, finalErrorMsg));

                    // Increment OTP index and try next
                    currentOTPIndex.incrementAndGet();
                    tryNextOTP(phone, phoneList, otpPool, callback, overallStartTime, phoneStartTime);
                }
            }

            @Override
            public void onSequenceFailed(@NonNull String error, int failedAtStep) {
                // Sequence execution failed - try next OTP
                Log.w(TAG, "Sequence failed for " + phone + " with OTP " + currentOTP + ": " + error);

                mainHandler.post(() -> callback.onOTPFailure(phone, currentOTP, error));

                // Try next OTP
                currentOTPIndex.incrementAndGet();
                tryNextOTP(phone, phoneList, otpPool, callback, overallStartTime, phoneStartTime);
            }

            // Minimal implementations for unused callbacks
            @Override
            public void onStepStarted(int stepNumber, int totalSteps, @NonNull String stepDescription) {}

            @Override
            public void onStepCompleted(int stepNumber, @NonNull String response, long durationMs) {}

            @Override
            public void onStepFailed(int stepNumber, @NonNull String error, int attemptCount) {}

            @Override
            public void onStepRetrying(int stepNumber, int attemptNumber, int maxRetries, @NonNull String previousError) {}

            @Override
            public void onProgressUpdate(int completedSteps, int totalSteps, int percentComplete) {}

            @Override
            public void onDataExtracted(@NonNull String dataKey, @NonNull String dataValue, int stepNumber) {}
        });
    }

    /**
     * Creates USSD sequence to test one OTP for one phone
     */
    private USSDSequence createOTPTestSequence(@NonNull String phone, @NonNull String otp) {

        // Success/failure detector validator
        ResponseValidator outcomeValidator = new ResponseValidator() {
            @Override
            public ValidationResult validate(@NonNull String response) {
                if (response == null) {
                    return ValidationResult.failure("Response is null");
                }

                String lower = response.toLowerCase();

                // Success keywords
                if (lower.contains("successful") ||
                    lower.contains("confirmed") ||
                    lower.contains("activated") ||
                    lower.contains("registered") ||
                    lower.contains("complete") ||
                    lower.contains("accepted") ||
                    lower.contains("verified")) {
                    return ValidationResult.success();
                }

                // Explicit failure keywords
                if (lower.contains("invalid") ||
                    lower.contains("wrong") ||
                    lower.contains("incorrect") ||
                    lower.contains("failed") ||
                    lower.contains("error") ||
                    lower.contains("denied") ||
                    lower.contains("rejected") ||
                    lower.contains("try again")) {
                    return ValidationResult.failure("OTP rejected: " + response);
                }

                // Ambiguous response - treat as failure to continue trying
                return ValidationResult.failure("Unclear response: " + response);
            }
        };

        // Extractor that detects success/failure
        ResponseExtractor outcomeExtractor = new ResponseExtractor() {
            @Override
            public ExtractionResult extract(@NonNull String response) {
                if (response == null) {
                    return ExtractionResult.failure("Response is null");
                }

                ValidationResult validation = outcomeValidator.validate(response);
                Map<String, String> metadata = new HashMap<>();

                if (validation.isValid()) {
                    metadata.put("success", "true");
                    metadata.put("original_response", response);
                    return ExtractionResult.success("true", metadata);
                } else {
                    metadata.put("success", "false");
                    metadata.put("error_message", validation.getErrorMessage());
                    metadata.put("original_response", response);
                    return ExtractionResult.success("false", metadata);
                }
            }
        };

        return new USSDSequence.Builder()
            .setSequenceId("otp_test_" + phone + "_" + otp + "_" + System.currentTimeMillis())
            .setName("Test OTP for " + phone)
            .setInitialUSSDCode(ussdCode)  // *348*{pin}#
            .setSimSlot(simSlot)

            // Step 1: Wait for initial menu, send "1"
            .addStep(new USSDStep.Builder()
                .setStepNumber(1)
                .setDescription("Select option 1")
                .setResponseToSend("1")
                .setTimeout(8000)
                .setRetryPolicy(USSDStep.RetryPolicy.RETRY_ONCE)
                .setValidator(new ResponseValidator.AcceptAll())
                .build())

            // Step 2: Enter phone number
            .addStep(new USSDStep.Builder()
                .setStepNumber(2)
                .setDescription("Enter phone: " + phone)
                .setResponseToSend(phone)
                .setTimeout(8000)
                .setRetryPolicy(USSDStep.RetryPolicy.RETRY_ONCE)
                .setValidator(new ResponseValidator.AcceptAll())
                .build())

            // Step 3: Enter OTP and detect outcome
            .addStep(new USSDStep.Builder()
                .setStepNumber(3)
                .setDescription("Try OTP: " + otp)
                .setResponseToSend(otp)
                .setTimeout(10000)
                .setRetryPolicy(USSDStep.RetryPolicy.NO_RETRY)  // No retry - just try next OTP
                .setValidator(new ResponseValidator.AcceptAll())  // Accept any response
                .setExtractor(outcomeExtractor)  // Extract success/failure
                .setVariableName("success")
                .build())

            .setGlobalTimeout(30000)
            .setStopOnError(false)  // Don't stop on errors - we want to try all OTPs
            .build();
    }

    /**
     * Stops matching process
     */
    public void stopMatching() {
        if (!isProcessing.get()) {
            Log.w(TAG, "No matching in progress");
            return;
        }

        isProcessing.set(false);
        controller.stopBatch();
        Log.d(TAG, "Matching stopped by user");
    }

    /**
     * Gets current successful matches
     *
     * @return Map of phone → MatchResult
     */
    @NonNull
    public Map<String, MatchResult> getMatches() {
        return new HashMap<>(phoneOTPMatches);
    }

    /**
     * Gets list of phones that didn't match any OTP
     *
     * @return List of unmatched phone numbers
     */
    @NonNull
    public List<String> getUnmatchedPhones() {
        return new ArrayList<>(unmatchedPhones);
    }

    /**
     * Checks if matching is currently in progress
     *
     * @return true if processing
     */
    public boolean isProcessing() {
        return isProcessing.get();
    }

    /**
     * Gets current phone index being processed
     *
     * @return Phone index (0-based)
     */
    public int getCurrentPhoneIndex() {
        return currentPhoneIndex.get();
    }

    /**
     * Gets current OTP index being tried for current phone
     *
     * @return OTP index (0-based)
     */
    public int getCurrentOTPIndex() {
        return currentOTPIndex.get();
    }

    /**
     * Cleanup resources
     */
    public void cleanup() {
        stopMatching();
        phoneOTPMatches.clear();
        unmatchedPhones.clear();
        controller.cleanup();
    }
}
