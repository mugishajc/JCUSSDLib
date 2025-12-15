package com.jcussdlib.callback;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.jcussdlib.model.USSDStep;

import java.util.Map;

/**
 * Comprehensive callback interface for USSD sequence execution events.
 * <p>
 * Provides granular callbacks for every stage of sequence execution:
 * - Sequence lifecycle (started, completed, failed)
 * - Step lifecycle (started, completed, failed, retried)
 * - Progress tracking
 * - Data extraction results
 * </p>
 *
 * <p>All callbacks are invoked on the main thread for UI updates.</p>
 *
 * <p>Example implementation:</p>
 * <pre>
 * USSDSequenceCallback callback = new USSDSequenceCallback() {
 *     {@literal @}Override
 *     public void onSequenceStarted(String sequenceId, String sequenceName, int totalSteps) {
 *         showProgress("Starting " + sequenceName + " (" + totalSteps + " steps)");
 *     }
 *
 *     {@literal @}Override
 *     public void onStepStarted(int stepNumber, int totalSteps, String stepDescription) {
 *         updateProgress(stepNumber, totalSteps, stepDescription);
 *     }
 *
 *     {@literal @}Override
 *     public void onStepCompleted(int stepNumber, String response, long durationMs) {
 *         logSuccess("Step " + stepNumber + " completed in " + durationMs + "ms");
 *     }
 *
 *     {@literal @}Override
 *     public void onSequenceCompleted(Map&lt;String, String&gt; extractedData, long totalDurationMs) {
 *         String otp = extractedData.get("otp");
 *         if (otp != null) {
 *             saveOTP(otp);
 *         }
 *         showSuccess("Completed in " + totalDurationMs + "ms");
 *     }
 *
 *     {@literal @}Override
 *     public void onSequenceFailed(String error, int failedAtStep) {
 *         showError("Failed at step " + failedAtStep + ": " + error);
 *     }
 * };
 * </pre>
 *
 * @author Mugisha Jean Claude
 * @version 2.0.0
 * @since 2.0.0
 */
public interface USSDSequenceCallback {

    // ========================================================================================
    // SEQUENCE LIFECYCLE CALLBACKS
    // ========================================================================================

    /**
     * Called when sequence execution starts
     *
     * @param sequenceId   Unique identifier for this sequence execution
     * @param sequenceName Name of the sequence being executed
     * @param totalSteps   Total number of steps in the sequence
     */
    void onSequenceStarted(@NonNull String sequenceId, @NonNull String sequenceName, int totalSteps);

    /**
     * Called when entire sequence completes successfully
     *
     * @param extractedData   Data extracted from responses (OTP, balance, etc.)
     * @param totalDurationMs Total time taken to complete sequence
     */
    void onSequenceCompleted(@NonNull Map<String, String> extractedData, long totalDurationMs);

    /**
     * Called when sequence fails
     *
     * @param error        Error message describing the failure
     * @param failedAtStep Step number where failure occurred (1-indexed), or 0 if not step-specific
     */
    void onSequenceFailed(@NonNull String error, int failedAtStep);

    // ========================================================================================
    // STEP LIFECYCLE CALLBACKS
    // ========================================================================================

    /**
     * Called when a step starts executing
     *
     * @param stepNumber      Current step number (1-indexed)
     * @param totalSteps      Total steps in sequence
     * @param stepDescription Human-readable description of the step
     */
    void onStepStarted(int stepNumber, int totalSteps, @NonNull String stepDescription);

    /**
     * Called when a step completes successfully
     *
     * @param stepNumber Step number that completed (1-indexed)
     * @param response   The USSD response received for this step
     * @param durationMs Time taken to complete this step
     */
    void onStepCompleted(int stepNumber, @NonNull String response, long durationMs);

    /**
     * Called when a step fails
     *
     * @param stepNumber   Step number that failed (1-indexed)
     * @param error        Error message
     * @param attemptCount Number of attempts made (including initial attempt)
     */
    void onStepFailed(int stepNumber, @NonNull String error, int attemptCount);

    /**
     * Called when a step is being retried
     *
     * @param stepNumber     Step number being retried (1-indexed)
     * @param attemptNumber  Current attempt number (1 = first retry, 2 = second retry, etc.)
     * @param maxRetries     Maximum number of retries allowed
     * @param previousError  Error that caused the retry
     */
    void onStepRetrying(int stepNumber, int attemptNumber, int maxRetries, @NonNull String previousError);

    // ========================================================================================
    // PROGRESS & DATA CALLBACKS
    // ========================================================================================

    /**
     * Called to report overall progress
     *
     * @param completedSteps Number of steps completed
     * @param totalSteps     Total steps in sequence
     * @param percentComplete Progress percentage (0-100)
     */
    void onProgressUpdate(int completedSteps, int totalSteps, int percentComplete);

    /**
     * Called when data is extracted from a response
     * (e.g., OTP code, balance, transaction ID)
     *
     * @param dataKey   Key identifying the extracted data (e.g., "otp", "balance")
     * @param dataValue Extracted value
     * @param stepNumber Step where data was extracted
     */
    void onDataExtracted(@NonNull String dataKey, @NonNull String dataValue, int stepNumber);

    // ========================================================================================
    // OPTIONAL CALLBACKS (Default implementations provided)
    // ========================================================================================

    /**
     * Called when sequence execution is paused
     * Default: no-op
     *
     * @param currentStep Step number where execution was paused
     */
    default void onSequencePaused(int currentStep) {
        // Default: no-op
    }

    /**
     * Called when paused sequence is resumed
     * Default: no-op
     *
     * @param resumingFromStep Step number from which execution resumes
     */
    default void onSequenceResumed(int resumingFromStep) {
        // Default: no-op
    }

    /**
     * Called when sequence is cancelled by user
     * Default: no-op
     *
     * @param cancelledAtStep Step number where cancellation occurred
     */
    default void onSequenceCancelled(int cancelledAtStep) {
        // Default: no-op
    }

    /**
     * Called when a timeout occurs
     * Default: no-op
     *
     * @param stepNumber  Step that timed out
     * @param timeoutMs   Timeout value that was exceeded
     * @param willRetry   Whether the step will be retried
     */
    default void onStepTimeout(int stepNumber, long timeoutMs, boolean willRetry) {
        // Default: no-op
    }

    /**
     * Called when response validation fails
     * Default: no-op
     *
     * @param stepNumber       Step where validation failed
     * @param response         Response that failed validation
     * @param validationError  Validation error message
     * @param willRetry        Whether the step will be retried
     */
    default void onValidationFailed(int stepNumber, @NonNull String response,
                                   @NonNull String validationError, boolean willRetry) {
        // Default: no-op
    }

    // ========================================================================================
    // UTILITY: EMPTY IMPLEMENTATION
    // ========================================================================================

    /**
     * Empty implementation of USSDSequenceCallback for convenience.
     * <p>
     * Use this when you only need to override a few methods:
     * </p>
     * <pre>
     * USSDSequenceCallback callback = new USSDSequenceCallback.Empty() {
     *     {@literal @}Override
     *     public void onSequenceCompleted(Map&lt;String, String&gt; data, long duration) {
     *         // Handle completion only
     *     }
     * };
     * </pre>
     */
    class Empty implements USSDSequenceCallback {
        @Override
        public void onSequenceStarted(@NonNull String sequenceId, @NonNull String sequenceName, int totalSteps) {}

        @Override
        public void onSequenceCompleted(@NonNull Map<String, String> extractedData, long totalDurationMs) {}

        @Override
        public void onSequenceFailed(@NonNull String error, int failedAtStep) {}

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
    }
}
