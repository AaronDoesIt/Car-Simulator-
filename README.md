# Car Simulator

An Android car driving simulator written in Kotlin with Jetpack Compose.

## Status

Early scaffold. The app launches into a landscape dashboard with a brake
pedal, an accelerator pedal, a speedometer, an automatic gear indicator and
a rev bar. The vehicle physics live in a pure Kotlin module so they can be
unit tested without an emulator.

## Requirements

- Android Studio Narwhal or newer, or the Android command-line tools
- JDK 17 or newer
- Android SDK platform 35

## Build

```sh
./gradlew assembleDebug
./gradlew test
```

## Layout

| Path | Purpose |
|---|---|
| `app/src/main/java/com/carsimulator/app/sim/` | Vehicle state and physics, no Android dependencies |
| `app/src/main/java/com/carsimulator/app/SimulationViewModel.kt` | Fixed-tick simulation loop that drives the UI |
| `app/src/main/java/com/carsimulator/app/ui/` | Compose screens |
| `app/src/test/` | JVM unit tests for the physics |

## Release signing

Create a `keystore.properties` file in the project root (it is ignored by
git) with `storeFile`, `storePassword`, `keyAlias` and `keyPassword`. The
release build type picks it up automatically.
