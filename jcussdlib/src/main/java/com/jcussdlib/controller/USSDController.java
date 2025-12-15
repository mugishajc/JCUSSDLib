package com.jcussdlib.controller;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.telephony.TelephonyManager;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresPermission;
import androidx.core.content.ContextCompat;

import com.jcussdlib.callback.USSDSequenceCallback;
import com.jcussdlib.executor.USSDSequenceExecutor;
import com.jcussdlib.model.USSDSequence;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Main controller for USSD automation.
 * <p>
 * Provides high-level API for:
 * - Single USSD code execution
 * - Multi-step sequence execution
 * - Batch processing (100+ sequences)
 * - Dual SIM support
 * - Accessibility service integration
 * - Response capture and parsing
 * </p>
 *
 * <p>Architecture:</p>
 * <ul>
 *   <li>Singleton pattern: Single instance manages all USSD operations</li>
 *   <li>Event-driven: Uses callbacks for async communication</li>
 *   <li>Thread-safe: Synchronized access to shared state</li>
 *   <li>Permission-aware: Checks and enforces required permissions</li>
 * </ul>
 *
 * <p>Usage:</p>
 * <pre>
 * // Initialize
 * USSDController controller = USSDController.getInstance(context);
 *
 * // Execute simple USSD
 * controller.dial("*123#", 0);
 *
 * // Execute sequence
 * USSDSequence sequence = new USSDSequence.Builder()
 *     .setName("Get OTP")
 *     .setInitialUSSDCode("*182*8*1#")
 *     .addStep(step1)
 *     .addStep(step2)
 *     .build();
 *
 * controller.executeSequence(sequence, callback);
 *
 * // Batch processing
 * List&lt;USSDSequence&gt; sequences = createSequencesForPhones(phoneList);
 * controller.executeBatch(sequences, batchCallback, 2000); // 2s delay between
 * </pre>
 *
 * @author Mugisha Jean Claude
 * @version 2.0.0
 * @since 2.0.0
 */
public class USSDController implements USSDSequenceExecutor.USSDController {

    private static final String TAG = "USSDController";

    // Singleton instance
    private static volatile USSDController instance;

    // Core dependencies
    private final Context context;
    private final Handler mainHandler;

    // USSD state
    private final AtomicBoolean isUSSDActive;
    private volatile String lastUSSDResponse;
    private final Map<String, USSDSequenceExecutor> activeExecutors;

    // Batch processing
    private final Queue<BatchTask> batchQueue;
    private final AtomicBoolean isBatchProcessing;

    // Response listeners
    private final List<USSDResponseListener> responseListeners;

    /**
     * Listener for USSD responses
     * This will be triggered by the accessibility service
     */
    public interface USSDResponseListener {
        void onUSSDResponse(@NonNull String response);
    }

    /**
     * Callback for batch processing progress
     */
    public interface BatchCallback {
        void onBatchStarted(int totalSequences);
        void onSequenceCompleted(int sequenceIndex, int totalSequences, @NonNull Map<String, String> extractedData);
        void onSequenceFailed(int sequenceIndex, int totalSequences, @NonNull String error);
        void onBatchCompleted(int successCount, int failureCount, long totalDurationMs);
    }

    /**
     * Private constructor - use getInstance()
     */
    private USSDController(@NonNull Context context) {
        this.context = context.getApplicationContext();
        this.mainHandler = new Handler(Looper.getMainLooper());
        this.isUSSDActive = new AtomicBoolean(false);
        this.activeExecutors = new ConcurrentHashMap<>();
        this.batchQueue = new LinkedList<>();
        this.isBatchProcessing = new AtomicBoolean(false);
        this.responseListeners = new ArrayList<>();
    }

    /**
     * Gets singleton instance
     *
     * @param context Application context
     * @return USSDController instance
     */
    @NonNull
    public static USSDController getInstance(@NonNull Context context) {
        if (instance == null) {
            synchronized (USSDController.class) {
                if (instance == null) {
                    instance = new USSDController(context);
                }
            }
        }
        return instance;
    }

    // ========================================================================================
    // SIMPLE USSD OPERATIONS
    // ========================================================================================

    /**
     * Dials a USSD code
     *
     * @param ussdCode USSD code to dial (e.g., "*123#", "*182*8*1#")
     * @param simSlot  SIM slot (-1 for default, 0 for SIM1, 1 for SIM2)
     * @return true if dial was initiated successfully
     */
    @RequiresPermission(Manifest.permission.CALL_PHONE)
    public boolean dial(@NonNull String ussdCode, int simSlot) {
        if (ussdCode == null || ussdCode.trim().isEmpty()) {
            Log.e(TAG, "USSD code cannot be null or empty");
            return false;
        }

        if (!checkPermissions()) {
            Log.e(TAG, "Missing required permissions");
            return false;
        }

        try {
            String encodedCode = Uri.encode(ussdCode);
            return sendUSSD(encodedCode, simSlot);
        } catch (Exception e) {
            Log.e(TAG, "Error dialing USSD code", e);
            return false;
        }
    }

    /**
     * Sends response to active USSD session
     *
     * @param response Response to send (e.g., "1", "2", "0")
     * @return true if response was sent successfully
     */
    @Override
    public boolean sendResponse(@NonNull String response) {
        if (response == null) {
            Log.e(TAG, "Response cannot be null");
            return false;
        }

        if (!isUSSDActive.get()) {
            Log.w(TAG, "No active USSD session");
            return false;
        }

        try {
            // In production, this would trigger accessibility service action
            // to click the input field, type the response, and click send
            Log.d(TAG, "Sending USSD response: " + response);

            // TODO: Integrate with accessibility service
            // For now, just log

            return true;
        } catch (Exception e) {
            Log.e(TAG, "Error sending USSD response", e);
            return false;
        }
    }

    /**
     * Cancels active USSD session
     *
     * @return true if cancellation was successful
     */
    @Override
    public boolean cancelUSSD() {
        if (!isUSSDActive.get()) {
            Log.w(TAG, "No active USSD session to cancel");
            return false;
        }

        try {
            // In production, this would trigger accessibility service action
            // to click the cancel/dismiss button
            Log.d(TAG, "Cancelling USSD session");

            isUSSDActive.set(false);
            lastUSSDResponse = null;

            // TODO: Integrate with accessibility service

            return true;
        } catch (Exception e) {
            Log.e(TAG, "Error cancelling USSD", e);
            return false;
        }
    }

    // ========================================================================================
    // SEQUENCE EXECUTION
    // ========================================================================================

    /**
     * Executes a USSD sequence
     *
     * @param sequence USSD sequence to execute
     * @param callback Callback for sequence events
     * @return Executor instance (can be used to pause/resume/cancel)
     */
    @NonNull
    @RequiresPermission(Manifest.permission.CALL_PHONE)
    public USSDSequenceExecutor executeSequence(@NonNull USSDSequence sequence,
                                                @NonNull USSDSequenceCallback callback) {
        if (sequence == null) {
            throw new IllegalArgumentException("Sequence cannot be null");
        }
        if (callback == null) {
            throw new IllegalArgumentException("Callback cannot be null");
        }

        if (!checkPermissions()) {
            throw new SecurityException("Missing required permissions");
        }

        // Create executor
        USSDSequenceExecutor executor = new USSDSequenceExecutor(sequence, callback, this);

        // Track active executor
        String sessionId = sequence.getSequenceId();
        activeExecutors.put(sessionId, executor);

        // Execute
        executor.execute();

        return executor;
    }

    /**
     * Executes multiple sequences in batch (sequential processing)
     *
     * @param sequences     List of sequences to execute
     * @param callback      Callback for batch progress
     * @param delayBetweenMs Delay between sequences (milliseconds)
     */
    @RequiresPermission(Manifest.permission.CALL_PHONE)
    public void executeBatch(@NonNull List<USSDSequence> sequences,
                            @NonNull BatchCallback callback,
                            long delayBetweenMs) {
        if (sequences == null || sequences.isEmpty()) {
            throw new IllegalArgumentException("Sequences list cannot be null or empty");
        }
        if (callback == null) {
            throw new IllegalArgumentException("Callback cannot be null");
        }

        if (!checkPermissions()) {
            throw new SecurityException("Missing required permissions");
        }

        if (isBatchProcessing.get()) {
            Log.w(TAG, "Batch processing already in progress");
            return;
        }

        // Queue all sequences
        synchronized (batchQueue) {
            batchQueue.clear();
            for (USSDSequence sequence : sequences) {
                batchQueue.offer(new BatchTask(sequence, delayBetweenMs));
            }
        }

        // Start batch processing
        processBatchAsync(callback, sequences.size());
    }

    /**
     * Stops batch processing
     */
    public void stopBatch() {
        if (!isBatchProcessing.get()) {
            Log.w(TAG, "No batch processing in progress");
            return;
        }

        synchronized (batchQueue) {
            batchQueue.clear();
        }

        isBatchProcessing.set(false);
        Log.d(TAG, "Batch processing stopped");
    }

    /**
     * Internal batch processing logic
     */
    private void processBatchAsync(@NonNull BatchCallback callback, int totalSequences) {
        isBatchProcessing.set(true);

        final long batchStartTime = System.currentTimeMillis();
        final int[] successCount = {0};
        final int[] failureCount = {0};
        final int[] currentIndex = {0};

        callback.onBatchStarted(totalSequences);

        // Process queue sequentially on background thread
        new Thread(() -> {
            try {
                while (isBatchProcessing.get()) {
                    BatchTask task;
                    synchronized (batchQueue) {
                        task = batchQueue.poll();
                    }

                    if (task == null) {
                        // Queue empty - batch complete
                        break;
                    }

                    final int index = currentIndex[0]++;
                    final USSDSequence sequence = task.sequence;

                    // Create single-use callback for this sequence
                    USSDSequenceCallback sequenceCallback = new USSDSequenceCallback() {
                        @Override
                        public void onSequenceStarted(@NonNull String sessionId, @NonNull String sequenceName, int totalSteps) {
                            Log.d(TAG, "Batch [" + (index + 1) + "/" + totalSequences + "] started: " + sequenceName);
                        }

                        @Override
                        public void onSequenceCompleted(@NonNull Map<String, String> extractedData, long totalDurationMs) {
                            successCount[0]++;
                            callback.onSequenceCompleted(index + 1, totalSequences, extractedData);
                            Log.d(TAG, "Batch [" + (index + 1) + "/" + totalSequences + "] completed");
                        }

                        @Override
                        public void onSequenceFailed(@NonNull String error, int failedAtStep) {
                            failureCount[0]++;
                            callback.onSequenceFailed(index + 1, totalSequences, error);
                            Log.e(TAG, "Batch [" + (index + 1) + "/" + totalSequences + "] failed: " + error);
                        }

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
                    };

                    // Execute sequence and wait for completion
                    USSDSequenceExecutor executor = executeSequence(sequence, sequenceCallback);

                    // Wait for this sequence to complete before starting next
                    while (executor.isRunning() && !executor.isCancelled()) {
                        try {
                            Thread.sleep(500); // Check every 500ms
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                            break;
                        }
                    }

                    // Cleanup
                    executor.shutdown();
                    activeExecutors.remove(sequence.getSequenceId());

                    // Delay before next sequence
                    if (task.delayMs > 0 && !batchQueue.isEmpty()) {
                        try {
                            Thread.sleep(task.delayMs);
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                            break;
                        }
                    }
                }

                // Batch complete
                final long totalDuration = System.currentTimeMillis() - batchStartTime;
                mainHandler.post(() -> callback.onBatchCompleted(
                    successCount[0],
                    failureCount[0],
                    totalDuration
                ));

            } catch (Exception e) {
                Log.e(TAG, "Error in batch processing", e);
            } finally {
                isBatchProcessing.set(false);
            }
        }, "USSD-Batch-Processor").start();
    }

    // ========================================================================================
    // USSD CONTROLLER INTERFACE IMPLEMENTATION
    // ========================================================================================

    @Override
    public boolean sendUSSD(@NonNull String ussdCode, int simSlot) {
        if (ussdCode == null || ussdCode.trim().isEmpty()) {
            Log.e(TAG, "USSD code cannot be null or empty");
            return false;
        }

        try {
            // Mark USSD as active
            isUSSDActive.set(true);

            // Use reflection for dual SIM support
            if (simSlot >= 0 && Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP_MR1) {
                return sendUSSDWithSIMSlot(ussdCode, simSlot);
            } else {
                return sendUSSDDefault(ussdCode);
            }
        } catch (Exception e) {
            Log.e(TAG, "Error sending USSD", e);
            isUSSDActive.set(false);
            return false;
        }
    }

    @Override
    public boolean isUSSDActive() {
        return isUSSDActive.get();
    }

    // ========================================================================================
    // RESPONSE HANDLING
    // ========================================================================================

    /**
     * Called by accessibility service when USSD response is received
     *
     * @param response USSD response text
     */
    public void onUSSDResponseReceived(@NonNull String response) {
        if (response == null) {
            Log.w(TAG, "Received null USSD response");
            return;
        }

        Log.d(TAG, "USSD response received: " + response);

        lastUSSDResponse = response;
        isUSSDActive.set(true);

        // Notify all listeners
        synchronized (responseListeners) {
            for (USSDResponseListener listener : responseListeners) {
                try {
                    listener.onUSSDResponse(response);
                } catch (Exception e) {
                    Log.e(TAG, "Error in response listener", e);
                }
            }
        }
    }

    /**
     * Registers a response listener
     *
     * @param listener Listener to register
     */
    public void addResponseListener(@NonNull USSDResponseListener listener) {
        if (listener == null) {
            return;
        }
        synchronized (responseListeners) {
            if (!responseListeners.contains(listener)) {
                responseListeners.add(listener);
            }
        }
    }

    /**
     * Removes a response listener
     *
     * @param listener Listener to remove
     */
    public void removeResponseListener(@NonNull USSDResponseListener listener) {
        if (listener == null) {
            return;
        }
        synchronized (responseListeners) {
            responseListeners.remove(listener);
        }
    }

    /**
     * Gets last received USSD response
     *
     * @return Last response or null if none
     */
    @Nullable
    public String getLastResponse() {
        return lastUSSDResponse;
    }

    // ========================================================================================
    // PERMISSION CHECKING
    // ========================================================================================

    /**
     * Checks if all required permissions are granted
     *
     * @return true if all permissions granted
     */
    public boolean checkPermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            return ContextCompat.checkSelfPermission(context, Manifest.permission.CALL_PHONE)
                == PackageManager.PERMISSION_GRANTED;
        }
        return true;
    }

    /**
     * Gets list of required permissions
     *
     * @return Array of permission strings
     */
    @NonNull
    public static String[] getRequiredPermissions() {
        return new String[]{
            Manifest.permission.CALL_PHONE,
            Manifest.permission.READ_PHONE_STATE
        };
    }

    // ========================================================================================
    // INTERNAL HELPERS
    // ========================================================================================

    /**
     * Sends USSD using default SIM
     */
    private boolean sendUSSDDefault(@NonNull String ussdCode) {
        try {
            String ussdUri = "tel:" + Uri.encode(ussdCode);
            android.content.Intent intent = new android.content.Intent(android.content.Intent.ACTION_CALL);
            intent.setData(Uri.parse(ussdUri));
            intent.setFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK);
            context.startActivity(intent);
            return true;
        } catch (Exception e) {
            Log.e(TAG, "Error sending USSD (default SIM)", e);
            return false;
        }
    }

    /**
     * Sends USSD using specific SIM slot (requires API 22+)
     */
    private boolean sendUSSDWithSIMSlot(@NonNull String ussdCode, int simSlot) {
        try {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP_MR1) {
                return sendUSSDDefault(ussdCode);
            }

            TelephonyManager telephonyManager = (TelephonyManager) context.getSystemService(Context.TELEPHONY_SERVICE);
            if (telephonyManager == null) {
                Log.e(TAG, "TelephonyManager not available");
                return false;
            }

            // Use reflection to access hidden APIs for dual SIM
            Method method = telephonyManager.getClass().getMethod(
                "sendUssdRequest",
                String.class,
                TelephonyManager.UssdResponseCallback.class,
                Handler.class
            );

            TelephonyManager.UssdResponseCallback callback = new TelephonyManager.UssdResponseCallback() {
                @Override
                public void onReceiveUssdResponse(TelephonyManager telephonyManager, String request, CharSequence response) {
                    onUSSDResponseReceived(response.toString());
                }

                @Override
                public void onReceiveUssdResponseFailed(TelephonyManager telephonyManager, String request, int failureCode) {
                    Log.e(TAG, "USSD request failed with code: " + failureCode);
                    isUSSDActive.set(false);
                }
            };

            method.invoke(telephonyManager, ussdCode, callback, mainHandler);
            return true;

        } catch (Exception e) {
            Log.e(TAG, "Error sending USSD (SIM slot " + simSlot + ")", e);
            return sendUSSDDefault(ussdCode);
        }
    }

    /**
     * Batch task wrapper
     */
    private static class BatchTask {
        final USSDSequence sequence;
        final long delayMs;

        BatchTask(USSDSequence sequence, long delayMs) {
            this.sequence = sequence;
            this.delayMs = delayMs;
        }
    }

    /**
     * Cleanup method - call when controller is no longer needed
     */
    public void cleanup() {
        // Cancel all active executors
        for (USSDSequenceExecutor executor : activeExecutors.values()) {
            try {
                executor.cancel();
                executor.shutdown();
            } catch (Exception e) {
                Log.e(TAG, "Error cleaning up executor", e);
            }
        }
        activeExecutors.clear();

        // Stop batch processing
        stopBatch();

        // Clear listeners
        synchronized (responseListeners) {
            responseListeners.clear();
        }

        isUSSDActive.set(false);
        lastUSSDResponse = null;
    }
}
