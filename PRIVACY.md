# GestureControl Privacy Statement

**Last updated: 23 November 2026**

GestureControl is a general-purpose Android gesture-control utility. It uses the front camera to recognise hand poses and movement so that a user can control basic screen navigation.

## Camera processing

The app requests camera access only after the user chooses to start gesture detection. Camera frames are processed on the device by the MediaPipe Hand Landmarker model to detect hand landmarks. The app does not intentionally save camera frames, record video, or transmit camera frames or hand landmarks to a server.

The app does not request the Android INTERNET or ACCESS_NETWORK_STATE permissions.

## Accessibility service

GestureControl asks the user to enable its Android Accessibility service only after showing a separate in-app disclosure and receiving affirmative consent.

The service is used only to:
- Dispatch a swipe or scroll gesture after the app recognises a corresponding hand movement.
- Perform the Android Home action after an open-palm hold.
- Perform the Android Back action after a fist hold.

The service does not request access to window contents, screen text, notifications, keystrokes or screenshots. It does not request or process accessibility events. It does not autonomously plan or initiate actions; system actions are triggered by a recognised hand gesture made by the user.

The user can stop detection from the app or the ongoing notification, withdraw in-app consent, and disable the Accessibility service in Android Settings.

## Data collection and sharing

GestureControl does not include an account, advertising SDK, analytics SDK or app-authored network service. The application does not intentionally collect or share personal data with the developer or third parties. Android and device manufacturers may independently process diagnostic or security information under their own system policies.

## Permissions

- **Camera:** hand landmark detection while gesture control is running.
- **Foreground service (camera):** keeps the camera session visible to the user through an ongoing Android notification.

## Children's privacy

GestureControl is not designed specifically for children and does not knowingly collect personal data from children.

## Changes and contact

This statement may be updated when app functionality changes. For questions about this app or this privacy statement, contact the repository maintainer through the public GitHub repository:

https://github.com/prudraraj-sketch/Jarvis-
