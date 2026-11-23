# GestureControl

GestureControl is a general-purpose Android utility for hands-free screen navigation using recognised hand gestures. It uses the front camera and MediaPipe Hand Landmarker. Recognition is performed on-device.

## Gesture mapping

| Hand gesture | Action |
|---|---|
| Open palm moved quickly left/right/up/down | Sends a screen swipe in that direction |
| Peace sign moved up/down | Sends a vertical scroll gesture |
| Open palm held still for about one second | Android Home action |
| Fist held still for about one second | Android Back action |

The app does not attempt to force-stop another application. Android does not expose a general force-stop API to ordinary applications; using the Back action is more predictable and avoids automatically dismissing arbitrary cards from the Recents screen.

## First-time setup

1. Install and open GestureControl.
2. Read the in-app Accessibility disclosure and explicitly accept it.
3. Open Android Accessibility settings and enable Gesture Control.
4. Return to the app, tap **Start gesture control**, and grant camera permission.
5. A persistent notification appears while the camera service is running. Use its Stop action or the in-app Stop button to stop detection.

Camera access is used only after the user taps Start. Accessibility actions are disabled unless the in-app consent is active. Consent can be withdrawn in the app; the user can also disable the service in Android Settings.

## Privacy and security

- Hand landmark recognition runs locally on the device.
- Camera frames are not intentionally saved, recorded, or transmitted.
- The app requests CAMERA and foreground-service permissions. The manifest removes INTERNET and ACCESS_NETWORK_STATE permissions contributed by transitive libraries.
- The Accessibility service is configured not to retrieve window content and requests no accessibility events. It only dispatches swipe/scroll gestures and Home/Back actions after a recognised hand gesture.
- The app is a general-purpose utility, not an accessibility tool for disability support. It does not set `isAccessibilityTool=true`.
- Read [PRIVACY.md](PRIVACY.md) for the privacy statement.

## Build

The GitHub Actions workflow builds a non-debuggable release APK against Android 16 / API 36, verifies its APK signature, checks that the INTERNET permission is absent, and publishes an APK plus SHA-256 checksum as a 30-day artifact.

1. Open the **Actions** tab.
2. Select **Build GestureControl release APK**.
3. Select **Run workflow** on `main`.
4. Download the APK artifact from the completed run.

### Release signing

For a persistent production signing identity, configure these repository Actions secrets:

- `RELEASE_KEYSTORE_BASE64`: Base64-encoded private release keystore.
- `RELEASE_STORE_PASSWORD`: keystore password.
- `RELEASE_KEY_ALIAS`: signing key alias.
- `RELEASE_KEY_PASSWORD`: signing key password.

Keep the keystore and passwords private and backed up. Never commit them to this repository. When the secrets are not configured, the workflow signs a non-debuggable sideload test build with the GitHub runner's debug key. That fallback is not suitable for Google Play publishing and will not provide a persistent signing identity between builds.

## Google Play submission

Because this is a general-purpose app that uses AccessibilityService, do not mark it as an accessibility tool. The developer must document the exact Accessibility use, complete the relevant Play Console declaration, and keep the in-app disclosure and consent flow. Google Play approval and Google Play Protect's device-level verdict are determined by Google; a successful CI build is not a guarantee of either.

## Development notes

- Minimum Android version: API 26.
- Target Android version: API 36.
- Java: 17.
- Gradle: 8.11.1.
- Android Gradle Plugin: 8.9.2.
- Hand model download is pinned by SHA-256 validation in `download_models.sh`.

Test the APK on the intended phone for camera orientation, gesture recognition, system navigation, thermal behaviour and battery consumption before relying on it.
