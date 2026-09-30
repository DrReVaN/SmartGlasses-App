# Smartglasses companion — Android 1.2.0

Companion for the WB35 Smartglasses running [firmware 0.3.0 or the archived 0.2.0 build](https://github.com/DrReVaN/Glasses_V0.1_BLE/releases).

This development branch includes runtime Bluetooth permissions, immediate ten-second scanning, protected in-process notification delivery, a foreground connection service with bounded reconnect, message expiry/loss counters, correct notification style handling, full service validation and an Android client for the project's CPU1 OTA protocol. Version 1.1.1 allows reading reset/fault diagnostics in the recovery bootloader as well as in the application.

Version 1.1.2 retains OTA failures across reconnects and diagnostic reads. It distinguishes an unacknowledged BEGIN, a partial transfer with the acknowledged byte count, and an unacknowledged commit. Update success still requires both the END acknowledgement and application-mode reconnection.

Version 1.2.0 checks GitHub for firmware releases when connected and every six hours while the connection service runs. The user can defer a version, explicitly download and install it, or select an older verified release. Downloads and version checks never start OTA automatically. Firmware 0.3.0 exposes its actual release version, image size and CRC so that successful reconnection can be checked against the selected package. Install this app before upgrading to firmware 0.3.0. See [firmware releases, version validation and rollback](docs/FIRMWARE_RELEASES.md).

Pairing requires comparing the six-digit number on phone and glasses. Hold pads 1+3 for three seconds to open pairing, pad 1 for one second to accept, or pad 3 to reject. Enable notification access separately in Android settings.

For OTA, first install the prepared bootloader once by ST-Link or the Wemos programmer. Select a GitHub release or the matching application BIN and JSON package in the app, start the update and approve `OTA?` on the glasses. Interrupted uploads can be restarted in the recovery bootloader. CPU2/FUS updates and automatic rollback are not supported.

Build with JDK 21 and Android SDK 36. From `test/`:

```text
gradlew testDebugUnitTest lintDebug assembleDebug --no-daemon
```

CI builds a debug APK and uploads test/lint reports. See [Android setup, OTA, signing and acceptance tests](docs/ANDROID.md) for details and remaining physical-device verification. The application ID remains `com.test` for existing installations; versionCode is now 5.
