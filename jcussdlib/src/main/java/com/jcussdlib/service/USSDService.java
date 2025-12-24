package com.jcussdlib.service;

import android.accessibilityservice.AccessibilityService;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Build;
import android.os.Bundle;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;

import com.jcussdlib.USSDController;

import java.util.List;

/**
 * Accessibility Service to intercept and handle USSD dialogs
 */
public class USSDService extends AccessibilityService {

    private static final String TAG = "USSDService";
    private BroadcastReceiver sendResponseReceiver;

    @Override
    public void onCreate() {
        super.onCreate();

        // Register receiver for sending responses (secured to same app only)
        sendResponseReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                // Security: Verify broadcast is from our own app
                if (intent == null) {
                    Log.w(TAG, "Received null intent - ignoring");
                    return;
                }

                // Check if intent is restricted to our package
                String senderPackage = intent.getPackage();
                if (senderPackage != null && !senderPackage.equals(context.getPackageName())) {
                    Log.w(TAG, "Received broadcast from external package: " + senderPackage + " - ignoring");
                    return;
                }

                String response = intent.getStringExtra("response");
                if (response != null) {
                    Log.d(TAG, "Received secured broadcast response: " + response);
                    sendResponse(response);
                } else {
                    Log.w(TAG, "Received broadcast with null response - ignoring");
                }
            }
        };

        IntentFilter filter = new IntentFilter("com.jcussdlib.SEND_RESPONSE");
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            // Android 13+: Use RECEIVER_NOT_EXPORTED for security
            registerReceiver(sendResponseReceiver, filter, Context.RECEIVER_NOT_EXPORTED);
            Log.d(TAG, "Registered secured broadcast receiver (RECEIVER_NOT_EXPORTED)");
        } else {
            // Pre-Android 13: Still secure via package validation in onReceive
            registerReceiver(sendResponseReceiver, filter);
            Log.d(TAG, "Registered broadcast receiver with package validation");
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (sendResponseReceiver != null) {
            unregisterReceiver(sendResponseReceiver);
        }
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        if (event == null) {
            return;
        }

        if (event.getEventType() == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED ||
            event.getEventType() == AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED) {

            AccessibilityNodeInfo rootNode = getRootInActiveWindow();
            if (rootNode == null) {
                return;
            }

            String packageName = event.getPackageName() != null ? event.getPackageName().toString() : "";

            // Check if it's a USSD dialog (typically from phone app)
            if (packageName.contains("phone") || packageName.contains("telecom")) {
                processUSSDDialog(rootNode);
            }

            rootNode.recycle();
        }
    }

    @Override
    public void onInterrupt() {
        // Called when service is interrupted
    }

    /**
     * Process USSD dialog window
     * @param rootNode Root node of the window
     */
    private void processUSSDDialog(AccessibilityNodeInfo rootNode) {
        if (rootNode == null) {
            return;
        }

        // Find text content in the dialog
        String message = extractTextFromNode(rootNode);

        if (message != null && !message.isEmpty()) {
            // Send message to controller
            USSDController controller = USSDController.getInstance(this);
            controller.processResponse(message);

            // Check if there's an OK/Cancel button (session ended)
            if (hasButton(rootNode, "OK") || hasButton(rootNode, "Cancel")) {
                // Dismiss dialog
                performGlobalAction(GLOBAL_ACTION_BACK);
                controller.notifyOver(message);
            }
        }
    }

    /**
     * Extract text content from node tree
     * @param node Root node
     * @return Extracted text
     */
    private String extractTextFromNode(AccessibilityNodeInfo node) {
        if (node == null) {
            return "";
        }

        StringBuilder text = new StringBuilder();

        // Get text from current node
        if (node.getText() != null) {
            text.append(node.getText().toString()).append(" ");
        }

        // Recursively get text from children
        for (int i = 0; i < node.getChildCount(); i++) {
            AccessibilityNodeInfo child = node.getChild(i);
            if (child != null) {
                String childText = extractTextFromNode(child);
                if (!childText.isEmpty()) {
                    text.append(childText);
                }
                child.recycle();
            }
        }

        return text.toString().trim();
    }

    /**
     * Check if node tree contains a button with specific text
     * @param node Root node
     * @param buttonText Button text to find
     * @return true if button found
     */
    private boolean hasButton(AccessibilityNodeInfo node, String buttonText) {
        if (node == null) {
            return false;
        }

        // Check current node
        if ("android.widget.Button".equals(node.getClassName())) {
            CharSequence text = node.getText();
            if (text != null && text.toString().equalsIgnoreCase(buttonText)) {
                return true;
            }
        }

        // Check children
        for (int i = 0; i < node.getChildCount(); i++) {
            AccessibilityNodeInfo child = node.getChild(i);
            if (child != null) {
                boolean found = hasButton(child, buttonText);
                child.recycle();
                if (found) {
                    return true;
                }
            }
        }

        return false;
    }

    /**
     * Send response by filling input field and clicking send button
     * @param response Response text to send
     */
    private void sendResponse(String response) {
        AccessibilityNodeInfo rootNode = getRootInActiveWindow();
        if (rootNode == null) {
            return;
        }

        // Find EditText input field
        AccessibilityNodeInfo inputNode = findNodeByClass(rootNode, "android.widget.EditText");
        if (inputNode != null) {
            // Set text
            Bundle arguments = new Bundle();
            arguments.putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, response);
            inputNode.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, arguments);
            inputNode.recycle();

            // Find and click send button
            AccessibilityNodeInfo sendButton = findNodeByText(rootNode, "Send");
            if (sendButton == null) {
                sendButton = findNodeByText(rootNode, "OK");
            }

            if (sendButton != null) {
                sendButton.performAction(AccessibilityNodeInfo.ACTION_CLICK);
                sendButton.recycle();
            }
        }

        rootNode.recycle();
    }

    /**
     * Find node by class name
     * @param node Root node
     * @param className Class name to find
     * @return Found node or null (caller must recycle)
     */
    private AccessibilityNodeInfo findNodeByClass(AccessibilityNodeInfo node, String className) {
        if (node == null) {
            return null;
        }

        if (className.equals(node.getClassName())) {
            return node;
        }

        for (int i = 0; i < node.getChildCount(); i++) {
            AccessibilityNodeInfo child = node.getChild(i);
            if (child != null) {
                AccessibilityNodeInfo found = findNodeByClass(child, className);
                child.recycle(); // Always recycle child after use
                if (found != null) {
                    return found; // Found node must be recycled by caller
                }
            }
        }

        return null;
    }

    /**
     * Find node by text content
     * @param node Root node
     * @param text Text to find
     * @return Found node or null (caller must recycle)
     */
    private AccessibilityNodeInfo findNodeByText(AccessibilityNodeInfo node, String text) {
        if (node == null) {
            return null;
        }

        CharSequence nodeText = node.getText();
        if (nodeText != null && nodeText.toString().equalsIgnoreCase(text)) {
            return node;
        }

        for (int i = 0; i < node.getChildCount(); i++) {
            AccessibilityNodeInfo child = node.getChild(i);
            if (child != null) {
                AccessibilityNodeInfo found = findNodeByText(child, text);
                child.recycle(); // Always recycle child after use
                if (found != null) {
                    return found; // Found node must be recycled by caller
                }
            }
        }

        return null;
    }
}
