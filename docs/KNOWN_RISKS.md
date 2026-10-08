# Known risks (read before your first Android build)

The backend is verified: 63 tests pass, migrations run, the server boots. The Android code was written
without being able to compile it (no Android SDK in the authoring environment). Expect a first-build
fix-up session. The most likely friction points, in order:

1. **Library API drift**: Vico (`CartesianChartHost`, axis `rememberStart/rememberBottom`, `columnSeries`), Material 3
   `rememberSwipeToDismissBoxState`, `DateRangePicker`. If a signature differs in your resolved version, the fix is small.
2. **Versions**: `libs.versions.toml` pins versions that exist but may be behind. KSP must match the Kotlin version
   (`<kotlin>-<ksp>`). Bump Kotlin + KSP together.
3. **`google-services.json`**: missing file or missing Google OAuth client → `R.string.default_web_client_id` unresolved.
4. **`hasRoute(it.route::class)`** in AppNavigation may need an explicit type if the compiler complains about `KClass<out Any>`.
5. **Robolectric** SDK: `src/test/resources/robolectric.properties` pins sdk=34; change if your Robolectric version lacks it.
6. **Tests I never ran**: all 71 Android unit tests and 3 UI tests. A failing test can be a test bug OR a code bug; read both.

Workflow: build → paste each compile error to Claude → fix → run tests → run the app → *then* use the interview pack.
