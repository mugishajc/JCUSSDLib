package com.jcussdlib.validation;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

/**
 * Framework for validating USSD responses before processing.
 * <p>
 * Provides:
 * - Pattern-based validation (regex)
 * - Length validation
 * - Content validation
 * - Phone number validation
 * - OTP format validation
 * - Custom validation logic
 * </p>
 *
 * <p>Thread-Safety: All validators are thread-safe and reusable.</p>
 *
 * <p>Error Handling: All validators use defensive programming with null checks,
 * regex safety, and comprehensive error messages.</p>
 *
 * @author Mugisha Jean Claude
 * @version 2.0.0
 * @since 2.0.0
 */
public abstract class ResponseValidator {

    /**
     * Validates a response
     *
     * @param response Response to validate (never null due to NonNull annotation)
     * @return ValidationResult indicating success or failure with error message
     */
    @NonNull
    public abstract ValidationResult validate(@NonNull String response);

    /**
     * Result of validation
     */
    public static class ValidationResult {
        private final boolean valid;
        private final String errorMessage;

        private ValidationResult(boolean valid, @Nullable String errorMessage) {
            this.valid = valid;
            this.errorMessage = errorMessage;
        }

        @NonNull
        public static ValidationResult success() {
            return new ValidationResult(true, null);
        }

        @NonNull
        public static ValidationResult failure(@NonNull String errorMessage) {
            if (errorMessage == null || errorMessage.trim().isEmpty()) {
                throw new IllegalArgumentException("Error message cannot be null or empty");
            }
            return new ValidationResult(false, errorMessage);
        }

        public boolean isValid() {
            return valid;
        }

        @Nullable
        public String getErrorMessage() {
            return errorMessage;
        }

        @Override
        public String toString() {
            return valid ? "Valid" : "Invalid: " + errorMessage;
        }
    }

    // ========================================================================================
    // BUILT-IN VALIDATORS
    // ========================================================================================

    /**
     * Validator that accepts any non-null response
     */
    public static class AcceptAll extends ResponseValidator {
        @NonNull
        @Override
        public ValidationResult validate(@NonNull String response) {
            // Defensive: Even though @NonNull, check for safety
            if (response == null) {
                return ValidationResult.failure("Response is null");
            }
            return ValidationResult.success();
        }
    }

    /**
     * Validator that checks if response matches a regex pattern
     */
    public static class PatternValidator extends ResponseValidator {
        private final Pattern pattern;
        private final String patternDescription;

        /**
         * Constructor with regex string
         *
         * @param regex Regex pattern
         * @throws IllegalArgumentException if regex is invalid
         */
        public PatternValidator(@NonNull String regex) {
            this(regex, "pattern: " + regex);
        }

        /**
         * Constructor with regex and description
         *
         * @param regex       Regex pattern
         * @param description Human-readable description
         * @throws IllegalArgumentException if regex is invalid
         */
        public PatternValidator(@NonNull String regex, @NonNull String description) {
            if (regex == null || regex.trim().isEmpty()) {
                throw new IllegalArgumentException("Regex pattern cannot be null or empty");
            }
            if (description == null) {
                description = "pattern: " + regex;
            }

            try {
                this.pattern = Pattern.compile(regex);
                this.patternDescription = description;
            } catch (PatternSyntaxException e) {
                throw new IllegalArgumentException("Invalid regex pattern: " + regex, e);
            }
        }

        /**
         * Constructor with pre-compiled Pattern
         *
         * @param pattern     Compiled pattern
         * @param description Description
         */
        public PatternValidator(@NonNull Pattern pattern, @NonNull String description) {
            if (pattern == null) {
                throw new IllegalArgumentException("Pattern cannot be null");
            }
            this.pattern = pattern;
            this.patternDescription = description != null ? description : "custom pattern";
        }

        @NonNull
        @Override
        public ValidationResult validate(@NonNull String response) {
            if (response == null) {
                return ValidationResult.failure("Response is null");
            }

            try {
                if (pattern.matcher(response).find()) {
                    return ValidationResult.success();
                } else {
                    return ValidationResult.failure("Response does not match " + patternDescription);
                }
            } catch (Exception e) {
                // Defensive: Catch any regex matching errors
                return ValidationResult.failure("Pattern matching error: " + e.getMessage());
            }
        }
    }

    /**
     * Validator that checks response length
     */
    public static class LengthValidator extends ResponseValidator {
        private final int minLength;
        private final int maxLength;

        /**
         * Constructor with min and max length
         *
         * @param minLength Minimum length (inclusive)
         * @param maxLength Maximum length (inclusive)
         */
        public LengthValidator(int minLength, int maxLength) {
            if (minLength < 0) {
                throw new IllegalArgumentException("Min length cannot be negative: " + minLength);
            }
            if (maxLength < minLength) {
                throw new IllegalArgumentException("Max length (" + maxLength +
                    ") cannot be less than min length (" + minLength + ")");
            }
            this.minLength = minLength;
            this.maxLength = maxLength;
        }

        @NonNull
        @Override
        public ValidationResult validate(@NonNull String response) {
            if (response == null) {
                return ValidationResult.failure("Response is null");
            }

            int length = response.length();
            if (length < minLength) {
                return ValidationResult.failure("Response too short: " + length +
                    " chars (minimum " + minLength + ")");
            }
            if (length > maxLength) {
                return ValidationResult.failure("Response too long: " + length +
                    " chars (maximum " + maxLength + ")");
            }
            return ValidationResult.success();
        }
    }

    /**
     * Validator for phone numbers (East African format)
     * Supports: 07XXXXXXXX, 2507XXXXXXXX, +2507XXXXXXXX
     */
    public static class PhoneNumberValidator extends ResponseValidator {
        private static final Pattern PHONE_PATTERN = Pattern.compile(
            "^(\\+?250|0)?[7][0-9]{8}$"
        );

        @NonNull
        @Override
        public ValidationResult validate(@NonNull String response) {
            if (response == null) {
                return ValidationResult.failure("Phone number is null");
            }

            // Remove spaces and dashes for validation
            String cleaned = response.replaceAll("[\\s-]", "");

            if (cleaned.isEmpty()) {
                return ValidationResult.failure("Phone number is empty");
            }

            try {
                if (PHONE_PATTERN.matcher(cleaned).matches()) {
                    return ValidationResult.success();
                } else {
                    return ValidationResult.failure("Invalid phone number format: " + response);
                }
            } catch (Exception e) {
                return ValidationResult.failure("Phone validation error: " + e.getMessage());
            }
        }
    }

    /**
     * Validator for OTP codes
     * Supports 4-8 digit OTP codes
     */
    public static class OTPValidator extends ResponseValidator {
        private final int minDigits;
        private final int maxDigits;
        private static final Pattern OTP_PATTERN_TEMPLATE = Pattern.compile("\\d+");

        /**
         * Default constructor (4-8 digits)
         */
        public OTPValidator() {
            this(4, 8);
        }

        /**
         * Constructor with custom digit range
         *
         * @param minDigits Minimum digits
         * @param maxDigits Maximum digits
         */
        public OTPValidator(int minDigits, int maxDigits) {
            if (minDigits < 1) {
                throw new IllegalArgumentException("Min digits must be at least 1");
            }
            if (maxDigits < minDigits) {
                throw new IllegalArgumentException("Max digits cannot be less than min digits");
            }
            if (maxDigits > 20) {
                throw new IllegalArgumentException("Max digits cannot exceed 20 (unreasonable OTP length)");
            }
            this.minDigits = minDigits;
            this.maxDigits = maxDigits;
        }

        @NonNull
        @Override
        public ValidationResult validate(@NonNull String response) {
            if (response == null) {
                return ValidationResult.failure("OTP is null");
            }

            // Extract only digits
            String digitsOnly = response.replaceAll("\\D", "");

            if (digitsOnly.isEmpty()) {
                return ValidationResult.failure("OTP contains no digits");
            }

            int length = digitsOnly.length();
            if (length < minDigits) {
                return ValidationResult.failure("OTP too short: " + length +
                    " digits (minimum " + minDigits + ")");
            }
            if (length > maxDigits) {
                return ValidationResult.failure("OTP too long: " + length +
                    " digits (maximum " + maxDigits + ")");
            }

            return ValidationResult.success();
        }
    }

    /**
     * Validator that requires response to contain specific keywords
     */
    public static class ContainsKeywordValidator extends ResponseValidator {
        private final String[] keywords;
        private final boolean caseSensitive;
        private final boolean requireAll; // true = all keywords, false = any keyword

        /**
         * Constructor (case-insensitive, any keyword)
         *
         * @param keywords Keywords to check for
         */
        public ContainsKeywordValidator(@NonNull String... keywords) {
            this(false, false, keywords);
        }

        /**
         * Full constructor
         *
         * @param caseSensitive Case-sensitive matching
         * @param requireAll    Require all keywords (true) or any (false)
         * @param keywords      Keywords to check for
         */
        public ContainsKeywordValidator(boolean caseSensitive, boolean requireAll, @NonNull String... keywords) {
            if (keywords == null || keywords.length == 0) {
                throw new IllegalArgumentException("At least one keyword must be provided");
            }
            for (String keyword : keywords) {
                if (keyword == null || keyword.trim().isEmpty()) {
                    throw new IllegalArgumentException("Keywords cannot be null or empty");
                }
            }
            this.keywords = keywords;
            this.caseSensitive = caseSensitive;
            this.requireAll = requireAll;
        }

        @NonNull
        @Override
        public ValidationResult validate(@NonNull String response) {
            if (response == null) {
                return ValidationResult.failure("Response is null");
            }

            String checkResponse = caseSensitive ? response : response.toLowerCase();

            if (requireAll) {
                // All keywords must be present
                for (String keyword : keywords) {
                    String checkKeyword = caseSensitive ? keyword : keyword.toLowerCase();
                    if (!checkResponse.contains(checkKeyword)) {
                        return ValidationResult.failure("Missing required keyword: " + keyword);
                    }
                }
                return ValidationResult.success();
            } else {
                // Any keyword must be present
                for (String keyword : keywords) {
                    String checkKeyword = caseSensitive ? keyword : keyword.toLowerCase();
                    if (checkResponse.contains(checkKeyword)) {
                        return ValidationResult.success();
                    }
                }
                return ValidationResult.failure("Response does not contain any of the required keywords");
            }
        }
    }

    /**
     * Validator that combines multiple validators with AND logic
     */
    public static class CompositeAndValidator extends ResponseValidator {
        private final ResponseValidator[] validators;

        public CompositeAndValidator(@NonNull ResponseValidator... validators) {
            if (validators == null || validators.length == 0) {
                throw new IllegalArgumentException("At least one validator must be provided");
            }
            for (ResponseValidator validator : validators) {
                if (validator == null) {
                    throw new IllegalArgumentException("Validators cannot be null");
                }
            }
            this.validators = validators;
        }

        @NonNull
        @Override
        public ValidationResult validate(@NonNull String response) {
            if (response == null) {
                return ValidationResult.failure("Response is null");
            }

            try {
                for (ResponseValidator validator : validators) {
                    ValidationResult result = validator.validate(response);
                    if (!result.isValid()) {
                        return result; // First failure stops validation
                    }
                }
                return ValidationResult.success();
            } catch (Exception e) {
                return ValidationResult.failure("Composite validation error: " + e.getMessage());
            }
        }
    }

    /**
     * Validator that combines multiple validators with OR logic
     */
    public static class CompositeOrValidator extends ResponseValidator {
        private final ResponseValidator[] validators;

        public CompositeOrValidator(@NonNull ResponseValidator... validators) {
            if (validators == null || validators.length == 0) {
                throw new IllegalArgumentException("At least one validator must be provided");
            }
            for (ResponseValidator validator : validators) {
                if (validator == null) {
                    throw new IllegalArgumentException("Validators cannot be null");
                }
            }
            this.validators = validators;
        }

        @NonNull
        @Override
        public ValidationResult validate(@NonNull String response) {
            if (response == null) {
                return ValidationResult.failure("Response is null");
            }

            StringBuilder errors = new StringBuilder();
            try {
                for (int i = 0; i < validators.length; i++) {
                    ValidationResult result = validators[i].validate(response);
                    if (result.isValid()) {
                        return ValidationResult.success(); // First success stops validation
                    }
                    if (i > 0) {
                        errors.append("; ");
                    }
                    errors.append(result.getErrorMessage());
                }
                return ValidationResult.failure("All validators failed: " + errors.toString());
            } catch (Exception e) {
                return ValidationResult.failure("Composite validation error: " + e.getMessage());
            }
        }
    }

    /**
     * Validator that inverts another validator's result
     */
    public static class NotValidator extends ResponseValidator {
        private final ResponseValidator validator;

        public NotValidator(@NonNull ResponseValidator validator) {
            if (validator == null) {
                throw new IllegalArgumentException("Validator cannot be null");
            }
            this.validator = validator;
        }

        @NonNull
        @Override
        public ValidationResult validate(@NonNull String response) {
            if (response == null) {
                return ValidationResult.failure("Response is null");
            }

            try {
                ValidationResult result = validator.validate(response);
                if (result.isValid()) {
                    return ValidationResult.failure("Validation succeeded but should have failed");
                } else {
                    return ValidationResult.success();
                }
            } catch (Exception e) {
                return ValidationResult.failure("NOT validation error: " + e.getMessage());
            }
        }
    }
}
