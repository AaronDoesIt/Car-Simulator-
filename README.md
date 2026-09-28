# Car Simulator

An Android crash-test game. Pick an American vehicle, steer it down a drag
strip with your finger, hit the blue boost strip, and watch what the
obstacles do to it. Every level launches the car faster and swaps the
obstacle course.

Kotlin, Jetpack Compose, OpenGL ES 2, no third-party engine. The world and
most vehicles are generated in code. Vehicles can also be imported from an
AI-generated GLB through `scripts/import_vehicle_glb.py`, which aligns the
model to the physics frame, cuts out its baked wheels and writes a compact
mesh plus albedo into `app/src/main/assets/vehicles/`; the 2001 S-10 is the
first one.

## How a run plays

1. **Garage.** Choose a vehicle. The header shows the level, its launch speed
   and which course is waiting.
2. **Launch.** Camera sits above and behind the car. Drag left or right
   anywhere on the screen to steer; lift your finger and the wheel centres.
   The car drives itself onto the blue strip, which sets its speed to the
   level target.
3. **Impact.** The moment the car reaches the obstacles (or anything hits the
   ground) the camera cuts to a trackside view, pulls back in proportion to
   speed, and the sim drops into slow motion for the first moments.
4. **Results.** Score from body damage, wheels torn off, air time, rollovers
   and the hardest hit. Continue to the next level, retry, or go back to the
   garage.

Levels start at 45 mph and add 25 mph each round, so level 20 is 520 mph.
Nine courses rotate. Six are bolted onto the road: speed-bump grid, monster
bump, launch ramp, loading dock, crater field, staircase. Three are the road
itself: a crest jump that ends in a cliff, a rollercoaster of hills, and a
canyon drop. Each gets taller or deeper as the level number rises, and the
landscape either side of the strip is always hills growing into mountains.

## Garage

| Vehicle | Curb weight | Wheelbase | Notes |
|---|---|---|---|
| 2001 Chevy S-10 (lifted) | 3,040 lb | 108.3 in | Reg cab, 6" lift, 31" tyres |
| 2001 Chevy Suburban (lifted) | 4,914 lb | 130 in | 1500, 6" lift |
| 2020 Ford F-150 SuperCrew | 4,630 lb | 145 in | |
| 2019 Ram 1500 Crew Cab | 5,070 lb | 144.5 in | |
| 1998 Hummer H1 | 7,050 lb | 130 in | |
| 2016 Chevy Corvette Stingray | 3,300 lb | 106.7 in | |
| 2018 Ford Mustang GT | 3,790 lb | 107.1 in | |
| 2019 Dodge Charger R/T | 4,250 lb | 120 in | |
| 2020 Jeep Wrangler | 3,970 lb | 96.8 in | |
| 2021 Cadillac Escalade | 5,950 lb | 120.9 in | |

Weights and dimensions come from manufacturer spec sheets as republished by
Edmunds, Cars.com, The Car Connection and automobile-catalog.com. Each entry
in `VehicleCatalog.kt` records its source. Suspension rates are derived from
class-typical ride frequencies; lifted trucks get a taller centre of gravity,
more travel and softer springs.

## Physics

The simulation lives under `app/src/main/java/com/carsimulator/app/sim/` and
has no Android dependencies, so it runs in plain JVM unit tests.

- Six-degree-of-freedom rigid chassis with a box inertia tensor.
- Four raycast wheels with spring-damper suspension, bump stops, and tyre
  forces (linear cornering stiffness with a smooth grip limit, friction
  circle, rolling resistance).
- Fourteen chassis contact points against the road heightfield with penalty
  forces and friction, so the body can land on its roof, bumper or side.
- Wheels tear off when a corner's load exceeds a multiple of vehicle weight
  and then fly as free bodies.
- Six-panel damage model driven by impact energy and angle. A square hit
  crumples; a glancing one scrapes and delivers a quarter of the crush. The
  panel facing the surface takes the hit, so a nose-first wall strike folds
  the front and a side swipe folds the door. The results screen reports the
  angle of the hardest hit.
- The road has its own height profile (crests, canyons, rollercoasters) and
  the land beside it is deterministic noise terrain up to mountain height.
- Sub-stepping keeps wheel samples under 8 cm apart even at 500 mph.
- Aerodynamic drag is scaled to 35 % of real so the boost speed survives to
  the obstacles; everything else uses real units.

## Graphics

Everything is generated: a gradient sky, hemisphere ambient plus a sun,
distance fog, a terrain mesh that colours grass, rock and snow by altitude
and slope, a forest of conifers and boulders scattered by hash, a soft
shadow under the car that fades as it flies, and bodies built from tapered
hulls with glass, wheel arches, mirrors, grille, lights, bumpers, bed rails,
roof racks and spoilers according to body style. The body mesh crumples with
damage and wheels spin, steer and tumble off.

## Build

```sh
./gradlew assembleDebug
./gradlew test
```

Requires Android Studio (or command-line tools) with SDK platform 35 and
JDK 17 or newer.

## Layout

| Path | Purpose |
|---|---|
| `sim/` | Vectors, quaternions, rigid body, track heightfield, vehicle sim, damage, catalog |
| `game/` | Level catalog, camera director, run state machine and scoring |
| `render/` | Mesh builders, shader, GL renderer |
| `ui/` | Garage, run (GL view + drag steering + HUD) and results screens |
| `app/src/test/` | JVM tests for sim, game and mesh builders |

## Release signing

Create `keystore.properties` in the project root (ignored by git) with
`storeFile`, `storePassword`, `keyAlias` and `keyPassword`. The release build
picks it up automatically.
