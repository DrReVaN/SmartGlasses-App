# Android companion 1.1.0

This development version targets Android 16 (API 36), supports Android 5.0+ (API 21), and speaks the firmware 0.2.0 protocol from `Glasses_V0.1_BLE/dev/firmware-fixes-ota`. The existing application ID `com.test` is retained so an owner-signed update can replace an existing installation without creating a second app.

## Setup and normal use

1. Wake the glasses, open **Brille suchen**, and grant the requested permissions. On Android 6–11 both fine-location permission and the system location switch are required by Android's legacy BLE scan. Android 12+ uses Nearby devices. The app does not calculate location and does not request background location.
2. Select the glasses (`SMRT_GLASS`). Results appear on first reception; each scan lasts ten seconds. The firmware does not advertise a service UUID, so the list can contain other devices. Complete Smartglasses service UUIDs, channel properties and protocol version are checked before normal transmission; an unsupported service is rejected before pairing.
3. For first pairing, hold pads 1+3 for three seconds. Compare the displayed six-digit number on both devices. Accept on the phone and hold pad 1 for one second on the glasses. Pad 3 rejects.
4. Enable **Benachrichtigungszugriff**. For a sideloaded APK Android may require enabling restricted settings in the app's system information first. The controls show whether the system listener is actually connected.
5. The private foreground connection service continues while the controls are closed. Its visible notification can reopen the controls or stop the connection. An intentional disconnect disables automatic reconnect. Loss of radio connection triggers up to eight delayed attempts; afterward the user can retry. A permission revocation stops the service safely.

Notification text is delivered directly within the app process. No custom text or connection-state broadcasts are exposed to other applications. The listener normalizes CharSequence, expanded text, inbox and messaging styles. Own notifications, group summaries, ongoing statuses and local-only notifications are skipped. Updates with unchanged text are deduplicated. The forwarding switch and selection of previously seen apps allow control of what is sent.

The in-memory outbox holds at most 16 messages and expires waiting messages after 60 seconds. Text is bounded to 252 UTF-8 bytes. Messages transmit one complete fragment batch at a time, with acknowledged ATT writes. Overload, expiry and disconnected delivery are counted visibly; the app does not persist private texts. Counters describe this process session. “Übertragen” means the BLE write batch completed; it is not a confirmation that the user read the display.

The old hard-coded RSSI trilateration, recurring bitmap allocation and unused debug layouts/helpers were removed from this companion. Their previous implementation remains available in Git history. There is no distance/position claim in the device list.

## OTA from Android

Version 1.1.1 also enables **Brillenstatus lesen** in recovery mode. Read the retained reset flags and fault number before power-cycling glasses that remain in the bootloader after OTA. This operation reads the protected diagnostic characteristic without writing flash or requesting a reset. It is unavailable during upload/post-update verification and while another ATT operation is queued.

Version 1.1.2 keeps the last OTA failure visible after reconnect and appends read diagnostics to that failure. It differentiates BEGIN not acknowledged, DATA interruption (acknowledged bytes only), and END not acknowledged. A failed BEGIN does not prove that the device left its previous image intact: it may have started erasing before its response was lost. A fresh explicit update clears the retained failure; the app never retries a failed upload automatically. The actual service tests cover these three failures on SDK 28 and 35 and ensure 100% progress without commit acknowledgement is not reported as success.

The custom CPU1 OTA bootloader must first be installed together with the application by ST-Link, following the firmware repository's `docs/OTA.md`. The original firmware alone cannot receive this update. OTA never installs the bootloader, CPU2 BLE stack or FUS.

1. Build and package the firmware. Copy the matching `build/application/smartglasses.bin` and `smartglasses.json` to the phone or an accessible document provider.
2. Connect to the glasses. In **Firmware aktualisieren**, select the BIN and then the matching JSON. Reading and validation run off the UI thread. The app checks target STM32WB35CE, format/profile, application address, size (320–196608 bytes), stack/reset vectors, CRC32, SHA-256 and the supported version.
3. Start the update and acknowledge the one-slot replacement dialog. On the glasses, approve `OTA?` with pad 1 held for one second. In an already running recovery bootloader, the transfer starts without this application-mode reboot step.
4. Normal texts and time writes pause. The client reconnects, verifies bootloader mode, sends `SGU1` with size and CRC, then offset-prefixed chunks of up to 16 bytes, each waiting for its write acknowledgement. BEGIN allows a bounded 15-second erase response; DATA allows five seconds; END allows ten seconds. A bounded wake lock keeps the phone CPU awake during transfer.
5. The glasses verify and commit the image before acknowledging `END1`. The app then reconnects and reads the application version/mode before reporting success. A percentage of 100 alone does not mean final validation succeeded.

There is one application slot. After interruption the previous application may no longer be available, but the bootloader remains reachable for a full new upload. Upload failure/cancellation never automatically restarts flashing; select **Update starten** again after reconnect. Keep the glasses powered. CRC/SHA verify integrity, not the publisher's identity: there is no firmware signature or anti-rollback mechanism. Use trusted build packages. At present the supported protocol/package version is 0.2.0. New incompatible firmware versions require corresponding client updates.

## Build, tests and signing

Use JDK 21, Android SDK platform 36, Build Tools 35.0.0 and the checked-in Gradle 8.11.1 wrapper. AGP is pinned to 8.10.1; test dependencies are pinned. Production UI uses native Android components and has no external library dependency.

From `test/`:

```text
gradlew testDebugUnitTest lintDebug assembleDebug --no-daemon
```

Lint warnings fail the build. The pinned AGP/Gradle pair supports SDK 36; release-currency notices remain visible as informational messages rather than depending on changing remote release metadata to pass a build. No correctness/security warnings are baselined. Tests exercise message overflow/expiry and late completion, Unicode bounds, deduplication, package/vector/digest rejection, OTA wire layout, acknowledged queue advancement, erase timeout and cancellation. Robolectric runs notification extraction, permission handling and activity/service lifecycle checks on SDK 28 and 35. Controlled ATT replies exercise the actual service's full OTA transfer, post-reboot verification, interruption, stale callbacks, cancellation, UI unbinding and retry exhaustion. These are Android framework/radio simulations, not physical radio tests. The existing standalone `tests/GattQueueTest.java` continues to cover retry bounds, pairing timeout and stale timers.

GitHub Actions installs the declared SDK, runs these checks and publishes the debug APK plus test/lint reports. Debug APKs use development signing and are not a dependable release-update identity across different build machines. Release signing is supported through all four environment variables:

```text
SMARTGLASSES_KEYSTORE
SMARTGLASSES_STORE_PASSWORD
SMARTGLASSES_KEY_ALIAS
SMARTGLASSES_KEY_PASSWORD
```

`SMARTGLASSES_KEYSTORE` points to the owner's keystore file; the other values are its credentials. Keep the private file and passwords out of Git and use the same owner key for successive releases. `gradlew assembleRelease` produces an unsigned APK when no complete signing configuration is supplied. A package signed with a different key cannot update an already installed app; uninstalling would reset app settings and notification access.

## Physical acceptance checklist

- Fresh install on Android 11 and 12+, grant/deny/revoke permissions and toggle Bluetooth/location switches.
- Correct and rejected number comparison; pairing removal and stale bonds after firmware replacement.
- Send plain text, styled text, multiline/messaging notifications and a burst of more than five full messages; verify counters and display behavior.
- Close/rotate the controls, turn the screen off, leave radio range, return, restart the glasses and restart the app process.
- Verify blocked apps and forwarding off; a separate app must not receive or inject text via the former custom broadcast action.
- Install OTA prerequisites on the actual WB35CE, perform a valid update, cancel/retry an upload and confirm recovery after a deliberately interrupted transfer.
- Confirm the exact display and touch-pad behavior on the photographed V1 board. Build/tests do not establish hardware compatibility by themselves.

## Review remediation map

| Review finding | Resolution |
| --- | --- |
| A01 permissions | Version-specific runtime flow, denial/settings path, revocation handling and legacy location-switch check. |
| A02 broadcasts | In-process delivery and listener observers; services remain non-exported. Only protected Bluetooth system broadcasts are registered. |
| A03 lifecycle/reconnect | Started foreground service owns all connection/message/time state; unbinding the UI leaves it running; bounded backoff. |
| A04 property mask | Complete read/write property checks; arbitrary GATT selection removed. |
| A05 tasks/bitmap load | No bitmap loop or Activity timer; scan callbacks and service tasks are cancelled at their owners' end. |
| A06 scan duration | One cancellable ten-second scan with failure handling. |
| A07 discovery delay | Display on first result, independent of RSSI history. |
| A08 text extraction | CharSequence and supported style fallbacks; notification update deduplication. |
| A09 lost messages | Bounded message-level outbox, one batch at a time, TTL and loss counters. |
| A10 manual channels | Fixed text, diagnostic and OTA functions with readiness checks. |
| A11 device validation | Exact service/channel UUIDs, required properties and version/mode validation. |
| Ortungsprototype | Removed from the companion; no false position estimates. |
| Lint/tests/tooling | Current SDK baseline, pinned tools/tests, stale resources removed and strict CI checks. |
| Android OTA | Validated package selection, physical approval, acknowledged transfer, recovery and post-reboot verification. |
