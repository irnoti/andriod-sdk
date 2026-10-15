# Changelog

## 1.1.0
- **Direct transport** for devices that cannot reach Firebase Cloud Messaging (typical in Iran without a VPN). The SDK keeps its own HTTPS stream (Server-Sent Events) to IRNoti, receives pushes through it, and acknowledges them with the normal delivery report. Nothing changes for the app's code.
- New `IrnotiConfig.transport`: `AUTO` (default: FCM when the device can reach it, direct otherwise, re-checked every 30 minutes), `FCM` (the 1.0 behavior) or `DIRECT` (Firebase is never touched, so `google-services.json` becomes optional).
- New `IrnotiConfig.directService` (default `true`): the live channel runs in a foreground service with one minimal silent notification. A 15-minute catch-up job and a boot receiver cover the cases where the service is not running.
- `IrnotiConfig` can now be built from Java with only the required arguments.
- New permissions in the library manifest: `FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_SPECIAL_USE`, `RECEIVE_BOOT_COMPLETED`.
- New `Irnoti.currentTransport()`.
- Requires an IRNoti server with the direct transport (`push_provider: "direct"`, `GET /devices/{id}/stream`).

## 1.0.1
- Build: add the Gradle wrapper (`gradlew`, `gradle/wrapper`, Gradle 8.9). JitPack could not build 1.0.0 because `jitpack.yml` calls `./gradlew`, which was not in the repository. No code changes.

## 1.0.0
- Initial release: device registration, FCM token updates, Topics, Tags, External ID and Aliases, events, notification display (image, two action buttons, deep links) and delivery/click reporting with a persistent queue.
