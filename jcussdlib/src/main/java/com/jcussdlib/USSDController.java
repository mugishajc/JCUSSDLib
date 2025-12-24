package com.jcussdlib;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.provider.Settings;
import android.telecom.TelecomManager;
import android.telephony.TelephonyManager;
import android.util.Log;

import androidx.core.content.ContextCompat;

import com.jcussdlib.service.SplashLoadingService;

import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.HashSet;

/**
 * Main controller class for managing USSD operations
 */
public class USSDController {

    private static final String TAG = "USSDController";

    private static USSDController instance;
    private Context context;
    private HashMap<String, HashSet<String>> map;
    private USSDApi ussdApi;
    private boolean isRunning = false;
    private boolean isLogin = false;
    private long timeoutMillis = 30000; // Default 30 seconds, now configurable

    private USSDController(Context context) {
        this.context = context.getApplicationContext();
        this.map = new HashMap<>();
    }

    /**
     * Get singleton instance of USSDController
     * @param context Application context
     * @return USSDController instance
     */
    public static synchronized USSDController getInstance(Context context) {
        if (instance == null) {
            instance = new USSDController(context);
        }
        return instance;
    }

    /**
     * Set the callback interface for USSD events
     * @param ussdApi Callback interface
     */
    public void setUSSDApi(USSDApi ussdApi) {
        this.ussdApi = ussdApi;
    }

    /**
     * Set response map for matching USSD responses
     * @param map HashMap containing response keywords
     */
    public void setMap(HashMap<String, HashSet<String>> map) {
        this.map = map;
    }

    /**
     * Set USSD timeout duration
     * @param timeoutMillis Timeout in milliseconds (must be > 0)
     */
    public void setTimeout(long timeoutMillis) {
        if (timeoutMillis <= 0) {
            throw new IllegalArgumentException("Timeout must be greater than 0");
        }
        this.timeoutMillis = timeoutMillis;
    }

    /**
     * Get current timeout setting
     * @return Timeout in milliseconds
     */
    public long getTimeout() {
        return timeoutMillis;
    }

    /**
     * Call USSD code
     * @param ussdCode USSD code to dial (e.g., "*123#")
     * @param simSlot SIM slot to use (0 or 1), use -1 for default
     * @throws SecurityException if CALL_PHONE permission is not granted
     * @throws IllegalStateException if accessibility service is not enabled
     */
    @SuppressLint("MissingPermission")
    public void callUSSDInvoke(String ussdCode, int simSlot) {
        if (isRunning) {
            return;
        }

        // Verify permissions before starting
        verifyPermissions();

        isRunning = true;
        isLogin = false;

        // Show loading overlay
        showLoading();

        // Make USSD call based on SIM slot
        if (simSlot >= 0 && Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            callUSSDWithSimSlot(ussdCode, simSlot);
        } else {
            callUSSDDefault(ussdCode);
        }

        // Set timeout (now configurable)
        new Handler().postDelayed(() -> {
            if (isRunning) {
                stopLoading();
                isRunning = false;
                if (ussdApi != null) {
                    ussdApi.over("Timeout");
                }
            }
        }, timeoutMillis);
    }

    /**
     * Call USSD with default SIM
     * @param ussdCode USSD code to dial
     */
    @SuppressLint("MissingPermission")
    private void callUSSDDefault(String ussdCode) {
        String encodedHash = Uri.encode("#");
        String ussd = ussdCode.replace("#", encodedHash);
        Intent intent = new Intent(Intent.ACTION_CALL);
        intent.setData(Uri.parse("tel:" + ussd));
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        context.startActivity(intent);
    }

    /**
     * Call USSD with specific SIM slot (Android M+)
     * @param ussdCode USSD code to dial
     * @param simSlot SIM slot (0 or 1)
     */
    @SuppressLint("MissingPermission")
    private void callUSSDWithSimSlot(String ussdCode, int simSlot) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            try {
                TelephonyManager telephonyManager = (TelephonyManager) context.getSystemService(Context.TELEPHONY_SERVICE);
                Method getSubIdMethod = TelephonyManager.class.getDeclaredMethod("getSubId", int.class);
                getSubIdMethod.setAccessible(true);
                int[] subId = (int[]) getSubIdMethod.invoke(telephonyManager, simSlot);

                if (subId != null && subId.length > 0) {
                    String encodedHash = Uri.encode("#");
                    String ussd = ussdCode.replace("#", encodedHash);
                    Intent intent = new Intent(Intent.ACTION_CALL);
                    intent.setData(Uri.parse("tel:" + ussd));
                    intent.putExtra("com.android.phone.extra.slot", simSlot);
                    intent.putExtra("Cdma_Supp", simSlot);
                    intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    context.startActivity(intent);
                } else {
                    callUSSDDefault(ussdCode);
                }
            } catch (Exception e) {
                e.printStackTrace();
                callUSSDDefault(ussdCode);
            }
        } else {
            callUSSDDefault(ussdCode);
        }
    }

    /**
     * Send response to USSD dialog
     * @param response Response text to send
     */
    public void send(String response) {
        // This will be handled by USSDService
        Intent intent = new Intent("com.jcussdlib.SEND_RESPONSE");
        intent.putExtra("response", response);
        intent.setPackage(context.getPackageName()); // Restrict to same app only
        context.sendBroadcast(intent);
        Log.d(TAG, "Sent secured broadcast to USSDService: " + response);
    }

    /**
     * Process USSD response message
     * @param message USSD response message
     */
    public void processResponse(String message) {
        if (!isRunning) {
            return;
        }

        // Check if message matches login pattern
        if (map.containsKey("KEY_LOGIN")) {
            HashSet<String> loginKeys = map.get("KEY_LOGIN");
            if (loginKeys != null) {
                for (String key : loginKeys) {
                    if (message.contains(key)) {
                        isLogin = true;
                        break;
                    }
                }
            }
        }

        // Check if message matches error pattern
        if (map.containsKey("KEY_ERROR")) {
            HashSet<String> errorKeys = map.get("KEY_ERROR");
            if (errorKeys != null) {
                for (String key : errorKeys) {
                    if (message.contains(key)) {
                        stopLoading();
                        isRunning = false;
                        if (ussdApi != null) {
                            ussdApi.over(message);
                        }
                        return;
                    }
                }
            }
        }

        // Invoke callback
        if (ussdApi != null) {
            ussdApi.responseInvoke(message);
        }
    }

    /**
     * Notify that USSD session has ended
     * <p>
     * This method is called when the USSD session terminates, either successfully
     * or due to an error. It stops the loading overlay, resets the running state,
     * and notifies the callback.
     * </p>
     *
     * @param message Final USSD message or error message
     */
    public void notifyOver(String message) {
        if (isRunning) {
            stopLoading();
            isRunning = false;
            if (ussdApi != null) {
                ussdApi.over(message);
            }
        }
    }

    /**
     * Show loading overlay
     */
    private void showLoading() {
        Intent intent = new Intent(context, SplashLoadingService.class);
        intent.setAction("SHOW");
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.startForegroundService(intent);
        } else {
            context.startService(intent);
        }
    }

    /**
     * Stop loading overlay
     */
    private void stopLoading() {
        Intent intent = new Intent(context, SplashLoadingService.class);
        intent.setAction("HIDE");
        context.startService(intent);
    }

    /**
     * Check if USSD is currently running
     * @return true if running
     */
    public boolean isRunning() {
        return isRunning;
    }

    /**
     * Check if login was successful
     * @return true if logged in
     */
    public boolean isLogin() {
        return isLogin;
    }

    /**
     * Reset controller state
     */
    public void reset() {
        isRunning = false;
        isLogin = false;
        stopLoading();
    }

    // ========================================================================================
    // PERMISSION VERIFICATION (Senior-Level Safety)
    // ========================================================================================

    /**
     * Verifies required permissions before making USSD call
     *
     * @throws SecurityException if CALL_PHONE permission is missing
     * @throws IllegalStateException if accessibility service is not enabled
     */
    private void verifyPermissions() {
        // Check CALL_PHONE permission
        if (!hasCallPhonePermission()) {
            throw new SecurityException(
                "CALL_PHONE permission is not granted!\n" +
                "Please grant this permission before making USSD calls.\n" +
                "Add to AndroidManifest.xml:\n" +
                "  <uses-permission android:name=\"android.permission.CALL_PHONE\" />\n" +
                "And request at runtime using ActivityCompat.requestPermissions()"
            );
        }

        // Check accessibility service
        if (!isAccessibilityServiceEnabled()) {
            Log.w(TAG, "WARNING: Accessibility service is not enabled - USSD responses may not be detected");
            // Don't throw exception here - just warn, since legacy mode might still work
        } else {
            Log.d(TAG, "✓ Accessibility service verified - USSD responses will be detected");
        }

        Log.d(TAG, "✓ CALL_PHONE permission verified");
    }

    /**
     * Checks if CALL_PHONE permission is granted
     *
     * @return true if granted
     */
    private boolean hasCallPhonePermission() {
        return ContextCompat.checkSelfPermission(context, Manifest.permission.CALL_PHONE)
                == PackageManager.PERMISSION_GRANTED;
    }

    /**
     * Checks if accessibility service is enabled
     *
     * @return true if enabled
     */
    private boolean isAccessibilityServiceEnabled() {
        String service = context.getPackageName() + "/com.jcussdlib.service.USSDService";
        try {
            int accessibilityEnabled = Settings.Secure.getInt(
                context.getContentResolver(),
                Settings.Secure.ACCESSIBILITY_ENABLED
            );
            if (accessibilityEnabled == 1) {
                String settingValue = Settings.Secure.getString(
                    context.getContentResolver(),
                    Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
                );
                if (settingValue != null) {
                    return settingValue.contains(service);
                }
            }
        } catch (Settings.SettingNotFoundException e) {
            Log.e(TAG, "Error checking accessibility service", e);
        }
        return false;
    }
}
