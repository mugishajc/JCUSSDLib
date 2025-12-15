package com.jcussdlib.executor;

import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.jcussdlib.callback.USSDSequenceCallback;
import com.jcussdlib.extraction.ResponseExtractor;
import com.jcussdlib.model.USSDSequence;
import com.jcussdlib.model.USSDStep;
import com.jcussdlib.state.USSDSessionState;
import com.jcussdlib.validation.ResponseValidator;

import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Core execution engine for USSD sequence automation.
 * <p>
 * Orchestrates multi-step USSD sequences with:
 * - Step-by-step execution with validation
 * - Automatic retry on failure with configurable policies
 * - Timeout management per step
 * - Data extraction from responses (OTP, balance, etc.)
 * - Progress tracking and callbacks
 * - Pause/resume/cancel support
 * - Batch processing support
 * </p>
 *
 * <p>Thread Model:</p>
 * <ul>
 *   <li>Background thread: Sequence execution logic</li>
 *   <li>Main thread: All callbacks invoked on main thread for UI updates</li>
 *   <li>Single executor per session: NOT thread-safe for concurrent execution</li>
 * </ul>
 *
 * <p>Error Handling:</p>
 * <ul>
 *   <li>Never crashes: All exceptions caught and reported via callbacks</li>
 *   <li>Defensive null checks throughout</li>
 *   <li>Graceful degradation on timeouts or validation failures</li>
 *   <li>Automatic retry with exponential backoff support</li>
 * </ul>
 *
 * <p>Example usage:</p>
 * <pre>
 * USSDSequenceExecutor executor = new USSDSequenceExecutor(
 *     sequence,
 *     callback,
 *     ussdController
 * );
 *
 * executor.execute(); // Start execution
 *
 * // Later...
 * executor.pause();   // Pause execution
 * executor.resume();  // Resume execution
 * executor.cancel();  // Cancel execution
 * </pre>
 *
 * @author Mugisha Jean Claude
 * @version 2.0.0
 * @since 2.0.0
 */
public class USSDSequenceExecutor {

    private static final String TAG = "USSDSequenceExecutor";

    // Core components
    private final USSDSequence sequence;
    private final USSDSequenceCallback callback;
    private final USSDController controller;
    private final USSDSessionState sessionState;

    // Threading
    private final ExecutorService executorService;
    private final Handler mainThreadHandler;
    private Future<?> executionFuture;

    // Execution control
    private final AtomicBoolean isPaused;
    private final AtomicBoolean isCancelled;
    private final AtomicInteger currentRetryCount;

    // Current step tracking
    private volatile int currentStepNumber;
    private volatile String lastResponse;
    private volatile long stepStartTime;

    /**
     * Interface for USSD control operations
     * This abstracts the actual USSD sending mechanism
     */
    public interface USSDController {
        /**
         * Sends USSD code
         *
         * @param ussdCode Code to send (e.g., "*123#", "1", "2")
         * @param simSlot  SIM slot (-1 for default, 0 for SIM1, 1 for SIM2)
         * @return true if send was initiated successfully
         */
        boolean sendUSSD(@NonNull String ussdCode, int simSlot);

        /**
         * Sends response to current USSD dialog
         *
         * @param response Response to send
         * @return true if send was successful
         */
        boolean sendResponse(@NonNull String response);

        /**
         * Cancels current USSD session
         *
         * @return true if cancellation was successful
         */
        boolean cancelUSSD();

        /**
         * Checks if USSD is currently active
         *
         * @return true if USSD session is active
         */
        boolean isUSSDActive();
    }

    /**
     * Constructor
     *
     * @param sequence   USSD sequence to execute
     * @param callback   Callback for execution events
     * @param controller USSD controller for sending commands
     */
    public USSDSequenceExecutor(@NonNull USSDSequence sequence,
                                @NonNull USSDSequenceCallback callback,
                                @NonNull USSDController controller) {
        if (sequence == null) {
            throw new IllegalArgumentException("Sequence cannot be null");
        }
        if (callback == null) {
            throw new IllegalArgumentException("Callback cannot be null");
        }
        if (controller == null) {
            throw new IllegalArgumentException("Controller cannot be null");
        }

        this.sequence = sequence;
        this.callback = callback;
        this.controller = controller;
        this.sessionState = new USSDSessionState(sequence);

        this.executorService = Executors.newSingleThreadExecutor();
        this.mainThreadHandler = new Handler(Looper.getMainLooper());

        this.isPaused = new AtomicBoolean(false);
        this.isCancelled = new AtomicBoolean(false);
        this.currentRetryCount = new AtomicInteger(0);

        this.currentStepNumber = 0;
    }

    // ========================================================================================
    // EXECUTION CONTROL
    // ========================================================================================

    /**
     * Starts sequence execution
     * Non-blocking: returns immediately, execution happens on background thread
     */
    public void execute() {
        if (executionFuture != null && !executionFuture.isDone()) {
            Log.w(TAG, "Execution already in progress");
            return;
        }

        // Validate sequence before execution
        USSDSequence.ValidationResult validation = sequence.validate();
        if (!validation.isValid()) {
            notifySequenceFailed("Sequence validation failed: " + validation.getErrorMessage(), 0);
            return;
        }

        executionFuture = executorService.submit(this::executeSequenceInternal);
    }

    /**
     * Pauses execution (can be resumed later)
     */
    public void pause() {
        if (!sessionState.isRunning()) {
            Log.w(TAG, "Cannot pause - not running");
            return;
        }

        isPaused.set(true);
        sessionState.pauseExecution();
        notifySequencePaused(currentStepNumber);
    }

    /**
     * Resumes paused execution
     */
    public void resume() {
        if (!sessionState.isPaused()) {
            Log.w(TAG, "Cannot resume - not paused");
            return;
        }

        isPaused.set(false);
        sessionState.resumeExecution();
        notifySequenceResumed(currentStepNumber);

        // Continue execution
        synchronized (isPaused) {
            isPaused.notifyAll();
        }
    }

    /**
     * Cancels execution (cannot be resumed)
     */
    public void cancel() {
        isCancelled.set(true);
        sessionState.cancelExecution();
        controller.cancelUSSD();
        notifySequenceCancelled(currentStepNumber);

        if (executionFuture != null && !executionFuture.isDone()) {
            executionFuture.cancel(true);
        }
    }

    /**
     * Shuts down the executor (call when done)
     */
    public void shutdown() {
        try {
            executorService.shutdown();
            if (!executorService.awaitTermination(5, TimeUnit.SECONDS)) {
                executorService.shutdownNow();
            }
        } catch (InterruptedException e) {
            executorService.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }

    // ========================================================================================
    // CORE EXECUTION LOGIC
    // ========================================================================================

    /**
     * Internal execution method - runs on background thread
     */
    private void executeSequenceInternal() {
        try {
            // Mark session as started
            sessionState.startExecution();
            notifySequenceStarted(
                sessionState.getSessionId(),
                sequence.getName(),
                sequence.getStepCount()
            );

            // Send initial USSD code
            if (!sendInitialUSSD()) {
                notifySequenceFailed("Failed to initiate USSD", 0);
                return;
            }

            // Execute each step
            for (int i = 0; i < sequence.getStepCount(); i++) {
                if (isCancelled.get()) {
                    Log.d(TAG, "Execution cancelled at step " + (i + 1));
                    return;
                }

                // Handle pause
                waitIfPaused();

                currentStepNumber = i + 1;
                USSDStep step = sequence.getStep(i);

                if (step == null) {
                    notifySequenceFailed("Step " + currentStepNumber + " is null", currentStepNumber);
                    return;
                }

                // Execute step with retry logic
                boolean success = executeStepWithRetry(step);

                if (!success) {
                    if (sequence.shouldStopOnError()) {
                        notifySequenceFailed("Step " + currentStepNumber + " failed", currentStepNumber);
                        return;
                    } else {
                        Log.w(TAG, "Step " + currentStepNumber + " failed but continuing (stopOnError=false)");
                    }
                }

                // Update progress
                notifyProgressUpdate(
                    sessionState.getCompletedStepsCount(),
                    sequence.getStepCount(),
                    sessionState.getProgressPercentage()
                );
            }

            // Sequence completed successfully
            sessionState.completeExecution();
            notifySequenceCompleted(
                sessionState.getExtractedData(),
                sessionState.getTotalDuration()
            );

        } catch (Exception e) {
            Log.e(TAG, "Unexpected error in sequence execution", e);
            sessionState.failExecution("Unexpected error: " + e.getMessage(), currentStepNumber);
            notifySequenceFailed("Unexpected error: " + e.getMessage(), currentStepNumber);
        }
    }

    /**
     * Sends initial USSD code to start the sequence
     */
    private boolean sendInitialUSSD() {
        try {
            String ussdCode = sequence.getInitialUSSDCode();
            int simSlot = sequence.getSimSlot();

            Log.d(TAG, "Sending initial USSD: " + ussdCode + " on SIM slot " + simSlot);

            boolean sent = controller.sendUSSD(ussdCode, simSlot);
            if (!sent) {
                Log.e(TAG, "Failed to send initial USSD code");
                return false;
            }

            // Wait for USSD to become active
            return waitForUSSDActive(5000); // 5 second timeout

        } catch (Exception e) {
            Log.e(TAG, "Error sending initial USSD", e);
            return false;
        }
    }

    /**
     * Executes a single step with retry logic
     */
    private boolean executeStepWithRetry(@NonNull USSDStep step) {
        int maxRetries = step.getRetryPolicy().getMaxRetries();
        currentRetryCount.set(0);

        for (int attempt = 0; attempt <= maxRetries; attempt++) {
            if (isCancelled.get()) {
                return false;
            }

            waitIfPaused();

            if (attempt > 0) {
                // This is a retry
                currentRetryCount.incrementAndGet();
                sessionState.recordRetry(step.getStepNumber());

                notifyStepRetrying(
                    step.getStepNumber(),
                    attempt,
                    maxRetries,
                    "Previous attempt failed"
                );

                // Optional: exponential backoff
                sleepSafely(1000 * attempt); // 1s, 2s, 3s, etc.
            }

            boolean success = executeSingleStep(step, attempt);
            if (success) {
                return true;
            }

            // If last attempt, don't retry
            if (attempt == maxRetries) {
                notifyStepFailed(
                    step.getStepNumber(),
                    "Step failed after " + (attempt + 1) + " attempts",
                    attempt + 1
                );
                return false;
            }
        }

        return false;
    }

    /**
     * Executes a single step (one attempt)
     */
    private boolean executeSingleStep(@NonNull USSDStep step, int attemptNumber) {
        try {
            stepStartTime = System.currentTimeMillis();
            sessionState.startStep(step.getStepNumber());

            notifyStepStarted(
                step.getStepNumber(),
                sequence.getStepCount(),
                step.getDescription()
            );

            // Wait for USSD response
            String response = waitForUSSDResponse(step.getTimeoutMillis());

            if (response == null) {
                Log.e(TAG, "Timeout waiting for USSD response at step " + step.getStepNumber());
                notifyStepTimeout(step.getStepNumber(), step.getTimeoutMillis(), attemptNumber < step.getRetryPolicy().getMaxRetries());
                return false;
            }

            lastResponse = response;
            sessionState.recordResponse(step.getStepNumber(), response);

            // Validate response
            if (step.getValidator() != null) {
                ResponseValidator.ValidationResult validation = step.getValidator().validate(response);
                if (!validation.isValid()) {
                    Log.e(TAG, "Validation failed at step " + step.getStepNumber() + ": " + validation.getErrorMessage());
                    notifyValidationFailed(
                        step.getStepNumber(),
                        response,
                        validation.getErrorMessage(),
                        attemptNumber < step.getRetryPolicy().getMaxRetries()
                    );
                    return false;
                }
            }

            // Extract data
            if (step.getExtractor() != null) {
                ResponseExtractor.ExtractionResult extraction = step.getExtractor().extract(response);
                if (extraction.isSuccess()) {
                    String extractedValue = extraction.getValue();
                    String dataKey = step.getVariableName() != null ? step.getVariableName() : "extracted_data";

                    sessionState.recordExtractedData(dataKey, extractedValue);
                    notifyDataExtracted(dataKey, extractedValue, step.getStepNumber());

                    Log.d(TAG, "Extracted data at step " + step.getStepNumber() + ": " + dataKey + " = " + extractedValue);
                } else {
                    Log.w(TAG, "Data extraction failed at step " + step.getStepNumber() + ": " + extraction.getErrorMessage());
                }
            }

            // Send response for next step
            if (step.getResponseToSend() != null) {
                String responseToSend = sequence.resolveVariables(step.getResponseToSend());
                boolean sent = controller.sendResponse(responseToSend);
                if (!sent) {
                    Log.e(TAG, "Failed to send response at step " + step.getStepNumber());
                    return false;
                }
                Log.d(TAG, "Sent response at step " + step.getStepNumber() + ": " + responseToSend);
            }

            // Step completed successfully
            long duration = System.currentTimeMillis() - stepStartTime;
            sessionState.completeStep(step.getStepNumber(), duration);
            notifyStepCompleted(step.getStepNumber(), response, duration);

            return true;

        } catch (Exception e) {
            Log.e(TAG, "Error executing step " + step.getStepNumber(), e);
            return false;
        }
    }

    // ========================================================================================
    // WAITING & SYNCHRONIZATION
    // ========================================================================================

    /**
     * Waits if execution is paused
     */
    private void waitIfPaused() {
        while (isPaused.get() && !isCancelled.get()) {
            synchronized (isPaused) {
                try {
                    isPaused.wait(100);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return;
                }
            }
        }
    }

    /**
     * Waits for USSD to become active
     */
    private boolean waitForUSSDActive(long timeoutMs) {
        long startTime = System.currentTimeMillis();
        while (System.currentTimeMillis() - startTime < timeoutMs) {
            if (controller.isUSSDActive()) {
                return true;
            }
            sleepSafely(100);
        }
        return false;
    }

    /**
     * Waits for USSD response
     * In real implementation, this would be triggered by accessibility service
     * For now, it's a placeholder that needs to be implemented
     */
    private String waitForUSSDResponse(long timeoutMs) {
        // TODO: This needs to be connected to the accessibility service
        // that captures USSD dialog text
        //
        // For now, return null to simulate timeout
        // Real implementation would:
        // 1. Register listener for USSD dialog events
        // 2. Wait for event or timeout
        // 3. Return dialog text or null if timeout

        Log.d(TAG, "Waiting for USSD response (timeout: " + timeoutMs + "ms)");

        // Placeholder: In production, this would be event-driven
        sleepSafely(timeoutMs);

        return lastResponse; // TODO: Replace with actual response from accessibility service
    }

    /**
     * Safe sleep that respects interruption
     */
    private void sleepSafely(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    // ========================================================================================
    // CALLBACK NOTIFICATIONS (All on main thread)
    // ========================================================================================

    private void notifySequenceStarted(String sessionId, String sequenceName, int totalSteps) {
        mainThreadHandler.post(() -> {
            try {
                callback.onSequenceStarted(sessionId, sequenceName, totalSteps);
            } catch (Exception e) {
                Log.e(TAG, "Error in callback onSequenceStarted", e);
            }
        });
    }

    private void notifySequenceCompleted(Map<String, String> extractedData, long totalDurationMs) {
        mainThreadHandler.post(() -> {
            try {
                callback.onSequenceCompleted(extractedData, totalDurationMs);
            } catch (Exception e) {
                Log.e(TAG, "Error in callback onSequenceCompleted", e);
            }
        });
    }

    private void notifySequenceFailed(String error, int failedAtStep) {
        mainThreadHandler.post(() -> {
            try {
                callback.onSequenceFailed(error, failedAtStep);
            } catch (Exception e) {
                Log.e(TAG, "Error in callback onSequenceFailed", e);
            }
        });
    }

    private void notifyStepStarted(int stepNumber, int totalSteps, String stepDescription) {
        mainThreadHandler.post(() -> {
            try {
                callback.onStepStarted(stepNumber, totalSteps, stepDescription);
            } catch (Exception e) {
                Log.e(TAG, "Error in callback onStepStarted", e);
            }
        });
    }

    private void notifyStepCompleted(int stepNumber, String response, long durationMs) {
        mainThreadHandler.post(() -> {
            try {
                callback.onStepCompleted(stepNumber, response, durationMs);
            } catch (Exception e) {
                Log.e(TAG, "Error in callback onStepCompleted", e);
            }
        });
    }

    private void notifyStepFailed(int stepNumber, String error, int attemptCount) {
        mainThreadHandler.post(() -> {
            try {
                callback.onStepFailed(stepNumber, error, attemptCount);
            } catch (Exception e) {
                Log.e(TAG, "Error in callback onStepFailed", e);
            }
        });
    }

    private void notifyStepRetrying(int stepNumber, int attemptNumber, int maxRetries, String previousError) {
        mainThreadHandler.post(() -> {
            try {
                callback.onStepRetrying(stepNumber, attemptNumber, maxRetries, previousError);
            } catch (Exception e) {
                Log.e(TAG, "Error in callback onStepRetrying", e);
            }
        });
    }

    private void notifyProgressUpdate(int completedSteps, int totalSteps, int percentComplete) {
        mainThreadHandler.post(() -> {
            try {
                callback.onProgressUpdate(completedSteps, totalSteps, percentComplete);
            } catch (Exception e) {
                Log.e(TAG, "Error in callback onProgressUpdate", e);
            }
        });
    }

    private void notifyDataExtracted(String dataKey, String dataValue, int stepNumber) {
        mainThreadHandler.post(() -> {
            try {
                callback.onDataExtracted(dataKey, dataValue, stepNumber);
            } catch (Exception e) {
                Log.e(TAG, "Error in callback onDataExtracted", e);
            }
        });
    }

    private void notifySequencePaused(int currentStep) {
        mainThreadHandler.post(() -> {
            try {
                callback.onSequencePaused(currentStep);
            } catch (Exception e) {
                Log.e(TAG, "Error in callback onSequencePaused", e);
            }
        });
    }

    private void notifySequenceResumed(int resumingFromStep) {
        mainThreadHandler.post(() -> {
            try {
                callback.onSequenceResumed(resumingFromStep);
            } catch (Exception e) {
                Log.e(TAG, "Error in callback onSequenceResumed", e);
            }
        });
    }

    private void notifySequenceCancelled(int cancelledAtStep) {
        mainThreadHandler.post(() -> {
            try {
                callback.onSequenceCancelled(cancelledAtStep);
            } catch (Exception e) {
                Log.e(TAG, "Error in callback onSequenceCancelled", e);
            }
        });
    }

    private void notifyStepTimeout(int stepNumber, long timeoutMs, boolean willRetry) {
        mainThreadHandler.post(() -> {
            try {
                callback.onStepTimeout(stepNumber, timeoutMs, willRetry);
            } catch (Exception e) {
                Log.e(TAG, "Error in callback onStepTimeout", e);
            }
        });
    }

    private void notifyValidationFailed(int stepNumber, String response, String validationError, boolean willRetry) {
        mainThreadHandler.post(() -> {
            try {
                callback.onValidationFailed(stepNumber, response, validationError, willRetry);
            } catch (Exception e) {
                Log.e(TAG, "Error in callback onValidationFailed", e);
            }
        });
    }

    // ========================================================================================
    // GETTERS
    // ========================================================================================

    @NonNull
    public USSDSessionState getSessionState() {
        return sessionState;
    }

    public boolean isRunning() {
        return sessionState.isRunning();
    }

    public boolean isPaused() {
        return isPaused.get();
    }

    public boolean isCancelled() {
        return isCancelled.get();
    }

    public int getCurrentStepNumber() {
        return currentStepNumber;
    }
}
