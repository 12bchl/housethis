# HouseThis

Phone app: photograph rooms/exterior, stamp each photo with motion-sensor data, detect straight lines + corners.
End goal: dimensioned wireframe of a house from photos + a couple reference measurements.

Status: Android capture + feature detection working (builds, unit-tested; not yet run on a device). iOS not started.

## Layout

Kotlin Multiplatform. Shared logic in pure Kotlin, native UI/camera/sensors per platform.

```
shared/        KMP library (targets: android, jvm, iosArm64, iosSimulatorArm64)
  commonMain/  app.housethis.core
    vision/    GrayImage, filters, LineSegmentDetector (LSD), CornerDetector (Shi-Tomasi),
               JunctionDetector (L/T line junctions), FeatureExtractor
    capture/   CaptureRecord + MotionStamp + CameraIntrinsics (JSON model), Attitude (gravity -> image)
  commonTest/  runs on JVM: ./gradlew :shared:jvmTest
androidApp/    Compose + CameraX + SensorManager
iosApp/        (future) SwiftUI + AVFoundation + CoreMotion, links shared as HouseThisCore.framework
```

Why KMP: reconstruction math (vanishing points, calibration, multi-view geometry) is the hard part and must be written once.
Camera + sensor APIs differ too much per platform to share; those stay native.
Vision is pure Kotlin (no OpenCV) -> same code + tests on every target. Swap in OpenCV/native later only if profiling demands it.

## Build

Requires JDK 21 (exact: Gradle toolchain, no auto-download; Debian: `openjdk-21-jdk-headless`), Android SDK via `ANDROID_HOME` (no `sdk.dir` in `local.properties`: path differs per machine, `~` not expanded).

```
./gradlew :shared:jvmTest              # core unit tests
./gradlew :androidApp:assembleDebug    # androidApp/build/outputs/apk/debug/androidApp-debug.apk
./gradlew :androidApp:lintDebug
adb install -r androidApp/build/outputs/apk/debug/androidApp-debug.apk
```

iOS framework targets only build on macOS (`./gradlew :shared:linkDebugFrameworkIosArm64`); skipped elsewhere.

## Android app

- Camera screen: preview, live pitch/roll readout, horizon line (green within 1 deg of level), shutter.
  UI is portrait-locked; capture rotation follows physical orientation (landscape shots come out upright).
- Captures screen: thumbnails with line/junction counts.
- Detail screen: photo with overlay (pinch zoom). Lines colored by gravity: cyan plumb, yellow level, pink other.
  Junctions: red L, orange T. Shi-Tomasi corners: green (off by default). Metadata summary below.

Pull captures: `adb pull /sdcard/Android/data/app.housethis/files/captures/`

## Capture data

Per capture in `captures/`:
- `<id>.jpg`: untouched pixels. EXIF `UserComment` = compact JSON `ExifStamp` (motion + rotation + focal px), `Software` = `housethis`.
- `<id>.json`: full `CaptureRecord` (schema `shared/.../capture/CaptureRecord.kt`, `schemaVersion` = 1).

Key fields:
- `motion.down`: unit vector toward earth, device frame (x right, y up in natural portrait, z out of screen).
- `motion.accelerometer` / `accelerometerWindow`: specific force m/s^2 (Android sign convention: +9.81 on y when upright).
  Window = 200 ms before to 100 ms after shutter press; `stdDev` = hand shake indicator. `gyroscopeWindow` same window.
- `motion.gravity`: fused gravity sensor if present (preferred source for `down`). `motion.rotation`: device->ENU quaternion.
- `rotationDegrees`: clockwise device rotation from portrait at capture (0/90/180/270); maps device frame -> upright image.
- `camera`: focal length mm, sensor size, active array, platform calibration `[fx, fy, cx, cy, s]` + distortion when reported.
- `focalLengthPx`: estimate for the upright image; assumes image spans full sensor long side.
- `features`: lines (`x1,y1,x2,y2,width,logNfa`), corners, junctions (indices into `lines`).
  Coordinates in full-res upright pixels; pixel (i, j) covers [i, i+1) x [j, j+1). Computed on a 1280 px downscale.

## Detection

- Lines: LSD (von Gioi et al., IPOL 2012) with a-contrario NFA validation -> few false positives, no tuning per image.
  Simplified: no rectangle-improvement step; low-density regions refined by radius reduction. Min length 2% of long side.
- Corners: Shi-Tomasi min-eigenvalue, sub-pixel peak, min-distance suppression.
- Junctions: pairs of segments >= 25 deg apart whose lines meet within ~1% of image size of both. Closer to "room corner" than texture corners.
- JVM timing: ~160 ms per 1280x960 frame. Phone: expect a few hundred ms (runs off main thread after capture).

## Next steps (wireframe)

1. Merge collinear fragments; vanishing points from plumb/level line families (gravity gives vertical VP prior).
2. Per-photo camera rotation from VPs + gravity; Manhattan-world room frame.
3. Multi-photo: match junctions across overlapping shots, solve poses + 3D points (bundle adjustment).
4. Scale from user reference measurements (e.g. one wall length, door height).
5. Consider ARCore/ARKit session poses during capture: metric VIO tracking would remove most of step 3.
