package com.jcussdlib;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.telecom.TelecomManager;
import android.telephony.TelephonyManager;

import com.jcussdlib.service.SplashLoadingService;

import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.HashSet;

/**
 * Main controller class for managing USSD operations
 */
public class USSDController {

    private static USSDController instance;
    private Context context;
    private HashMap<String, HashSet<String>> map;
    private USSDApi ussdApi;
    private boolean isRunning = false;
    private boolean isLogin = false;

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
     * Call USSD code
     * @param ussdCode USSD code to dial (e.g., "*123#")
     * @param simSlot SIM slot to use (0 or 1), use -1 for default
     */
    @SuppressLint("MissingPermission")
    public void callUSSDInvoke(String ussdCode, int simSlot) {
        if (isRunning) {
            return;
        }

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

        // Set timeout
        new Handler().postDelayed(() -> {
            if (isRunning) {
                stopLoading();
                isRunning = false;
                if (ussdApi != null) {
                    ussdApi.over("Timeout");
                }
            }
        }, 30000);
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
        context.sendBroadcast(intent);
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
}
