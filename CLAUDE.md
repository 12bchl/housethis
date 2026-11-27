# HouseThis

Read README.md first: layout, data format, conventions.

## Rules

- Platform-independent logic (vision, geometry, data model) goes in `shared/src/commonMain`; Android/iOS code only wraps platform APIs.
- Every change to `shared` needs a JVM test in `shared/src/commonTest`. Run `./gradlew :shared:jvmTest`.
- Before finishing Android work: `./gradlew :androidApp:assembleDebug :androidApp:lintDebug` clean.
- Changing `CaptureRecord` / `MotionStamp` shape: bump `CaptureRecord.SCHEMA_VERSION`, keep old JSON decodable (defaults), update README "Capture data".
- Device frame convention (x right, y up, z out of screen, specific force +9.81 on y upright) is fixed; platform adapters convert into it.

## Toolchain (teebox)

JDK 21 at `~/tools/jdk21`, SDK at `~/android-sdk` (both in `$HOME` volume; may be wiped). If missing, reinstall:
Adoptium JDK 21 tarball + Android commandline-tools, then `sdkmanager platform-tools`. AGP fetches platforms itself.
`export JAVA_HOME=~/tools/jdk21` before `./gradlew`. No `/dev/kvm` -> no emulator; device testing is user-side.
