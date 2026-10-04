# PhoneGuard

Native Android security, privacy, storage and device-health diagnostics.

## What is live in 0.2.0

- Full-device scan orchestration with staged progress.
- Installed-app permission/risk inventory using Android PackageManager.
- Privacy/security checks for usage access, VPN transport, overlay access, accessibility services and device administrators.
- Android security-patch and build-integrity signals.
- Shared-storage inventory through MediaStore.
- Large-file and suspicious-extension detection.
- SHA-256 hashing for bounded duplicate analysis.
- Confirmation-gated duplicate deletion through Android's system media-delete confirmation on supported Android versions.
- Battery, storage and memory diagnostics.
- Android Settings deep links for security, overlay, accessibility and storage review.
- Clear coverage messaging for protected/private Android areas that an ordinary app cannot inspect.
- GitHub Actions build producing a debug APK and release AAB artifact.

## Important limits

PhoneGuard is a diagnostic/security assistant, not a rootkit or a kernel antivirus. It does not bypass Android sandboxing, root-only locations, verified-boot partitions, or another app's private data. App risk labels are heuristics based on requested capabilities and device state; they are not proof that an app is malicious.

MediaStore coverage depends on Android version and the permissions granted by the user. A future deep-scan path can use the Storage Access Framework for a user-selected folder.

## Build

The repository CI uses JDK 17 and Gradle 8.13.

- gradle assembleDebug
- gradle bundleRelease
- gradle lint

The debug APK and release AAB are uploaded as the phoneguard-android-build GitHub Actions artifact.

## Package visibility

PhoneGuard currently declares QUERY_ALL_PACKAGES because comprehensive installed-app security inspection is a core security/antivirus use case. If this app is distributed through Google Play, the permission is subject to Play policy and approval; review the current policy before publishing.

## Privacy

Scans run locally on the device. PhoneGuard does not upload personal files as part of the local scan flow.
