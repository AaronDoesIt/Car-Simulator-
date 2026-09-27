# Car Simulator

Android crash-test game. Kotlin, Jetpack Compose, OpenGL ES 2, single `app`
module, no game engine. Conventions match the other AaronDoesIt Android apps
(TalkBridge is the reference for Gradle setup and signing).

## Commands

- Build: `./gradlew assembleDebug`
- Unit tests: `./gradlew test`
- Release: `./gradlew assembleRelease` (needs `keystore.properties`, never committed)

## Architecture

- `sim/` is pure Kotlin with no Android imports. Everything physical lives
  there: `SimVehicle` (raycast wheels, chassis contacts, boost, detachment),
  `Track` (heightfield of `Obstacle`s on a `RoadProfile` with noise `Terrain`
  beside it), `DamageModel` (angle-aware), `VehicleCatalog`.
- `game/` is also pure Kotlin: `LevelCatalog` (speed and course per level),
  `GameSession` (LAUNCH → IMPACT → RESULTS, slow motion, scoring),
  `CameraDirector` (chase view, cut to side view on impact).
- `render/` has `MeshData` and the mesh builders `VehicleMeshBuilder`,
  `TrackMeshBuilder`, `SceneryBuilder` (pure Kotlin, testable) plus `GlMesh`,
  `ShaderProgram` and `GameRenderer` (Android GL).
- `ui/` is Compose. `RunScreen` hosts a `GLSurfaceView` and maps finger drag
  to steering. The sim is stepped on the GL thread inside `onDrawFrame`.

Body frame: X forward, Y up, Z right, centre of gravity at the origin.
World: the strip runs along +X.

## Rules

- Keep `sim/`, `game/` and the mesh builders free of Android imports so the
  JVM tests keep working. Android-only GL code stays in `GlMesh`,
  `ShaderProgram` and `GameRenderer`.
- Every physics or level change gets a unit test. The existing tests check
  ride height, straight-line stability, boost, steering direction, obstacle
  carnage and numerical sanity at 400 mph; extend them rather than loosening.
- New vehicles go in `VehicleCatalog` with real curb weight and dimensions and
  a `sourceNote`. Do not invent numbers.
- Versions of AGP, Kotlin and the Compose BOM are pinned in the Gradle files;
  bump them deliberately.
- Never commit signing keys or `local.properties`.
