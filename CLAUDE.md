# Car Simulator

Android app. Kotlin, Jetpack Compose, single `app` module. Conventions match
the other AaronDoesIt Android apps (TalkBridge is the reference).

## Commands

- Build: `./gradlew assembleDebug`
- Unit tests: `./gradlew test`
- Release: `./gradlew assembleRelease` (needs `keystore.properties`, never committed)

## Rules

- Keep everything under `sim/` free of Android imports so it stays JVM testable.
- Add a unit test in `app/src/test` for every change to the physics.
- Versions of AGP, Kotlin and the Compose BOM are pinned in the Gradle files;
  bump them deliberately, not as a side effect of another change.
- Never commit signing keys or `local.properties`.
