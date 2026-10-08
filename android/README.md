# SpendWise Android

## First-time setup
1. Android Studio (latest stable) → **New Project → Empty Activity**, name `SpendWise`, package `com.spendwise.app`,
   Kotlin, min SDK 26, **save location = this `android/` folder**. This generates the Gradle wrapper and launcher icons.
2. **Overwrite** the generated files with the ones in this repo (`settings.gradle.kts`, `build.gradle.kts`, `gradle.properties`,
   `gradle/libs.versions.toml`, `app/build.gradle.kts`, `app/proguard-rules.pro`, everything under `app/src`).
   Delete the template's `ui/theme`, `MainActivity.kt` and `ExampleUnitTest` files if they remain.
3. Put your `google-services.json` in `app/` (git-ignored). The Google sign-in client ID must exist in it:
   enable the Google provider *before* downloading it, and add your debug SHA-1 (`./gradlew signingReport`).
4. Sync, run. Start the backend first; the emulator reaches it at `http://10.0.2.2:8000`.

## Commands
```bash
./gradlew testDebugUnitTest            # 71 JVM tests (Robolectric, MockK, Turbine)
./gradlew connectedDebugAndroidTest    # 3 Compose UI tests (needs emulator/device)
./gradlew assembleRelease -PSPENDWISE_API_URL=https://your-api.onrender.com/   # needs keystore.properties
```

## Versions
`gradle/libs.versions.toml` pins versions I know exist. Studio will flag newer ones: upgrade one group at a
time and re-run the tests. If AGP prompts for 9.x ("built-in Kotlin"), follow its migration assistant.

## Release keystore (once)
```bash
keytool -genkeypair -v -keystore release.jks -alias spendwise -keyalg RSA -keysize 2048 -validity 10000
# create android/keystore.properties (git-ignored):
# storeFile=release.jks  storePassword=...  keyAlias=spendwise  keyPassword=...
```
Back the keystore up: losing it means you can never update a published app. Add its SHA-1 to Firebase too.
