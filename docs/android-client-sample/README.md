# Android sample (MVVM + coroutines)

This folder contains **sample Kotlin + XML** files that demonstrate the MVVM
architecture requested (View → ViewModel → Model with coroutines and Room).
It is **not a runnable Android project by itself**. To run it, create a new
Android Studio project and copy the files into it.

## How to run

1. **Create a new Android Studio project** (Empty Activity).
2. **Copy Kotlin files** from `docs/android-client-sample/src/main/kotlin` into
   your new app module at `app/src/main/kotlin`.
3. **Copy XML files** from `docs/android-client-sample/src/main/res/layout` into
   `app/src/main/res/layout`.
4. **Copy `AndroidManifest.xml`** from `docs/android-client-sample/src/main/`
   into your app module and merge the `MainActivity` entry (or replace your
   generated manifest).
5. **Add dependencies** (Room, Retrofit, coroutines, lifecycle, Moshi) in
   `app/build.gradle` as shown in `docs/android-client-mvvm.md`.
6. **Provide dependencies** for `PestScoutApi`, `AppDatabase`, and
   `FarmRepository` (Hilt or manual wiring), then inject into `FarmViewModel`.
7. **Run** the app from Android Studio on an emulator or device.

## Entry point

`MainActivity` loads `FarmFragment` into `activity_main.xml`, which hosts the
list UI.
