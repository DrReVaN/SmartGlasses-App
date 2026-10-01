# Android remediation verification — 2026-09-30

All eleven findings A01–A11 from the Android review have been addressed; the implementation map and remaining physical acceptance checks are in [ANDROID.md](ANDROID.md). The old uncalibrated location prototype is removed. The Android companion now includes the custom WB35CE CPU1 OTA client.

## Executed checks

- JDK 21, SDK 36, AGP 8.10.1 and regenerated Gradle 8.11.1 wrapper with distribution SHA-256 verification.
- `:app:assembleDebug`, `:app:assembleRelease`, `:app:lintDebug`, `:app:lintRelease`, `:app:testDebugUnitTest`: successful.
- 49 JUnit test executions, no failures/errors/skips: 19 core tests, 12 Android notification/permission/lifecycle tests and 18 OTA service tests. Android-dependent tests run on simulated SDK 28 and 35.
- The existing standalone protocol/GATT queue regression suite passes.
- The Android package validator accepts the actual firmware development build: STM32WB35CE application, 37804 bytes, 2363 offset-prefixed ATT chunks, SHA-256 `4fe516e6360d4d394f0b596d5d6276a655b4f03749914edcd574d65f97af56c1`.
- Debug APK builds with development signing; release APK builds unsigned when the owner's signing environment is absent. No owner signing key was provided or created.
- Lint has no errors or warnings. The pinned tool version has an informational release-currency notice, explicitly configured in `test/lint.xml`. Correctness/security checks remain active, and warnings fail the build.

The OTA service tests control ATT responses rather than bypassing the service. They cover BEGIN/erase acknowledgement, every DATA packet and offset, END acknowledgement, application-mode verification after reconnect, interrupted uploads, old-session callbacks, explicit full restart, cancellation, unbinding during upload, exhausted verification retries and rejection of missing full services. At 100 percent, success is withheld until END and the application handshake complete.

## Limits

Version 1.1.1 adds recovery-mode diagnostic reads with the existing protected characteristic. Tests verify the reset/fault values reach the UI without any ATT write, repeated reads cannot overlap, diagnostics cannot interleave an OTA upload, and a bootloader handshake after commit reports recovery rather than update success. Debug/release lint and the debug APK build pass; the local debug APK retains the 1.1.0 certificate so it can replace that installed build. The real-device reason for remaining in recovery still requires the owner's diagnostic values.

No physical Android phone or the photographed glasses were connected for these checks. Real pairing, radio/background behavior, the exact display, pad approval and flash/power-loss recovery still require the acceptance checks in ANDROID.md. OTA requires the prepared bootloader to be installed once by cable; it cannot retrofit itself onto the original firmware. Firmware packages must come from a trusted source: the existing protocol verifies integrity but does not provide signatures or automatic rollback.

## App 1.3.0

Adds automatic GitHub APK checks, explicit download/deferral, signer/package/version verification, the Android installation prompt and the private read-only APK provider. Android versionCode is 7. The existing local development certificate is reused for public APKs; its private keystore remains outside the repository. CI verifies the prepared APK certificate and compares all packaged contents except signing metadata with the CI build before publication. Real system installation remains to be confirmed on the phone.
