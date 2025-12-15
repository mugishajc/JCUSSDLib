package com.jcussdlib.model;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.regex.Pattern;

/**
 * Represents a single step in a USSD sequence.
 * <p>
 * Each step defines:
 * - Expected response pattern (regex)
 * - Response to send
 * - Timeout configuration
 * - Retry policy
 * - Validation rules
 * - Data extraction rules
 * </p>
 *
 * <p>Example usage:</p>
 * <pre>
 * USSDStep step = new USSDStep.Builder()
 *     .setStepNumber(1)
 *     .setDescription("Select service option")
 *     .setExpectedPattern("(?i)select.*option")
 *     .setResponseToSend("1")
 *     .setTimeout(10000)
 *     .setRetryPolicy(RetryPolicy.RETRY_3_TIMES)
 *     .build();
 * </pre>
 *
 * @author Mugisha Jean Claude
 * @version 2.0.0
 * @since 2.0.0
 */
public class USSDStep {

    private final int stepNumber;
    private final String description;
    private final Pattern expectedPattern;
    private final String responseToSend;
    private final long timeoutMillis;
    private final RetryPolicy retryPolicy;
    private final boolean requiresUserInput;
    private final String variableName;
    private final ResponseValidator validator;
    private final DataExtractor extractor;
    private final ErrorHandler errorHandler;

    /**
     * Private constructor - use Builder to create instances
     */
    private USSDStep(Builder builder) {
        this.stepNumber = builder.stepNumber;
        this.description = builder.description;
        this.expectedPattern = builder.expectedPattern;
        this.responseToSend = builder.responseToSend;
        this.timeoutMillis = builder.timeoutMillis;
        this.retryPolicy = builder.retryPolicy;
        this.requiresUserInput = builder.requiresUserInput;
        this.variableName = builder.variableName;
        this.validator = builder.validator;
        this.extractor = builder.extractor;
        this.errorHandler = builder.errorHandler;
    }

    // Getters

    public int getStepNumber() {
        return stepNumber;
    }

    @NonNull
    public String getDescription() {
        return description != null ? description : "Step " + stepNumber;
    }

    @Nullable
    public Pattern getExpectedPattern() {
        return expectedPattern;
    }

    @Nullable
    public String getResponseToSend() {
        return responseToSend;
    }

    public long getTimeoutMillis() {
        return timeoutMillis;
    }

    @NonNull
    public RetryPolicy getRetryPolicy() {
        return retryPolicy;
    }

    public boolean requiresUserInput() {
        return requiresUserInput;
    }

    @Nullable
    public String getVariableName() {
        return variableName;
    }

    @Nullable
    public ResponseValidator getValidator() {
        return validator;
    }

    @Nullable
    public DataExtractor getExtractor() {
        return extractor;
    }

    @Nullable
    public ErrorHandler getErrorHandler() {
        return errorHandler;
    }

    /**
     * Checks if the given response matches this step's expected pattern
     *
     * @param response The USSD response to check
     * @return true if response matches expected pattern, false otherwise
     */
    public boolean matchesExpectedPattern(@NonNull String response) {
        if (expectedPattern == null) {
            return true; // No pattern means accept any response
        }
        return expectedPattern.matcher(response).find();
    }

    /**
     * Validates the response using the configured validator
     *
     * @param response The response to validate
     * @return ValidationResult containing success status and error message if any
     */
    @NonNull
    public ValidationResult validate(@NonNull String response) {
        if (validator == null) {
            return ValidationResult.success();
        }
        return validator.validate(response);
    }

    @Override
    public String toString() {
        return "USSDStep{" +
                "stepNumber=" + stepNumber +
                ", description='" + description + '\'' +
                ", timeout=" + timeoutMillis + "ms" +
                ", retryPolicy=" + retryPolicy +
                '}';
    }

    /**
     * Builder class for creating USSDStep instances
     */
    public static class Builder {
        private int stepNumber;
        private String description;
        private Pattern expectedPattern;
        private String responseToSend;
        private long timeoutMillis = 10000; // Default 10 seconds
        private RetryPolicy retryPolicy = RetryPolicy.NO_RETRY;
        private boolean requiresUserInput = false;
        private String variableName;
        private ResponseValidator validator;
        private DataExtractor extractor;
        private ErrorHandler errorHandler;

        /**
         * Sets the step number (1-indexed)
         */
        public Builder setStepNumber(int stepNumber) {
            if (stepNumber < 1) {
                throw new IllegalArgumentException("Step number must be >= 1");
            }
            this.stepNumber = stepNumber;
            return this;
        }

        /**
         * Sets a human-readable description of this step
         */
        public Builder setDescription(@NonNull String description) {
            this.description = description;
            return this;
        }

        /**
         * Sets the regex pattern to match against USSD response
         */
        public Builder setExpectedPattern(@NonNull String regex) {
            this.expectedPattern = Pattern.compile(regex);
            return this;
        }

        /**
         * Sets the regex pattern with flags
         */
        public Builder setExpectedPattern(@NonNull String regex, int flags) {
            this.expectedPattern = Pattern.compile(regex, flags);
            return this;
        }

        /**
         * Sets the response to send for this step
         * Can include variable placeholders like {{phone}}
         */
        public Builder setResponseToSend(@NonNull String response) {
            this.responseToSend = response;
            return this;
        }

        /**
         * Sets the timeout for this step in milliseconds
         */
        public Builder setTimeout(long timeoutMillis) {
            if (timeoutMillis < 0) {
                throw new IllegalArgumentException("Timeout cannot be negative");
            }
            this.timeoutMillis = timeoutMillis;
            return this;
        }

        /**
         * Sets the retry policy for this step
         */
        public Builder setRetryPolicy(@NonNull RetryPolicy retryPolicy) {
            this.retryPolicy = retryPolicy;
            return this;
        }

        /**
         * Marks this step as requiring user input
         * (e.g., dynamic phone number, OTP, etc.)
         */
        public Builder requiresUserInput(boolean requires) {
            this.requiresUserInput = requires;
            return this;
        }

        /**
         * Sets the variable name for dynamic input
         * e.g., "phone", "otp", "amount"
         */
        public Builder setVariableName(@NonNull String variableName) {
            this.variableName = variableName;
            this.requiresUserInput = true;
            return this;
        }

        /**
         * Sets a custom validator for response validation
         */
        public Builder setValidator(@NonNull ResponseValidator validator) {
            this.validator = validator;
            return this;
        }

        /**
         * Sets a data extractor to extract information from response
         * (e.g., OTP code, balance, transaction ID)
         */
        public Builder setExtractor(@NonNull DataExtractor extractor) {
            this.extractor = extractor;
            return this;
        }

        /**
         * Sets a custom error handler for this step
         */
        public Builder setErrorHandler(@NonNull ErrorHandler errorHandler) {
            this.errorHandler = errorHandler;
            return this;
        }

        /**
         * Builds the USSDStep instance
         */
        @NonNull
        public USSDStep build() {
            if (stepNumber < 1) {
                throw new IllegalStateException("Step number must be set and >= 1");
            }
            return new USSDStep(this);
        }
    }

    /**
     * Retry policy for failed steps
     */
    public enum RetryPolicy {
        NO_RETRY(0),
        RETRY_ONCE(1),
        RETRY_TWICE(2),
        RETRY_3_TIMES(3),
        RETRY_5_TIMES(5);

        private final int maxRetries;

        RetryPolicy(int maxRetries) {
            this.maxRetries = maxRetries;
        }

        public int getMaxRetries() {
            return maxRetries;
        }
    }

    /**
     * Interface for response validation
     */
    public interface ResponseValidator {
        @NonNull
        ValidationResult validate(@NonNull String response);
    }

    /**
     * Interface for data extraction from responses
     */
    public interface DataExtractor {
        @Nullable
        String extract(@NonNull String response);
    }

    /**
     * Interface for custom error handling
     */
    public interface ErrorHandler {
        void onError(@NonNull USSDStep step, @NonNull String error, int attemptNumber);
    }

    /**
     * Result of validation
     */
    public static class ValidationResult {
        private final boolean isValid;
        private final String errorMessage;

        private ValidationResult(boolean isValid, String errorMessage) {
            this.isValid = isValid;
            this.errorMessage = errorMessage;
        }

        public static ValidationResult success() {
            return new ValidationResult(true, null);
        }

        public static ValidationResult failure(@NonNull String errorMessage) {
            return new ValidationResult(false, errorMessage);
        }

        public boolean isValid() {
            return isValid;
        }

        @Nullable
        public String getErrorMessage() {
            return errorMessage;
        }
    }
}
