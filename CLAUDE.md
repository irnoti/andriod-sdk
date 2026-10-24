# CLAUDE.md — IRNoti Android SDK

Kotlin library (`irnoti-sdk/`) for the IRNoti push service; published through JitPack from git tags (`vX.Y.Z`). Wire protocol:
the IRNoti device API (`/v1/sdk`, documented in the SDK repo docs and LOGIC §98 of `dadehnamair/irnoti`). Talk to the owner in
Persian; code, comments and docs in English.

## Rules
- **Dates (owner rule, every project):** stored Gregorian, entered and shown Solar Hijri. The SDK stores nothing in a database,
  but any date it sends or documents follows the server contract: Solar Hijri strings at the edges where the API takes them
  (e.g. `IrnotiProfile.birthday` = `YYYY/MM/DD` Jalali), never a Jalali date persisted as such.
- Public API stays source-compatible: new parameters get defaults and `@JvmOverloads`; bump `VERSION` and `CHANGELOG.md` per release.
- No direct Google calls besides the FCM token; everything else goes to the IRNoti API.
