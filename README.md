# Subscription Tracker

An Android app that tracks recurring subscriptions and free trials. All data stays on the device.

## Features (v1.1)

- Currency choice, categories, and a spending breakdown screen
- Reminders at a time you choose, on the days you choose (7, 3, 2, 1 or 0 days before)
- Active / paused / cancelled status, with "money saved by cancelling"
- Cancel link or note per subscription, and price-change history
- Search and sort
- Home-screen widget with the next three renewals
- Optional app lock (fingerprint, face or phone PIN)
- Backup and restore to a JSON file
- Ad consent form (EU/UK) through Google's User Messaging Platform

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
