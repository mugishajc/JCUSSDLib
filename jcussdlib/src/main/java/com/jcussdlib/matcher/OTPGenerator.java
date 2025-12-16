package com.jcussdlib.matcher;

import androidx.annotation.NonNull;

import java.util.ArrayList;
import java.util.List;

/**
 * OTP Generator for brute-force matching
 * <p>
 * Generates all possible OTP combinations for a given digit length.
 * </p>
 *
 * <p>Examples:</p>
 * <ul>
 *   <li>4 digits: 0001, 0002, 0003, ..., 9999 (9999 OTPs)</li>
 *   <li>6 digits: 000001, 000002, ..., 999999 (999,999 OTPs)</li>
 * </ul>
 *
 * <p>Thread-safe: All methods are stateless</p>
 *
 * @author Mugisha Jean Claude
 * @version 2.0.0
 * @since 2.0.0
 */
public class OTPGenerator {

    private static final String TAG = "OTPGenerator";

    /**
     * Generates all possible OTP codes for given digit length
     *
     * @param digits Number of digits (e.g., 4 for 0001-9999)
     * @return List of all possible OTPs
     */
    @NonNull
    public static List<String> generateAll(int digits) {
        if (digits < 1) {
            throw new IllegalArgumentException("Digits must be at least 1");
        }
        if (digits > 8) {
            throw new IllegalArgumentException("Digits cannot exceed 8 (too many combinations)");
        }

        int totalCombinations = (int) Math.pow(10, digits);
        List<String> otps = new ArrayList<>(totalCombinations);

        for (int i = 0; i < totalCombinations; i++) {
            otps.add(formatOTP(i, digits));
        }

        return otps;
    }

    /**
     * Generates OTP range from start to end (inclusive)
     *
     * @param digits Number of digits
     * @param start  Start number (e.g., 1 for 0001)
     * @param end    End number (e.g., 9999)
     * @return List of OTPs in range
     */
    @NonNull
    public static List<String> generateRange(int digits, int start, int end) {
        if (digits < 1 || digits > 8) {
            throw new IllegalArgumentException("Digits must be between 1 and 8");
        }
        if (start < 0 || end < start) {
            throw new IllegalArgumentException("Invalid range: start=" + start + ", end=" + end);
        }

        int maxValue = (int) Math.pow(10, digits) - 1;
        if (end > maxValue) {
            end = maxValue;
        }

        List<String> otps = new ArrayList<>(end - start + 1);
        for (int i = start; i <= end; i++) {
            otps.add(formatOTP(i, digits));
        }

        return otps;
    }

    /**
     * Generates sequential OTPs (1, 2, 3, ...)
     *
     * @param digits Number of digits
     * @return List of sequential OTPs
     */
    @NonNull
    public static List<String> generateSequential(int digits) {
        return generateAll(digits);
    }

    /**
     * Generates common/popular OTPs first, then remaining
     * <p>
     * Common patterns: 1234, 0000, 1111, 2222, etc.
     * This can speed up brute-force by trying likely OTPs first
     * </p>
     *
     * @param digits Number of digits
     * @return List of OTPs with common ones first
     */
    @NonNull
    public static List<String> generateCommonFirst(int digits) {
        List<String> otps = new ArrayList<>();

        // Add common patterns first
        List<String> commonPatterns = getCommonPatterns(digits);
        otps.addAll(commonPatterns);

        // Add remaining OTPs
        int totalCombinations = (int) Math.pow(10, digits);
        for (int i = 0; i < totalCombinations; i++) {
            String otp = formatOTP(i, digits);
            if (!commonPatterns.contains(otp)) {
                otps.add(otp);
            }
        }

        return otps;
    }

    /**
     * Gets list of common OTP patterns
     */
    @NonNull
    private static List<String> getCommonPatterns(int digits) {
        List<String> common = new ArrayList<>();

        // Sequential ascending (1234, 12345, etc.)
        StringBuilder ascending = new StringBuilder();
        for (int i = 1; i <= digits; i++) {
            ascending.append(i % 10);
        }
        common.add(ascending.toString());

        // All same digits (0000, 1111, 2222, etc.)
        for (int digit = 0; digit <= 9; digit++) {
            StringBuilder same = new StringBuilder();
            for (int i = 0; i < digits; i++) {
                same.append(digit);
            }
            common.add(same.toString());
        }

        // Reverse sequential (9876, 98765, etc.)
        StringBuilder descending = new StringBuilder();
        for (int i = 9; i >= Math.max(0, 10 - digits); i--) {
            descending.append(i);
        }
        if (descending.length() == digits) {
            common.add(descending.toString());
        }

        // Alternating patterns (0101, 1010, etc.)
        if (digits % 2 == 0) {
            StringBuilder alt01 = new StringBuilder();
            StringBuilder alt10 = new StringBuilder();
            for (int i = 0; i < digits; i++) {
                alt01.append(i % 2);
                alt10.append((i + 1) % 2);
            }
            common.add(alt01.toString());
            common.add(alt10.toString());
        }

        // Birth years (1980-2024) if 4 digits
        if (digits == 4) {
            for (int year = 1980; year <= 2024; year++) {
                common.add(String.valueOf(year));
            }
        }

        return common;
    }

    /**
     * Formats a number as OTP with leading zeros
     *
     * @param number Number to format
     * @param digits Total digits
     * @return Formatted OTP string
     */
    @NonNull
    public static String formatOTP(int number, int digits) {
        String format = "%0" + digits + "d";
        return String.format(format, number);
    }

    /**
     * Generates OTPs in chunks for memory-efficient processing
     * <p>
     * Instead of generating all 9999 OTPs at once, generates in chunks
     * </p>
     *
     * @param digits    Number of digits
     * @param chunkSize Size of each chunk
     * @return List of OTP chunks
     */
    @NonNull
    public static List<List<String>> generateChunks(int digits, int chunkSize) {
        if (chunkSize < 1) {
            throw new IllegalArgumentException("Chunk size must be at least 1");
        }

        int totalCombinations = (int) Math.pow(10, digits);
        List<List<String>> chunks = new ArrayList<>();

        for (int start = 0; start < totalCombinations; start += chunkSize) {
            int end = Math.min(start + chunkSize - 1, totalCombinations - 1);
            chunks.add(generateRange(digits, start, end));
        }

        return chunks;
    }

    /**
     * Calculates total number of OTPs for given digits
     *
     * @param digits Number of digits
     * @return Total combinations
     */
    public static int getTotalCombinations(int digits) {
        return (int) Math.pow(10, digits);
    }

    /**
     * Estimates time to brute-force all OTPs
     *
     * @param digits            Number of digits
     * @param secondsPerAttempt Average time per OTP attempt
     * @return Estimated total seconds
     */
    public static long estimateBruteForceTime(int digits, int secondsPerAttempt) {
        int totalOTPs = getTotalCombinations(digits);
        return (long) totalOTPs * secondsPerAttempt;
    }

    /**
     * Formats estimated time as human-readable string
     */
    @NonNull
    public static String formatEstimatedTime(long totalSeconds) {
        if (totalSeconds < 60) {
            return totalSeconds + " seconds";
        } else if (totalSeconds < 3600) {
            return (totalSeconds / 60) + " minutes";
        } else if (totalSeconds < 86400) {
            return (totalSeconds / 3600) + " hours";
        } else {
            return (totalSeconds / 86400) + " days";
        }
    }
}
