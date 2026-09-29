# Smartglasses companion app — development update

This branch matches [firmware 0.2.0](https://github.com/DrReVaN/Glasses_V0.1_BLE/tree/dev/firmware-fixes-ota).

The app now uses an asynchronous, bounded GATT operation queue. Each fragment waits for its write callback; failed writes, disconnects and timeouts abort the transfer. Bluetooth bonding happens before queued writes start. Compare the six-digit number with the glasses and hold pad 1 for one second to approve; pad 3 rejects. Open the pairing window with pads 1 and 3 held together for three seconds.

Text is limited to 252 UTF-8 bytes without splitting a Unicode code point. Packets contain two header bytes and at most 18 payload bytes. Time synchronization uses ASCII HHmmddMMyyyy after discovery and once per minute. The firmware continues time locally. Characteristic objects are rediscovered after reconnect.

## Build and tests

Use JDK 11, Android SDK platform 31 and Build Tools **30.0.3**. From `test/` run:

```text
gradlew testDebugUnitTest assembleDebug --no-daemon
```

The queue and protocol regression suite also runs without an Android SDK:

```text
javac -encoding UTF-8 -d build/tests test/app/src/main/java/com/test/GattQueue.java test/app/src/main/java/com/test/SmartglassesProtocol.java tests/GattQueueTest.java
java -cp build/tests GattQueueTest
```

GitHub Actions runs both checks and uploads a debug APK. Actual Android BLE pairing, permissions and reconnect behavior still require a phone/device test.

Firmware OTA is prepared in the firmware repository with a desktop BLE client and installation instructions. The Android app does not contain an OTA upload screen.
