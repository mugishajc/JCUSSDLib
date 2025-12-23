package com.jcussdlib.state;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.jcussdlib.model.USSDSequence;
import com.jcussdlib.model.USSDStep;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Maintains the runtime state of a USSD sequence execution.
 * <p>
 * Tracks:
 * - Current step being executed
 * - Responses received at each step
 * - Extracted data from responses
 * - Timing information per step
 * - Retry attempts
 * - Error history
 * </p>
 *
 * <p>Thread Safety: This class is now thread-safe with internal synchronization.
 * All state-modifying operations are synchronized to ensure safe concurrent access.</p>
 *
 * <p>Example usage:</p>
 * <pre>
 * USSDSessionState state = new USSDSessionState(sequence);
 * state.startExecution();
 * state.startStep(1);
 * state.recordResponse(1, "Welcome! Select option:");
 * state.completeStep(1, 1250); // 1.25 seconds
 * </pre>
 *
 * @author Mugisha Jean Claude
 * @version 2.0.0
 * @since 2.0.0
 */
public class USSDSessionState {

    /**
     * Execution status enum
     */
    public enum Status {
        IDLE,           // Not started
        RUNNING,        // Currently executing
        PAUSED,         // Temporarily paused
        COMPLETED,      // Successfully completed
        FAILED,         // Failed with error
        CANCELLED       // Cancelled by user
    }

    // Core state
    private final USSDSequence sequence;
    private final String sessionId;
    private Status status;
    private int currentStepIndex; // 0-based index
    private long sessionStartTime;
    private long sessionEndTime;

    // Step tracking
    private final List<StepExecutionRecord> stepRecords;
    private final Map<Integer, Integer> stepRetryCount; // stepNumber -> retryCount

    // Data storage
    private final Map<String, String> extractedData;
    private final List<String> responses;

    // Error tracking
    private String lastError;
    private int failedAtStep; // 1-indexed, 0 if not step-specific

    /**
     * Constructor
     *
     * @param sequence The sequence being executed
     */
    public USSDSessionState(@NonNull USSDSequence sequence) {
        this.sequence = sequence;
        this.sessionId = generateSessionId();
        this.status = Status.IDLE;
        this.currentStepIndex = -1;
        this.stepRecords = new ArrayList<>();
        this.stepRetryCount = new HashMap<>();
        this.extractedData = new HashMap<>();
        this.responses = new ArrayList<>();
        this.failedAtStep = 0;
    }

    // ========================================================================================
    // SESSION LIFECYCLE
    // ========================================================================================

    /**
     * Marks the session as started
     */
    public synchronized void startExecution() {
        if (status != Status.IDLE) {
            throw new IllegalStateException("Cannot start execution in status: " + status);
        }
        this.status = Status.RUNNING;
        this.sessionStartTime = System.currentTimeMillis();
        this.currentStepIndex = 0;
    }

    /**
     * Marks the session as completed successfully
     */
    public synchronized void completeExecution() {
        if (status != Status.RUNNING) {
            throw new IllegalStateException("Cannot complete execution in status: " + status);
        }
        this.status = Status.COMPLETED;
        this.sessionEndTime = System.currentTimeMillis();
    }

    /**
     * Marks the session as failed
     *
     * @param error Error message
     * @param atStep Step number where failure occurred (1-indexed), 0 if not step-specific
     */
    public synchronized void failExecution(@NonNull String error, int atStep) {
        this.status = Status.FAILED;
        this.lastError = error;
        this.failedAtStep = atStep;
        this.sessionEndTime = System.currentTimeMillis();
    }

    /**
     * Pauses the execution
     */
    public synchronized void pauseExecution() {
        if (status != Status.RUNNING) {
            throw new IllegalStateException("Cannot pause execution in status: " + status);
        }
        this.status = Status.PAUSED;
    }

    /**
     * Resumes paused execution
     */
    public synchronized void resumeExecution() {
        if (status != Status.PAUSED) {
            throw new IllegalStateException("Cannot resume execution in status: " + status);
        }
        this.status = Status.RUNNING;
    }

    /**
     * Cancels the execution
     */
    public synchronized void cancelExecution() {
        this.status = Status.CANCELLED;
        this.sessionEndTime = System.currentTimeMillis();
    }

    // ========================================================================================
    // STEP LIFECYCLE
    // ========================================================================================

    /**
     * Starts execution of a step
     *
     * @param stepNumber Step number (1-indexed)
     */
    public synchronized void startStep(int stepNumber) {
        if (status != Status.RUNNING) {
            throw new IllegalStateException("Cannot start step in status: " + status);
        }

        int index = stepNumber - 1;
        if (index < 0 || index >= sequence.getStepCount()) {
            throw new IllegalArgumentException("Invalid step number: " + stepNumber);
        }

        this.currentStepIndex = index;

        // Create record for this step
        StepExecutionRecord record = new StepExecutionRecord(stepNumber);
        record.startTime = System.currentTimeMillis();

        // Add or update record
        if (stepRecords.size() <= index) {
            stepRecords.add(record);
        } else {
            stepRecords.set(index, record);
        }
    }

    /**
     * Records a response for current step
     *
     * @param stepNumber Step number (1-indexed)
     * @param response   The response received
     */
    public synchronized void recordResponse(int stepNumber, @NonNull String response) {
        responses.add(response);

        int index = stepNumber - 1;
        if (index >= 0 && index < stepRecords.size()) {
            stepRecords.get(index).response = response;
        }
    }

    /**
     * Completes a step successfully
     *
     * @param stepNumber Step number (1-indexed)
     * @param durationMs Duration in milliseconds
     */
    public synchronized void completeStep(int stepNumber, long durationMs) {
        int index = stepNumber - 1;
        if (index >= 0 && index < stepRecords.size()) {
            StepExecutionRecord record = stepRecords.get(index);
            record.endTime = System.currentTimeMillis();
            record.durationMs = durationMs;
            record.success = true;
        }

        // Move to next step
        if (currentStepIndex < sequence.getStepCount() - 1) {
            currentStepIndex++;
        }
    }

    /**
     * Records a retry attempt for a step
     *
     * @param stepNumber Step number (1-indexed)
     */
    public synchronized void recordRetry(int stepNumber) {
        int retries = stepRetryCount.getOrDefault(stepNumber, 0);
        stepRetryCount.put(stepNumber, retries + 1);

        int index = stepNumber - 1;
        if (index >= 0 && index < stepRecords.size()) {
            stepRecords.get(index).retryCount = retries + 1;
        }
    }

    /**
     * Records extracted data from a response
     *
     * @param key   Data key (e.g., "otp", "balance")
     * @param value Data value
     */
    public synchronized void recordExtractedData(@NonNull String key, @NonNull String value) {
        extractedData.put(key, value);
    }

    // ========================================================================================
    // GETTERS
    // ========================================================================================

    @NonNull
    public USSDSequence getSequence() {
        return sequence;
    }

    @NonNull
    public String getSessionId() {
        return sessionId;
    }

    @NonNull
    public Status getStatus() {
        return status;
    }

    public int getCurrentStepNumber() {
        return currentStepIndex + 1; // Convert to 1-indexed
    }

    public int getCurrentStepIndex() {
        return currentStepIndex;
    }

    @Nullable
    public USSDStep getCurrentStep() {
        if (currentStepIndex < 0 || currentStepIndex >= sequence.getStepCount()) {
            return null;
        }
        return sequence.getStep(currentStepIndex);
    }

    public boolean isRunning() {
        return status == Status.RUNNING;
    }

    public boolean isCompleted() {
        return status == Status.COMPLETED;
    }

    public boolean isFailed() {
        return status == Status.FAILED;
    }

    public boolean isPaused() {
        return status == Status.PAUSED;
    }

    public long getSessionStartTime() {
        return sessionStartTime;
    }

    public long getSessionEndTime() {
        return sessionEndTime;
    }

    public long getTotalDuration() {
        if (sessionStartTime == 0) {
            return 0;
        }
        long endTime = sessionEndTime != 0 ? sessionEndTime : System.currentTimeMillis();
        return endTime - sessionStartTime;
    }

    public int getCompletedStepsCount() {
        int count = 0;
        for (StepExecutionRecord record : stepRecords) {
            if (record.success) {
                count++;
            }
        }
        return count;
    }

    public int getProgressPercentage() {
        if (sequence.getStepCount() == 0) {
            return 0;
        }
        return (getCompletedStepsCount() * 100) / sequence.getStepCount();
    }

    @NonNull
    public List<String> getResponses() {
        return Collections.unmodifiableList(responses);
    }

    @Nullable
    public String getResponseForStep(int stepNumber) {
        int index = stepNumber - 1;
        if (index >= 0 && index < stepRecords.size()) {
            return stepRecords.get(index).response;
        }
        return null;
    }

    @NonNull
    public Map<String, String> getExtractedData() {
        return Collections.unmodifiableMap(extractedData);
    }

    @Nullable
    public String getExtractedData(@NonNull String key) {
        return extractedData.get(key);
    }

    public int getRetryCount(int stepNumber) {
        return stepRetryCount.getOrDefault(stepNumber, 0);
    }

    @Nullable
    public String getLastError() {
        return lastError;
    }

    public int getFailedAtStep() {
        return failedAtStep;
    }

    @NonNull
    public List<StepExecutionRecord> getStepRecords() {
        return Collections.unmodifiableList(stepRecords);
    }

    @Nullable
    public StepExecutionRecord getStepRecord(int stepNumber) {
        int index = stepNumber - 1;
        if (index >= 0 && index < stepRecords.size()) {
            return stepRecords.get(index);
        }
        return null;
    }

    // ========================================================================================
    // UTILITY
    // ========================================================================================

    /**
     * Generates a unique session ID
     */
    private static String generateSessionId() {
        return "session_" + System.currentTimeMillis() + "_" + (int) (Math.random() * 10000);
    }

    @Override
    public String toString() {
        return "USSDSessionState{" +
                "sessionId='" + sessionId + '\'' +
                ", status=" + status +
                ", currentStep=" + getCurrentStepNumber() +
                ", progress=" + getProgressPercentage() + "%" +
                ", duration=" + getTotalDuration() + "ms" +
                '}';
    }

    /**
     * Record of a single step execution
     */
    public static class StepExecutionRecord {
        public final int stepNumber;
        public long startTime;
        public long endTime;
        public long durationMs;
        public String response;
        public boolean success;
        public int retryCount;

        public StepExecutionRecord(int stepNumber) {
            this.stepNumber = stepNumber;
            this.success = false;
            this.retryCount = 0;
        }

        @Override
        public String toString() {
            return "Step " + stepNumber +
                    " [duration=" + durationMs + "ms" +
                    ", retries=" + retryCount +
                    ", success=" + success + "]";
        }
    }
}
