package com.jcussdlib.model;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Represents a complete USSD sequence with multiple steps.
 * <p>
 * A sequence defines:
 * - Initial USSD code to dial
 * - Ordered list of steps to execute
 * - Variable mappings for dynamic values
 * - Global timeout and retry configuration
 * - Success and failure conditions
 * </p>
 *
 * <p>Example usage:</p>
 * <pre>
 * USSDSequence sequence = new USSDSequence.Builder()
 *     .setName("Check Balance")
 *     .setInitialUSSDCode("*123#")
 *     .addStep(step1)
 *     .addStep(step2)
 *     .addStep(step3)
 *     .setGlobalTimeout(30000)
 *     .build();
 * </pre>
 *
 * <p>Thread Safety: Immutable once built - safe for concurrent use</p>
 *
 * @author Mugisha Jean Claude
 * @version 2.0.0
 * @since 2.0.0
 */
public class USSDSequence {

    private final String sequenceId;
    private final String name;
    private final String description;
    private final String initialUSSDCode;
    private final List<USSDStep> steps;
    private final Map<String, String> variables;
    private final long globalTimeoutMillis;
    private final int simSlot;
    private final boolean stopOnError;
    private final SequenceMetadata metadata;

    /**
     * Private constructor - use Builder
     */
    private USSDSequence(Builder builder) {
        this.sequenceId = builder.sequenceId != null ? builder.sequenceId : generateSequenceId();
        this.name = builder.name;
        this.description = builder.description;
        this.initialUSSDCode = builder.initialUSSDCode;
        this.steps = Collections.unmodifiableList(new ArrayList<>(builder.steps));
        this.variables = Collections.unmodifiableMap(new HashMap<>(builder.variables));
        this.globalTimeoutMillis = builder.globalTimeoutMillis;
        this.simSlot = builder.simSlot;
        this.stopOnError = builder.stopOnError;
        this.metadata = builder.metadata;
    }

    // Getters

    @NonNull
    public String getSequenceId() {
        return sequenceId;
    }

    @NonNull
    public String getName() {
        return name != null ? name : "Unnamed Sequence";
    }

    @Nullable
    public String getDescription() {
        return description;
    }

    @NonNull
    public String getInitialUSSDCode() {
        return initialUSSDCode;
    }

    @NonNull
    public List<USSDStep> getSteps() {
        return steps; // Already immutable
    }

    public int getStepCount() {
        return steps.size();
    }

    @Nullable
    public USSDStep getStep(int index) {
        if (index < 0 || index >= steps.size()) {
            return null;
        }
        return steps.get(index);
    }

    @NonNull
    public Map<String, String> getVariables() {
        return variables; // Already immutable
    }

    @Nullable
    public String getVariable(@NonNull String key) {
        return variables.get(key);
    }

    public long getGlobalTimeoutMillis() {
        return globalTimeoutMillis;
    }

    public int getSimSlot() {
        return simSlot;
    }

    public boolean shouldStopOnError() {
        return stopOnError;
    }

    @Nullable
    public SequenceMetadata getMetadata() {
        return metadata;
    }

    /**
     * Resolves variables in a response string
     * Replaces {{varName}} with actual value
     *
     * @param template String with variable placeholders
     * @return Resolved string with actual values
     */
    @NonNull
    public String resolveVariables(@NonNull String template) {
        String resolved = template;
        for (Map.Entry<String, String> entry : variables.entrySet()) {
            String placeholder = "{{" + entry.getKey() + "}}";
            resolved = resolved.replace(placeholder, entry.getValue());
        }
        return resolved;
    }

    /**
     * Validates the sequence configuration
     *
     * @return ValidationResult with any errors
     */
    @NonNull
    public ValidationResult validate() {
        List<String> errors = new ArrayList<>();

        if (initialUSSDCode == null || initialUSSDCode.trim().isEmpty()) {
            errors.add("Initial USSD code cannot be empty");
        }

        if (steps.isEmpty()) {
            errors.add("Sequence must have at least one step");
        }

        // Validate step numbers are sequential
        for (int i = 0; i < steps.size(); i++) {
            USSDStep step = steps.get(i);
            if (step.getStepNumber() != i + 1) {
                errors.add("Step " + (i + 1) + " has incorrect step number: " + step.getStepNumber());
            }
        }

        // Validate all required variables are provided
        for (USSDStep step : steps) {
            String varName = step.getVariableName();
            if (varName != null && !variables.containsKey(varName)) {
                errors.add("Step " + step.getStepNumber() + " requires variable '" + varName + "' but it's not provided");
            }
        }

        if (errors.isEmpty()) {
            return ValidationResult.success();
        } else {
            return ValidationResult.failure(String.join("; ", errors));
        }
    }

    @Override
    public String toString() {
        return "USSDSequence{" +
                "id='" + sequenceId + '\'' +
                ", name='" + name + '\'' +
                ", ussdCode='" + initialUSSDCode + '\'' +
                ", steps=" + steps.size() +
                ", timeout=" + globalTimeoutMillis + "ms" +
                '}';
    }

    /**
     * Generates a unique sequence ID
     */
    private static String generateSequenceId() {
        return "seq_" + System.currentTimeMillis() + "_" + (int) (Math.random() * 10000);
    }

    /**
     * Builder class for USSDSequence
     */
    public static class Builder {
        private String sequenceId;
        private String name;
        private String description;
        private String initialUSSDCode;
        private final List<USSDStep> steps = new ArrayList<>();
        private final Map<String, String> variables = new HashMap<>();
        private long globalTimeoutMillis = 30000; // Default 30 seconds
        private int simSlot = -1; // Default SIM
        private boolean stopOnError = true;
        private SequenceMetadata metadata;

        /**
         * Sets a custom sequence ID
         */
        public Builder setSequenceId(@NonNull String sequenceId) {
            this.sequenceId = sequenceId;
            return this;
        }

        /**
         * Sets the sequence name
         */
        public Builder setName(@NonNull String name) {
            this.name = name;
            return this;
        }

        /**
         * Sets the sequence description
         */
        public Builder setDescription(@NonNull String description) {
            this.description = description;
            return this;
        }

        /**
         * Sets the initial USSD code to dial (e.g., "*123#", "*348*1234#")
         */
        public Builder setInitialUSSDCode(@NonNull String ussdCode) {
            this.initialUSSDCode = ussdCode;
            return this;
        }

        /**
         * Adds a step to the sequence
         */
        public Builder addStep(@NonNull USSDStep step) {
            this.steps.add(step);
            return this;
        }

        /**
         * Adds multiple steps at once
         */
        public Builder addSteps(@NonNull List<USSDStep> steps) {
            this.steps.addAll(steps);
            return this;
        }

        /**
         * Sets a variable for the sequence
         * Used to replace {{varName}} placeholders in responses
         */
        public Builder setVariable(@NonNull String key, @NonNull String value) {
            this.variables.put(key, value);
            return this;
        }

        /**
         * Sets multiple variables at once
         */
        public Builder setVariables(@NonNull Map<String, String> variables) {
            this.variables.putAll(variables);
            return this;
        }

        /**
         * Sets the global timeout for the entire sequence
         * Individual steps can override this
         */
        public Builder setGlobalTimeout(long timeoutMillis) {
            if (timeoutMillis < 0) {
                throw new IllegalArgumentException("Global timeout cannot be negative");
            }
            this.globalTimeoutMillis = timeoutMillis;
            return this;
        }

        /**
         * Sets which SIM slot to use
         * -1 = default, 0 = SIM 1, 1 = SIM 2
         */
        public Builder setSimSlot(int simSlot) {
            if (simSlot < -1 || simSlot > 1) {
                throw new IllegalArgumentException("SIM slot must be -1, 0, or 1");
            }
            this.simSlot = simSlot;
            return this;
        }

        /**
         * Sets whether to stop execution on first error
         */
        public Builder setStopOnError(boolean stopOnError) {
            this.stopOnError = stopOnError;
            return this;
        }

        /**
         * Sets metadata for this sequence
         */
        public Builder setMetadata(@NonNull SequenceMetadata metadata) {
            this.metadata = metadata;
            return this;
        }

        /**
         * Builds the USSDSequence
         *
         * @throws IllegalStateException if configuration is invalid
         */
        @NonNull
        public USSDSequence build() {
            if (initialUSSDCode == null || initialUSSDCode.trim().isEmpty()) {
                throw new IllegalStateException("Initial USSD code must be set");
            }

            if (steps.isEmpty()) {
                throw new IllegalStateException("Sequence must have at least one step");
            }

            // Auto-number steps if not already numbered correctly
            for (int i = 0; i < steps.size(); i++) {
                USSDStep step = steps.get(i);
                if (step.getStepNumber() != i + 1) {
                    // Rebuild step with correct number
                    USSDStep.Builder stepBuilder = new USSDStep.Builder()
                            .setStepNumber(i + 1)
                            .setDescription(step.getDescription())
                            .setTimeout(step.getTimeoutMillis())
                            .setRetryPolicy(step.getRetryPolicy())
                            .requiresUserInput(step.requiresUserInput());

                    if (step.getExpectedPattern() != null) {
                        stepBuilder.setExpectedPattern(step.getExpectedPattern().pattern());
                    }
                    if (step.getResponseToSend() != null) {
                        stepBuilder.setResponseToSend(step.getResponseToSend());
                    }
                    if (step.getVariableName() != null) {
                        stepBuilder.setVariableName(step.getVariableName());
                    }
                    if (step.getValidator() != null) {
                        stepBuilder.setValidator(step.getValidator());
                    }
                    if (step.getExtractor() != null) {
                        stepBuilder.setExtractor(step.getExtractor());
                    }

                    steps.set(i, stepBuilder.build());
                }
            }

            return new USSDSequence(this);
        }
    }

    /**
     * Metadata for sequence tracking and analytics
     */
    public static class SequenceMetadata {
        private final String category;
        private final String version;
        private final Map<String, Object> customData;

        public SequenceMetadata(String category, String version, Map<String, Object> customData) {
            this.category = category;
            this.version = version;
            this.customData = customData != null ? new HashMap<>(customData) : new HashMap<>();
        }

        public String getCategory() {
            return category;
        }

        public String getVersion() {
            return version;
        }

        public Map<String, Object> getCustomData() {
            return Collections.unmodifiableMap(customData);
        }
    }

    /**
     * Result of sequence validation
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
