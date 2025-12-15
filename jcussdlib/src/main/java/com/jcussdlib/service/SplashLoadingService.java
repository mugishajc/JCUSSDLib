package com.jcussdlib.service;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Intent;
import android.graphics.PixelFormat;
import android.os.Build;
import android.os.IBinder;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.WindowManager;

import androidx.core.app.NotificationCompat;

import com.jcussdlib.R;

/**
 * Service to display loading overlay during USSD operations
 */
public class SplashLoadingService extends Service {

    private static final String CHANNEL_ID = "JCUSSDLib_Channel";
    private static final int NOTIFICATION_ID = 1001;

    private WindowManager windowManager;
    private View overlayView;
    private boolean isShowing = false;

    @Override
    public void onCreate() {
        super.onCreate();
        windowManager = (WindowManager) getSystemService(WINDOW_SERVICE);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null && intent.getAction() != null) {
            String action = intent.getAction();

            if ("SHOW".equals(action)) {
                showOverlay();
                startForeground(NOTIFICATION_ID, createNotification());
            } else if ("HIDE".equals(action)) {
                hideOverlay();
                stopForeground(true);
                stopSelf();
            }
        }

        return START_NOT_STICKY;
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        hideOverlay();
    }

    /**
     * Show loading overlay
     */
    private void showOverlay() {
        if (isShowing) {
            return;
        }

        try {
            // Inflate overlay layout
            overlayView = LayoutInflater.from(this).inflate(R.layout.loading_overlay, null);

            // Configure window parameters
            WindowManager.LayoutParams params;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                params = new WindowManager.LayoutParams(
                        WindowManager.LayoutParams.MATCH_PARENT,
                        WindowManager.LayoutParams.MATCH_PARENT,
                        WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE |
                                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL |
                                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                        PixelFormat.TRANSLUCENT
                );
            } else {
                params = new WindowManager.LayoutParams(
                        WindowManager.LayoutParams.MATCH_PARENT,
                        WindowManager.LayoutParams.MATCH_PARENT,
                        WindowManager.LayoutParams.TYPE_SYSTEM_ALERT,
                        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE |
                                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL |
                                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                        PixelFormat.TRANSLUCENT
                );
            }

            params.gravity = Gravity.CENTER;

            // Add view to window
            windowManager.addView(overlayView, params);
            isShowing = true;

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * Hide loading overlay
     */
    private void hideOverlay() {
        if (isShowing && overlayView != null) {
            try {
                windowManager.removeView(overlayView);
                overlayView = null;
                isShowing = false;
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    /**
     * Create notification for foreground service
     * @return Notification object
     */
    private Notification createNotification() {
        // Create notification channel for Android O+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID,
                    "USSD Service",
                    NotificationManager.IMPORTANCE_LOW
            );
            channel.setDescription("Processing USSD request");
            NotificationManager manager = getSystemService(NotificationManager.class);
            if (manager != null) {
                manager.createNotificationChannel(channel);
            }
        }

        // Build notification
        return new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("JCUSSDLib")
                .setContentText("Processing USSD request...")
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .setOngoing(true)
                .build();
    }
}
