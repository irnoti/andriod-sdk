# IRNoti Android SDK

[![JitPack](https://jitpack.io/v/irnoti/andriod-sdk.svg)](https://jitpack.io/#irnoti/andriod-sdk)

The official IRNoti (Iran Notification) SDK for Android apps. Use it to register devices, receive and display Firebase notifications, segment users with Topics, Tags, External ID and Aliases, send behavioral events, and report delivery and click statistics.

Current version: **1.1.0**

## Table of contents

- [Features](#features)
- [Requirements](#requirements)
- [Quick start](#quick-start)
- [Installation](#installation)
- [Connecting Firebase](#connecting-firebase)
- [Initializing the SDK](#initializing-the-sdk)
- [Notification permission on Android 13+](#notification-permission-on-android-13-and-above)
- [Delivery without Firebase (direct transport)](#delivery-without-firebase-direct-transport)
- [Sending your first notification](#sending-your-first-notification)
- [Public methods](#public-methods)
- [Topics](#topics)
- [External ID and Aliases](#external-id-and-aliases)
- [Tags](#tags)
- [Events](#events)
- [Links, deep links and action buttons](#links-deep-links-and-action-buttons)
- [If your app already has a Firebase service](#if-your-app-already-has-a-firebase-service)
- [Using the SDK from Java](#using-the-sdk-from-java)
- [Troubleshooting](#troubleshooting)
- [Security](#security)
- [License](#license)

## Features

| Feature | Description |
|---|---|
| Automatic device registration | Fetches the FCM token, registers it with IRNoti and updates it automatically when the token changes |
| Works without Firebase | Devices that cannot reach FCM receive the same notifications through IRNoti's own channel, automatically |
| Notification display | Title, body, large image and icon, in both foreground and background states |
| Action buttons | Up to two action buttons per notification |
| Links and deep links | Web links and deep links (custom scheme or App Link) |
| Topics | Subscribe, unsubscribe and list the device's topics |
| External ID and Aliases | Link a device to your own user ID plus several labeled identifiers such as `phone` |
| Tags | Simple labels for segmentation (for example `plan=pro`) |
| Events | Send behavioral events with parameters |
| Reporting | Delivery, click, dismiss and button reports, backed by a persistent queue with automatic retry |
| Kotlin and Java | Public API usable from both languages |

## Requirements

1. An **organization** and a **project** in IRNoti with the "Push Notification" service enabled.
2. An **app** in that service (created with your Firebase service-account JSON file). After creating it, copy the app's **public key** (`pk_live_…`) from the dashboard.
3. A Firebase project with an Android app whose package name exactly matches your app, and that Android app's `google-services.json` file. (Optional if you use `Transport.DIRECT` only.)
4. Android 6.0 or higher (`minSdk 23`) and `compileSdk 35` or higher.

> [!IMPORTANT]
> The Firebase project of your app and the Firebase project whose service account you registered in IRNoti must be the same project.

> [!CAUTION]
> Only put the **public key** (`pk_live_…`) inside the app. The Firebase service-account file and the broker link are secret and must never be included in the APK, the app source or GitHub.

## Quick start

1. Add the dependency from JitPack.
2. Put `google-services.json` in the `app/` folder and enable the Google Services plugin.
3. Call `Irnoti.init()` with your public key in `Application.onCreate()`.
4. On Android 13+, request the notification permission.
5. Run the app on a real device and send a test message from the IRNoti dashboard.

## Installation

In `settings.gradle.kts`:

```kotlin
dependencyResolutionManagement {
    repositories {
        google()
        mavenCentral()
        maven { url = uri("https://jitpack.io") }
    }
}
```

In `app/build.gradle.kts`:

```kotlin
dependencies {
    implementation("com.github.irnoti:andriod-sdk:1.1.0")
}
```

> The repository is named `andriod-sdk` (with this exact spelling) and the artifact uses the same name.

## Connecting Firebase

If your app already uses Firebase, this step is already done. Otherwise:

```kotlin
// build.gradle.kts (root)
plugins { id("com.google.gms.google-services") version "4.4.2" apply false }

// app/build.gradle.kts
plugins { id("com.google.gms.google-services") }
```

## Initializing the SDK

In your `Application` class (and reference it in `AndroidManifest.xml`):

```kotlin
class App : Application() {
    override fun onCreate() {
        super.onCreate()
        Irnoti.init(
            this,
            IrnotiConfig(
                appKey = "pk_live_xxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx",
                smallIcon = R.drawable.ic_stat_notify, // monochrome icon
            ),
        )
    }
}
```

`init` is only needed once at process start. Device registration runs in the background, and a network error will not crash the app; registration is retried the next time the app starts.

## Notification permission on Android 13 and above

Request the `POST_NOTIFICATIONS` permission from the user yourself (from inside an Activity) and report the result to the SDK:

```kotlin
private val askPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
    Irnoti.setNotificationsEnabled(granted)
}

fun requestNotificationPermission() {
    if (Build.VERSION.SDK_INT >= 33) askPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
}
```

## Delivery without Firebase (direct transport)

FCM keeps its own connection to `mtalk.google.com:5228`. On networks that block it (for example in Iran without a VPN) FCM notifications never arrive. The SDK handles this for you:

| `transport` | Behavior |
|---|---|
| `Transport.AUTO` (default) | Uses FCM when the device can reach Google's push servers, otherwise IRNoti's own channel. The check is repeated every 30 minutes and on every app start, so a device moves back to FCM as soon as it can. |
| `Transport.FCM` | FCM only (SDK 1.0 behavior). |
| `Transport.DIRECT` | IRNoti's own channel only. Firebase is never used and `google-services.json` is not needed. |

```kotlin
IrnotiConfig(
    appKey = "pk_live_xxx",
    smallIcon = R.drawable.ic_stat_notify,
    transport = Transport.AUTO,          // default
    directService = true,                // default: instant delivery through a foreground service
    directNotificationTitle = "اتصال برای دریافت اعلان‌ها",
    directNotificationText = "برنامه آماده‌ی دریافت اعلان است",
)
```

How it works:

1. A device on the direct transport holds one HTTPS request (`GET /devices/{id}/stream`, Server-Sent Events) open. IRNoti sends each message through it and pings every 25 seconds; the SDK reconnects with exponential backoff.
2. Messages wait in a queue on the server until the device acknowledges them (the normal `delivered` report) or their `ttlSeconds` expires, so a device that was offline gets them when it reconnects. A message is shown once even if the server resends it.
3. With `directService = true` the connection lives in a foreground service, which Android requires to show one minimal, silent notification (title and text above). Without it the connection only exists while the app process does; a periodic catch-up job (about every 15 minutes) and a boot receiver cover the rest.

Things to know:

- Devices on the direct transport are Android only; iOS and web always use APNs / FCM.
- Some manufacturers (Xiaomi, Huawei, Samsung, Oppo…) kill background services aggressively. Ask users to exclude the app from battery optimization and allow auto-start if delivery is delayed.
- If the app is in the background when the service must start (Android 12+), Android may refuse; the SDK then falls back to the catch-up job until the app is opened once.
- The library manifest adds `FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_SPECIAL_USE` and `RECEIVE_BOOT_COMPLETED`. Apps published on Google Play must declare the special-use foreground service in the Play Console; set `directService = false` to avoid it.
- `Irnoti.currentTransport()` returns `"fcm"`, `"direct"` or `null` (not registered yet).

## Sending your first notification

From the IRNoti dashboard (the project's Push section), or from your own server using the project API key:

```bash
curl -X POST "https://api.irnoti.ir/v1/sdk-apps/APP_ID/send" \
  -H "Authorization: Bearer irn_live_xxx" \
  -H "Content-Type: application/json" \
  -d '{
    "message": { "title": "Hello", "body": "Your first notification", "url": "https://example.com" },
    "target": { "all": true }
  }'
```

## Public methods

| Method | Description |
|---|---|
| `Irnoti.init(context, config)` | Initialize the SDK and register the device |
| `Irnoti.setNotificationsEnabled(Boolean)` | Tell the SDK whether the notification permission is granted |
| `Irnoti.identify(externalId, aliases)` | Link the device to your user |
| `Irnoti.logout()` | Detach the user from the device |
| `Irnoti.subscribe(topic)` / `unsubscribe(topic)` | Subscribe to or unsubscribe from a topic |
| `Irnoti.setTags(map)` | Set tags, or remove one by passing a `null` value |
| `Irnoti.track(name, props)` | Send an event |
| `Irnoti.unregister()` | Fully unregister the device |

All methods are asynchronous and safe to call before the device is registered (calls are queued).

## Topics

```kotlin
Irnoti.subscribe("news")
Irnoti.unsubscribe("news")
```

Topic names may contain English letters, digits and `_ . -`, up to 64 characters. Each device can join at most 100 topics.

## External ID and Aliases

After the user signs in:

```kotlin
Irnoti.identify("user-123", mapOf("phone" to "09120000000", "tier" to "gold"))
// When the user signs out:
Irnoti.logout()
```

## Tags

```kotlin
Irnoti.setTags(mapOf("plan" to "pro", "city" to "tehran", "old_tag" to null)) // null = remove
```

Up to 50 tags; keys up to 40 characters and values up to 256 characters.

## Events

```kotlin
Irnoti.track("purchase", mapOf("amount" to 120000, "currency" to "IRR"))
```

The parameters (`props`) can be at most 4 KB.

## Links, deep links and action buttons

Message fields (when sending from the dashboard or the API):

| Field | Description |
|---|---|
| `title`, `body` | Title and body text |
| `image` | `https` URL of the large image |
| `url` | Web link opened when the notification is tapped |
| `deeplink` | Absolute URI with your app's scheme (for example `myapp://orders/5`); takes priority over `url` |
| `actions` | Up to two buttons: `[{"id":"open","title":"View","url":"myapp://orders/5"}]` |
| `channel` | Android notification channel ID |
| `extra` | Custom data as string key-value pairs |

For deep links, declare an intent filter for your scheme on the destination Activity.

## If your app already has a Firebase service

The SDK's service is registered automatically in the library manifest. If you already have your own `FirebaseMessagingService`, declare your service and add these calls:

```kotlin
class MyMessagingService : FirebaseMessagingService() {
    override fun onNewToken(token: String) {
        Irnoti.onNewToken(token)
        // your code
    }

    override fun onMessageReceived(message: RemoteMessage) {
        if (Irnoti.isIrnotiMessage(message.data)) Irnoti.handleMessage(this, message.data)
        else { /* your own messages */ }
    }
}
```

## Using the SDK from Java

```java
Irnoti.init(this, new IrnotiConfig("pk_live_xxx", R.drawable.ic_stat_notify));
Irnoti.identify("user-123");
Irnoti.subscribe("news");
```

## Troubleshooting

| Problem | Cause and solution |
|---|---|
| The device does not appear in the dashboard | Check the public key; the app must be created in the project's active service. Check internet access and that `api.irnoti.ir` is reachable. |
| Registration fails with a 403 error | The project's push service is disabled or expired, or the device limit of the current plan has been reached. Check the status in the dashboard. |
| Notifications are not delivered on some networks | FCM is blocked there. Leave `transport` on `AUTO` (default) so the device switches to the direct channel; check `Irnoti.currentTransport()`. |
| Notifications are not delivered | The app's Firebase project and the registered service account must be the same project. Check the notification permission (Android 13+) and Battery Optimization. |
| Notification is not shown although sending succeeded | `smallIcon` must be a valid monochrome drawable. |
| A link or deep link does not open | The SDK records the click through an intermediate Activity and then opens the link. Make sure the scheme is declared with an intent filter on a destination Activity. |

## Security

- Only the public key goes inside the app. The device token (`dt_…`) is stored in the app's private storage. For sensitive apps, set `android:allowBackup="false"`.
- All communication uses `https` only.

## License

The license is determined by the repository owner (see the `LICENSE` file).
