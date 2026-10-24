# Subscription Tracker

An Android app that tracks recurring subscriptions and free trials, shows your monthly and yearly total, and sends a notification 3 days, 1 day, and on the day a renewal is due. All data stays on the device.

## Getting the APK

Every push to `main` builds a debug APK with GitHub Actions.

1. Open the **Actions** tab, then the latest **Build APK** run.
2. Download **subscription-tracker-debug-apk** from the Artifacts section at the bottom.
3. Unzip it and copy `app-debug.apk` to your phone.
4. Open it on the phone and allow "install unknown apps" when asked.

You can also start a build manually from the Actions tab with **Run workflow**.

## Ads (AdMob)

The app ships with Google's official **test** ad IDs, so it is safe to run and click while testing. Before publishing, replace:

- App ID in `app/src/main/AndroidManifest.xml`
- Banner unit ID in `app/src/main/res/layout/activity_main.xml`
- Interstitial unit ID in `MainActivity.kt`

Never click your own live ads. Use test ads or test devices during development.

## Before publishing on Google Play

- Change `applicationId` in `app/build.gradle.kts` to a package name you own.
- Check Google Play's current target API level requirement and raise `targetSdk` if needed.
- Create a signed release build (AAB) and add a privacy policy that mentions AdMob.
