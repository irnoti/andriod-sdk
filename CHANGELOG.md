# Changelog

## Unreleased
- Docs: `IrnotiProfile.birthday` also takes the year (`YYYY/MM/DD`, e.g. `1370/06/31`); `MM/DD` still works. No code change.

## 1.1.0
- `IrnotiProfile` and `Irnoti.identify(externalId, aliases, profile)`: send the user's contact details (phone, name, last name, e-mail, city, company, note, Solar Hijri birthday `MM/DD`, extra phonebook groups, custom fields). The person is filed in the app owner's IRNoti phonebook and the device is tied to them, so the owner can also reach them by SMS.
- `Irnoti.setProfile(profile)`: the same without an external id.
- `sdk_version` now reports `android/1.1.0` (it said `1.0.0` in 1.0.1/1.0.2).

## 1.0.1
- Build: add the Gradle wrapper (`gradlew`, `gradle/wrapper`, Gradle 8.9). JitPack could not build 1.0.0 because `jitpack.yml` calls `./gradlew`, which was not in the repository. No code changes.

## 1.0.0
- Initial release: device registration, FCM token updates, Topics, Tags, External ID and Aliases, events, notification display (image, two action buttons, deep links) and delivery/click reporting with a persistent queue.
