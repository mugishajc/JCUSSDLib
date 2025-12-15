package com.jcussdlib.extraction;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

/**
 * Framework for extracting structured data from USSD responses.
 * <p>
 * Provides:
 * - Pattern-based extraction (regex with capture groups)
 * - OTP code extraction (4-8 digits)
 * - Phone number extraction
 * - Balance/amount extraction (with currency support)
 * - Transaction ID extraction
 * - Multi-value extraction
 * - Custom extraction logic
 * </p>
 *
 * <p>Thread-Safety: All extractors are thread-safe and reusable.</p>
 *
 * <p>Error Handling: All extractors use defensive programming with null checks,
 * regex safety, and comprehensive error messages. Never throws exceptions.</p>
 *
 * <p>Example usage:</p>
 * <pre>
 * // Extract OTP
 * ResponseExtractor otpExtractor = new ResponseExtractor.OTPExtractor();
 * ExtractionResult result = otpExtractor.extract("Your OTP is 123456. Valid for 5 minutes.");
 * if (result.isSuccess()) {
 *     String otp = result.getValue(); // "123456"
 * }
 *
 * // Extract balance
 * ResponseExtractor balanceExtractor = new ResponseExtractor.BalanceExtractor();
 * ExtractionResult result = balanceExtractor.extract("Your balance is RWF 15,000.50");
 * if (result.isSuccess()) {
 *     String balance = result.getValue(); // "15000.50"
 *     String currency = result.getMetadata("currency"); // "RWF"
 * }
 * </pre>
 *
 * @author Mugisha Jean Claude
 * @version 2.0.0
 * @since 2.0.0
 */
public abstract class ResponseExtractor {

    /**
     * Extracts data from a USSD response
     *
     * @param response Response to extract data from (never null due to NonNull annotation)
     * @return ExtractionResult containing extracted value or error
     */
    @NonNull
    public abstract ExtractionResult extract(@NonNull String response);

    /**
     * Result of data extraction
     */
    public static class ExtractionResult {
        private final boolean success;
        private final String value;
        private final String errorMessage;
        private final Map<String, String> metadata;

        private ExtractionResult(boolean success, @Nullable String value,
                                @Nullable String errorMessage,
                                @Nullable Map<String, String> metadata) {
            this.success = success;
            this.value = value;
            this.errorMessage = errorMessage;
            this.metadata = metadata != null ? new HashMap<>(metadata) : new HashMap<>();
        }

        @NonNull
        public static ExtractionResult success(@NonNull String value) {
            if (value == null) {
                throw new IllegalArgumentException("Extracted value cannot be null");
            }
            return new ExtractionResult(true, value, null, null);
        }

        @NonNull
        public static ExtractionResult success(@NonNull String value, @Nullable Map<String, String> metadata) {
            if (value == null) {
                throw new IllegalArgumentException("Extracted value cannot be null");
            }
            return new ExtractionResult(true, value, null, metadata);
        }

        @NonNull
        public static ExtractionResult failure(@NonNull String errorMessage) {
            if (errorMessage == null || errorMessage.trim().isEmpty()) {
                throw new IllegalArgumentException("Error message cannot be null or empty");
            }
            return new ExtractionResult(false, null, errorMessage, null);
        }

        public boolean isSuccess() {
            return success;
        }

        @Nullable
        public String getValue() {
            return value;
        }

        @Nullable
        public String getErrorMessage() {
            return errorMessage;
        }

        @NonNull
        public Map<String, String> getMetadata() {
            return Collections.unmodifiableMap(metadata);
        }

        @Nullable
        public String getMetadata(@NonNull String key) {
            return metadata.get(key);
        }

        @Override
        public String toString() {
            return success ? "Extracted: " + value : "Failed: " + errorMessage;
        }
    }

    // ========================================================================================
    // BUILT-IN EXTRACTORS
    // ========================================================================================

    /**
     * Extractor that returns the entire response as-is
     */
    public static class FullResponseExtractor extends ResponseExtractor {
        @NonNull
        @Override
        public ExtractionResult extract(@NonNull String response) {
            // Defensive: Even though @NonNull, check for safety
            if (response == null) {
                return ExtractionResult.failure("Response is null");
            }
            return ExtractionResult.success(response);
        }
    }

    /**
     * Pattern-based extractor using regex with capture groups
     */
    public static class PatternExtractor extends ResponseExtractor {
        private final Pattern pattern;
        private final int captureGroup;
        private final String patternDescription;

        /**
         * Constructor with regex string (uses first capture group)
         *
         * @param regex Regex pattern with at least one capture group
         * @throws IllegalArgumentException if regex is invalid
         */
        public PatternExtractor(@NonNull String regex) {
            this(regex, 1, "pattern: " + regex);
        }

        /**
         * Constructor with regex and capture group index
         *
         * @param regex        Regex pattern
         * @param captureGroup Capture group index (1-based)
         * @throws IllegalArgumentException if regex is invalid or capture group < 1
         */
        public PatternExtractor(@NonNull String regex, int captureGroup) {
            this(regex, captureGroup, "pattern: " + regex);
        }

        /**
         * Constructor with regex, capture group, and description
         *
         * @param regex        Regex pattern
         * @param captureGroup Capture group index (1-based)
         * @param description  Human-readable description
         * @throws IllegalArgumentException if parameters are invalid
         */
        public PatternExtractor(@NonNull String regex, int captureGroup, @NonNull String description) {
            if (regex == null || regex.trim().isEmpty()) {
                throw new IllegalArgumentException("Regex pattern cannot be null or empty");
            }
            if (captureGroup < 1) {
                throw new IllegalArgumentException("Capture group must be >= 1 (1-based indexing)");
            }
            if (description == null) {
                description = "pattern: " + regex;
            }

            try {
                this.pattern = Pattern.compile(regex);
                this.captureGroup = captureGroup;
                this.patternDescription = description;
            } catch (PatternSyntaxException e) {
                throw new IllegalArgumentException("Invalid regex pattern: " + regex, e);
            }
        }

        /**
         * Constructor with pre-compiled Pattern
         *
         * @param pattern      Compiled pattern
         * @param captureGroup Capture group index
         * @param description  Description
         */
        public PatternExtractor(@NonNull Pattern pattern, int captureGroup, @NonNull String description) {
            if (pattern == null) {
                throw new IllegalArgumentException("Pattern cannot be null");
            }
            if (captureGroup < 1) {
                throw new IllegalArgumentException("Capture group must be >= 1");
            }
            this.pattern = pattern;
            this.captureGroup = captureGroup;
            this.patternDescription = description != null ? description : "custom pattern";
        }

        @NonNull
        @Override
        public ExtractionResult extract(@NonNull String response) {
            if (response == null) {
                return ExtractionResult.failure("Response is null");
            }

            try {
                Matcher matcher = pattern.matcher(response);
                if (matcher.find()) {
                    // Check if capture group exists
                    if (matcher.groupCount() < captureGroup) {
                        return ExtractionResult.failure("Capture group " + captureGroup +
                            " not found (pattern has " + matcher.groupCount() + " groups)");
                    }

                    String extracted = matcher.group(captureGroup);
                    if (extracted == null) {
                        return ExtractionResult.failure("Capture group " + captureGroup + " matched null");
                    }

                    return ExtractionResult.success(extracted.trim());
                } else {
                    return ExtractionResult.failure("Pattern not found: " + patternDescription);
                }
            } catch (Exception e) {
                // Defensive: Catch any regex extraction errors
                return ExtractionResult.failure("Extraction error: " + e.getMessage());
            }
        }
    }

    /**
     * Extractor for OTP codes (4-8 digits)
     * Supports common OTP formats in USSD responses
     */
    public static class OTPExtractor extends ResponseExtractor {
        private final int minDigits;
        private final int maxDigits;
        private final Pattern otpPattern;

        /**
         * Default constructor (4-8 digits)
         */
        public OTPExtractor() {
            this(4, 8);
        }

        /**
         * Constructor with custom digit range
         *
         * @param minDigits Minimum digits
         * @param maxDigits Maximum digits
         */
        public OTPExtractor(int minDigits, int maxDigits) {
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

            // Pattern matches digits with optional separators
            // Examples: "123456", "12 34 56", "12-34-56", "OTP: 123456"
            String patternStr = "\\b(\\d{" + minDigits + "," + maxDigits + "})\\b";
            this.otpPattern = Pattern.compile(patternStr);
        }

        @NonNull
        @Override
        public ExtractionResult extract(@NonNull String response) {
            if (response == null) {
                return ExtractionResult.failure("Response is null");
            }

            try {
                // First try: Look for explicit OTP keywords
                Pattern explicitPattern = Pattern.compile(
                    "(?i)(otp|code|pin|verification code)[:\\s]+([\\d\\s-]{" + minDigits + "," + (maxDigits + maxDigits/2) + "})",
                    Pattern.CASE_INSENSITIVE
                );

                Matcher explicitMatcher = explicitPattern.matcher(response);
                if (explicitMatcher.find()) {
                    String rawOtp = explicitMatcher.group(2);
                    String cleanedOtp = rawOtp.replaceAll("[\\s-]", "");

                    if (cleanedOtp.length() >= minDigits && cleanedOtp.length() <= maxDigits) {
                        Map<String, String> metadata = new HashMap<>();
                        metadata.put("extraction_method", "explicit_keyword");
                        return ExtractionResult.success(cleanedOtp, metadata);
                    }
                }

                // Second try: Look for any digit sequence in valid range
                Matcher matcher = otpPattern.matcher(response);
                if (matcher.find()) {
                    String otp = matcher.group(1);
                    Map<String, String> metadata = new HashMap<>();
                    metadata.put("extraction_method", "pattern_match");
                    return ExtractionResult.success(otp, metadata);
                }

                return ExtractionResult.failure("No OTP code found (" + minDigits + "-" +
                    maxDigits + " digits)");
            } catch (Exception e) {
                return ExtractionResult.failure("OTP extraction error: " + e.getMessage());
            }
        }
    }

    /**
     * Extractor for phone numbers (East African format)
     * Supports: 07XXXXXXXX, 2507XXXXXXXX, +2507XXXXXXXX
     */
    public static class PhoneNumberExtractor extends ResponseExtractor {
        private static final Pattern PHONE_PATTERN = Pattern.compile(
            "(\\+?250|0)?([7][0-9]{8})"
        );

        @NonNull
        @Override
        public ExtractionResult extract(@NonNull String response) {
            if (response == null) {
                return ExtractionResult.failure("Response is null");
            }

            try {
                // Remove common separators for matching
                String cleaned = response.replaceAll("[\\s()-]", "");

                Matcher matcher = PHONE_PATTERN.matcher(cleaned);
                if (matcher.find()) {
                    String prefix = matcher.group(1);
                    String number = matcher.group(2);

                    // Normalize to full international format
                    String normalized = "250" + number;

                    Map<String, String> metadata = new HashMap<>();
                    metadata.put("original_format", matcher.group(0));
                    metadata.put("had_prefix", prefix != null ? "yes" : "no");

                    return ExtractionResult.success(normalized, metadata);
                } else {
                    return ExtractionResult.failure("No valid phone number found");
                }
            } catch (Exception e) {
                return ExtractionResult.failure("Phone extraction error: " + e.getMessage());
            }
        }
    }

    /**
     * Extractor for balance/amount values
     * Supports various currency formats: RWF 1,000.50, $100.00, 1000.50 RWF
     */
    public static class BalanceExtractor extends ResponseExtractor {
        private static final Pattern BALANCE_PATTERN = Pattern.compile(
            "(?i)(balance|amount|total)[:\\s]*((?:[A-Z]{3}|[\\$€£]))?[\\s]*([-+]?[\\d,]+(?:\\.\\d{1,2})?)[\\s]*((?:[A-Z]{3}|[\\$€£]))?",
            Pattern.CASE_INSENSITIVE
        );

        // Fallback: any number with currency
        private static final Pattern AMOUNT_PATTERN = Pattern.compile(
            "((?:[A-Z]{3}|[\\$€£]))?[\\s]*([-+]?[\\d,]+(?:\\.\\d{1,2})?)[\\s]*((?:[A-Z]{3}|[\\$€£]))?"
        );

        @NonNull
        @Override
        public ExtractionResult extract(@NonNull String response) {
            if (response == null) {
                return ExtractionResult.failure("Response is null");
            }

            try {
                // First try: Look for explicit balance keywords
                Matcher explicitMatcher = BALANCE_PATTERN.matcher(response);
                if (explicitMatcher.find()) {
                    String currencyBefore = explicitMatcher.group(2);
                    String amount = explicitMatcher.group(3);
                    String currencyAfter = explicitMatcher.group(4);

                    String cleanedAmount = amount.replaceAll(",", "");
                    String currency = currencyBefore != null ? currencyBefore :
                                    (currencyAfter != null ? currencyAfter : "");

                    Map<String, String> metadata = new HashMap<>();
                    metadata.put("currency", currency.trim());
                    metadata.put("raw_amount", amount);
                    metadata.put("extraction_method", "explicit_keyword");

                    return ExtractionResult.success(cleanedAmount, metadata);
                }

                // Second try: Look for any amount with currency
                Matcher amountMatcher = AMOUNT_PATTERN.matcher(response);
                if (amountMatcher.find()) {
                    String currencyBefore = amountMatcher.group(1);
                    String amount = amountMatcher.group(2);
                    String currencyAfter = amountMatcher.group(3);

                    String cleanedAmount = amount.replaceAll(",", "");
                    String currency = currencyBefore != null ? currencyBefore :
                                    (currencyAfter != null ? currencyAfter : "");

                    Map<String, String> metadata = new HashMap<>();
                    metadata.put("currency", currency.trim());
                    metadata.put("raw_amount", amount);
                    metadata.put("extraction_method", "pattern_match");

                    return ExtractionResult.success(cleanedAmount, metadata);
                }

                return ExtractionResult.failure("No balance/amount found");
            } catch (Exception e) {
                return ExtractionResult.failure("Balance extraction error: " + e.getMessage());
            }
        }
    }

    /**
     * Extractor for transaction IDs
     * Supports alphanumeric IDs with common formats
     */
    public static class TransactionIdExtractor extends ResponseExtractor {
        private static final Pattern TRANSACTION_PATTERN = Pattern.compile(
            "(?i)(transaction|txn|ref(?:erence)?|id)[:\\s#]?\\s*([A-Z0-9]{6,20})",
            Pattern.CASE_INSENSITIVE
        );

        @NonNull
        @Override
        public ExtractionResult extract(@NonNull String response) {
            if (response == null) {
                return ExtractionResult.failure("Response is null");
            }

            try {
                Matcher matcher = TRANSACTION_PATTERN.matcher(response);
                if (matcher.find()) {
                    String transactionId = matcher.group(2);

                    Map<String, String> metadata = new HashMap<>();
                    metadata.put("id_type", matcher.group(1).toLowerCase());
                    metadata.put("id_length", String.valueOf(transactionId.length()));

                    return ExtractionResult.success(transactionId, metadata);
                } else {
                    return ExtractionResult.failure("No transaction ID found");
                }
            } catch (Exception e) {
                return ExtractionResult.failure("Transaction ID extraction error: " + e.getMessage());
            }
        }
    }

    /**
     * Extractor that tries multiple extractors in sequence
     * Returns first successful extraction
     */
    public static class ChainedExtractor extends ResponseExtractor {
        private final ResponseExtractor[] extractors;

        public ChainedExtractor(@NonNull ResponseExtractor... extractors) {
            if (extractors == null || extractors.length == 0) {
                throw new IllegalArgumentException("At least one extractor must be provided");
            }
            for (ResponseExtractor extractor : extractors) {
                if (extractor == null) {
                    throw new IllegalArgumentException("Extractors cannot be null");
                }
            }
            this.extractors = extractors;
        }

        @NonNull
        @Override
        public ExtractionResult extract(@NonNull String response) {
            if (response == null) {
                return ExtractionResult.failure("Response is null");
            }

            StringBuilder errors = new StringBuilder();
            try {
                for (int i = 0; i < extractors.length; i++) {
                    ExtractionResult result = extractors[i].extract(response);
                    if (result.isSuccess()) {
                        return result; // First success
                    }
                    if (i > 0) {
                        errors.append("; ");
                    }
                    errors.append(result.getErrorMessage());
                }
                return ExtractionResult.failure("All extractors failed: " + errors.toString());
            } catch (Exception e) {
                return ExtractionResult.failure("Chained extraction error: " + e.getMessage());
            }
        }
    }

    /**
     * Extractor that extracts multiple values using different extractors
     * Returns all extracted values in metadata
     */
    public static class MultiValueExtractor extends ResponseExtractor {
        private final Map<String, ResponseExtractor> extractorMap;

        /**
         * Constructor with named extractors
         *
         * @param extractorMap Map of (key -> extractor) pairs
         */
        public MultiValueExtractor(@NonNull Map<String, ResponseExtractor> extractorMap) {
            if (extractorMap == null || extractorMap.isEmpty()) {
                throw new IllegalArgumentException("At least one extractor must be provided");
            }
            for (Map.Entry<String, ResponseExtractor> entry : extractorMap.entrySet()) {
                if (entry.getKey() == null || entry.getValue() == null) {
                    throw new IllegalArgumentException("Extractor keys and values cannot be null");
                }
            }
            this.extractorMap = new HashMap<>(extractorMap);
        }

        @NonNull
        @Override
        public ExtractionResult extract(@NonNull String response) {
            if (response == null) {
                return ExtractionResult.failure("Response is null");
            }

            try {
                Map<String, String> allExtracted = new HashMap<>();
                List<String> failures = new ArrayList<>();
                int successCount = 0;

                for (Map.Entry<String, ResponseExtractor> entry : extractorMap.entrySet()) {
                    String key = entry.getKey();
                    ResponseExtractor extractor = entry.getValue();

                    ExtractionResult result = extractor.extract(response);
                    if (result.isSuccess()) {
                        allExtracted.put(key, result.getValue());
                        successCount++;
                    } else {
                        failures.add(key + ": " + result.getErrorMessage());
                    }
                }

                if (successCount == 0) {
                    return ExtractionResult.failure("All extractions failed: " +
                        String.join("; ", failures));
                }

                // Return first extracted value as primary, rest in metadata
                String primaryKey = extractorMap.keySet().iterator().next();
                String primaryValue = allExtracted.get(primaryKey);

                if (primaryValue == null) {
                    // Primary extraction failed, use any successful one
                    Map.Entry<String, String> firstSuccess = allExtracted.entrySet().iterator().next();
                    primaryValue = firstSuccess.getValue();
                    allExtracted.put("primary_key", firstSuccess.getKey());
                } else {
                    allExtracted.put("primary_key", primaryKey);
                }

                allExtracted.put("success_count", String.valueOf(successCount));
                allExtracted.put("total_count", String.valueOf(extractorMap.size()));

                return ExtractionResult.success(primaryValue, allExtracted);
            } catch (Exception e) {
                return ExtractionResult.failure("Multi-value extraction error: " + e.getMessage());
            }
        }
    }

    /**
     * Extractor that applies a transformation to another extractor's result
     */
    public static class TransformingExtractor extends ResponseExtractor {
        private final ResponseExtractor baseExtractor;
        private final ValueTransformer transformer;

        public interface ValueTransformer {
            String transform(String value);
        }

        public TransformingExtractor(@NonNull ResponseExtractor baseExtractor,
                                    @NonNull ValueTransformer transformer) {
            if (baseExtractor == null) {
                throw new IllegalArgumentException("Base extractor cannot be null");
            }
            if (transformer == null) {
                throw new IllegalArgumentException("Transformer cannot be null");
            }
            this.baseExtractor = baseExtractor;
            this.transformer = transformer;
        }

        @NonNull
        @Override
        public ExtractionResult extract(@NonNull String response) {
            if (response == null) {
                return ExtractionResult.failure("Response is null");
            }

            try {
                ExtractionResult baseResult = baseExtractor.extract(response);
                if (!baseResult.isSuccess()) {
                    return baseResult; // Pass through failure
                }

                String transformedValue = transformer.transform(baseResult.getValue());
                if (transformedValue == null) {
                    return ExtractionResult.failure("Transformation returned null");
                }

                Map<String, String> metadata = new HashMap<>(baseResult.getMetadata());
                metadata.put("original_value", baseResult.getValue());

                return ExtractionResult.success(transformedValue, metadata);
            } catch (Exception e) {
                return ExtractionResult.failure("Transformation error: " + e.getMessage());
            }
        }
    }
}
