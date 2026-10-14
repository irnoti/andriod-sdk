# Changelog

## 1.0.1
- Build: add the Gradle wrapper (`gradlew`, `gradle/wrapper`, Gradle 8.9). JitPack could not build 1.0.0 because `jitpack.yml` calls `./gradlew`, which was not in the repository. No code changes.

## 1.0.0
- Initial release: device registration, FCM token updates, Topics, Tags, External ID and Aliases, events, notification display (image, two action buttons, deep links) and delivery/click reporting with a persistent queue.
