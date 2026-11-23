# GestureControl

Android gesture-control application using the front camera, MediaPipe hand and face detection, and Android Accessibility actions. Camera frames are processed on-device. Model files are downloaded at build time and packaged in the APK.

## Build with GitHub Actions

1. Open the **Actions** tab.
2. Select **Build GestureControl APK**.
3. Select **Run workflow** on the `main` branch.
4. When the run succeeds, download the APK artifact from the run summary.

The artifact includes the debug APK and its SHA-256 checksum. It is retained for 30 days.

## Local build

Requirements: JDK 17 and Gradle 8.9.

```bash
bash download_models.sh
gradle assembleDebug --no-daemon
```

APK output: `app/build/outputs/apk/debug/app-debug.apk`.

## First launch

Grant camera permission and enable the app's Accessibility service in Android settings. The front camera is used while the foreground service is active. Gesture actions depend on Android accessibility support and may behave differently across device manufacturers.

## Gesture controls

- Open palm + quick movement: swipe left, right, up, or down.
- Peace sign + vertical movement: scroll.
- Open palm held still for one second: Home.
- Fist held still for one second: opens Recents and attempts to dismiss the top app card.

Android does not provide ordinary apps with a direct "force close current app" API. The fist action therefore uses the Recents screen and a swipe gesture; this behavior must be tested on the target device.

## Project validation

This is a debug-build configuration. It has not yet been validated on a physical Android device. Test camera startup, gesture recognition, accessibility actions, battery use, and Android-version compatibility before relying on it.
