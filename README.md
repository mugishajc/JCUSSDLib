# JCUSSDLib

A powerful Android library for automating USSD (Unstructured Supplementary Service Data) interactions. JCUSSDLib enables your application to programmatically send USSD requests and handle responses without manual user intervention.

## Features

- **Automated USSD Invocation**: Send USSD codes programmatically
- **Response Handling**: Capture and process USSD dialog responses automatically
- **Dual SIM Support**: Select which SIM card to use for USSD requests (Android M+)
- **Accessibility Service Integration**: Intercepts system USSD dialogs seamlessly
- **Loading Overlay**: Optional visual feedback during USSD operations
- **Sequential Menu Navigation**: Send multiple responses for hierarchical USSD menus
- **Customizable Response Parsing**: Configure keywords for login success and error detection

## Requirements

- **Minimum SDK**: API 23 (Android 6.0 Marshmallow)
- **Target SDK**: API 34
- **Language**: Java 8+

## Permissions

The library requires the following permissions:

```xml
<uses-permission android:name="android.permission.CALL_PHONE" />
<uses-permission android:name="android.permission.SYSTEM_ALERT_WINDOW" />
```

## Installation

### Step 1: Add the library module to your project

1. Copy the `jcussdlib` folder into your project
2. Add it to your `settings.gradle`:

```gradle
include ':app', ':jcussdlib'
```

3. Add the dependency in your app's `build.gradle`:

```gradle
dependencies {
    implementation project(':jcussdlib')
}
```

### Step 2: Update AndroidManifest

The library's services are automatically merged into your app's manifest. No additional configuration needed.

## Usage

### 1. Initialize USSDController

```java
USSDController ussdController = USSDController.getInstance(context);
ussdController.setUSSDApi(new USSDApi() {
    @Override
    public void responseInvoke(String message) {
        // Handle USSD response
        Log.d("USSD", "Response: " + message);
    }

    @Override
    public void over(String message) {
        // USSD session ended
        Log.d("USSD", "Session ended: " + message);
    }
});
```

### 2. Configure Response Keywords (Optional)

Define keywords to identify successful responses and errors:

```java
HashMap<String, HashSet<String>> map = new HashMap<>();

// Login/success keywords
HashSet<String> loginKeys = new HashSet<>();
loginKeys.add("successful");
loginKeys.add("balance");
loginKeys.add("confirmed");
map.put("KEY_LOGIN", loginKeys);

// Error keywords
HashSet<String> errorKeys = new HashSet<>();
errorKeys.add("error");
errorKeys.add("failed");
errorKeys.add("invalid");
map.put("KEY_ERROR", errorKeys);

ussdController.setMap(map);
```

### 3. Make USSD Call

```java
// Using default SIM
ussdController.callUSSDInvoke("*123#", -1);

// Using specific SIM (0 for SIM 1, 1 for SIM 2)
ussdController.callUSSDInvoke("*123#", 0);
```

### 4. Send Response to USSD Menu

For interactive USSD menus, send responses programmatically:

```java
@Override
public void responseInvoke(String message) {
    if (message.contains("Select option")) {
        // Send option "1" to the USSD menu
        ussdController.send("1");
    }
}
```

## Setup Instructions for Users

### Enable Accessibility Service

The library requires accessibility service to be enabled:

1. Open **Settings** → **Accessibility**
2. Find **USSD Service** (or your app name)
3. Toggle it **ON**

```java
// Open accessibility settings programmatically
Intent intent = new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS);
startActivity(intent);
```

### Enable Overlay Permission (Android M+)

For the loading overlay:

```java
if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
    if (!Settings.canDrawOverlays(this)) {
        Intent intent = new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:" + getPackageName()));
        startActivity(intent);
    }
}
```

### Request CALL_PHONE Permission

```java
if (ContextCompat.checkSelfPermission(this, Manifest.permission.CALL_PHONE)
        != PackageManager.PERMISSION_GRANTED) {
    ActivityCompat.requestPermissions(this,
            new String[]{Manifest.permission.CALL_PHONE}, REQUEST_CODE);
}
```

## Complete Example

```java
public class MainActivity extends AppCompatActivity implements USSDApi {

    private USSDController ussdController;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // Initialize controller
        ussdController = USSDController.getInstance(this);
        ussdController.setUSSDApi(this);

        // Configure response keywords
        HashMap<String, HashSet<String>> map = new HashMap<>();
        HashSet<String> loginKeys = new HashSet<>();
        loginKeys.add("successful");
        map.put("KEY_LOGIN", loginKeys);
        ussdController.setMap(map);

        // Make USSD call
        Button dialButton = findViewById(R.id.btn_dial);
        dialButton.setOnClickListener(v -> {
            if (checkPermissions()) {
                ussdController.callUSSDInvoke("*123#", -1);
            }
        });
    }

    @Override
    public void responseInvoke(String message) {
        runOnUiThread(() -> {
            Toast.makeText(this, "Response: " + message, Toast.LENGTH_SHORT).show();
        });
    }

    @Override
    public void over(String message) {
        runOnUiThread(() -> {
            Toast.makeText(this, "Session ended", Toast.LENGTH_SHORT).show();
        });
    }

    private boolean checkPermissions() {
        // Check and request permissions
        return true;
    }
}
```

## API Reference

### USSDController

| Method | Parameters | Description |
|--------|------------|-------------|
| `getInstance(Context)` | `context` - Application context | Get singleton instance |
| `setUSSDApi(USSDApi)` | `ussdApi` - Callback interface | Set event listener |
| `setMap(HashMap)` | `map` - Response keywords | Configure response matching |
| `callUSSDInvoke(String, int)` | `ussdCode` - USSD code<br>`simSlot` - SIM slot (-1, 0, or 1) | Initiate USSD call |
| `send(String)` | `response` - Text to send | Send response to USSD menu |
| `reset()` | - | Reset controller state |
| `isRunning()` | - | Check if USSD is active |
| `isLogin()` | - | Check if login succeeded |

### USSDApi Interface

```java
public interface USSDApi {
    void responseInvoke(String message);  // Called on each USSD response
    void over(String message);            // Called when session ends
}
```

## How It Works

1. **Accessibility Service**: The library uses Android's AccessibilityService to monitor system windows
2. **Dialog Interception**: When a USSD dialog appears, the service captures its content
3. **Response Processing**: The captured text is parsed and callbacks are triggered
4. **Automated Interaction**: The service can automatically fill input fields and click buttons

## Troubleshooting

### USSD not working

- Ensure accessibility service is enabled
- Check that CALL_PHONE permission is granted
- Verify the device supports USSD (test manually first)
- Some carriers may block automated USSD access

### Overlay not showing

- Enable overlay permission in settings
- Check for battery optimization restrictions
- Ensure service is running in foreground (Android O+)

### Dual SIM not working

- Dual SIM support requires Android M+ (API 23)
- SIM slot indices may vary by manufacturer
- Some devices use different APIs - test thoroughly

## Architecture

```
JCUSSDLib/
├── USSDController       - Main API and business logic
├── USSDApi             - Callback interface
├── service/
│   ├── USSDService     - Accessibility service for dialog interception
│   └── SplashLoadingService - Foreground service for loading overlay
└── res/
    ├── layout/         - Loading overlay layout
    └── xml/            - Accessibility service configuration
```

## License

This library is available under the Apache License 2.0.

## Contributing

Contributions are welcome! Please feel free to submit issues and pull requests.

## Changelog

### Version 1.0.0
- Initial release
- USSD automation with accessibility service
- Dual SIM support
- Loading overlay
- Response keyword matching
- Sequential menu navigation

## Credits

Developed with inspiration from VoIpUSSD library architecture.

## Support

For issues, questions, or contributions, please open an issue on the GitHub repository.
