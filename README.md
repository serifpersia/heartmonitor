# Heart Monitor

<div align="center">
<img width="256" height="256" alt="image" src="https://github.com/user-attachments/assets/af900541-2ad9-41ad-a62c-406b83fd2004" />
  
</div>

<div align="center">
<img width="624" height="1168" alt="image" src="https://github.com/user-attachments/assets/23fb3be5-c76b-455d-ad8d-299d4af72e0f" />

</div>
A simple Android application to monitor your heart rate using your device's body sensors.

## Features

- Real-time heart rate display (BPM).
- Heart rate graph visualization.
- Displays average, minimum, and maximum BPM.
- Haptic feedback on detected heartbeats.
- Requires `BODY_SENSORS` permission.

## How to Use

1.  **Install the App:**
    *   You can download the latest release APK from the  [![Release](https://img.shields.io/github/release/serifpersia/heartmonitor.svg?style=flat-square)](https://github.com/serifpersia/heartmonitor/releases)

    *   Install the APK on your Android device.

2.  **Grant Permission:**
    *   When you first open the app, it will request the "Body Sensors" permission. Please grant this permission for the app to function.
    *   If you deny it, you can manually grant it later in your device's `Settings > Apps > Heart Monitor > Permissions`.
heartmonitor
3.  **Start Monitoring:**
    *   Place your finger firmly on the heart rate sensor (usually located near the camera on the back of your phone, or on a dedicated sensor area if your device has one).
    *   Hold still and wait for the app to calibrate and start displaying your heart rate.

## Build from Source

If you want to build the app from its source code:

1.  **Prerequisites:**
    *   Android Studio installed on your machine.
    *   A device to run the app.

2.  **Steps:**
    *   Clone this repository to your local machine.
    *   Open the project in Android Studio.
    *   Sync Gradle files.
    *   Connect your Android device or select an emulator.
    *   Click the 'Run' button (green play icon) in Android Studio to build and install the app.
