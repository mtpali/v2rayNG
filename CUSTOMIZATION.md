# ARMv7 F-Droid customization

This branch is based on upstream v2rayNG 2.3.7 commit
`2020807c255b76b09c9ced4255c95600250aef48`.

## Product changes

- Removes the TCP-only batch delay test while preserving real-ping testing.
- Removes About, Promotion, Logcat, Check for update, and the drawer header image.
- Adds pull-to-refresh on the main server list using the existing subscription update action.
- Adds AmneziaWG v3 URI/INI import, export, profile persistence, Xray outbound mapping,
  and native backend support.
- Produces only the F-Droid `armeabi-v7a` APK in CI.

The AmneziaWG implementation is adapted from `mtpali/MobileTinaVPN` commit
`670b89531d274376da9af9c1fc58fc3be8152817` and is rebuilt against the exact Xray
revision declared by the current `AndroidLibXrayLite` submodule.

## Build

The GitHub Actions workflow builds and tests the installable debug APK. To reproduce it in
an Android build environment with Go, gomobile, Android SDK 37, and NDK 29 installed:

```sh
bash scripts/build-amnezia-libv2ray.sh V2rayNG/app/libs/libv2ray.aar
cd V2rayNG
./gradlew :app:testFdroidDebugUnitTest :app:compileFdroidDebugKotlin \
  :app:assembleFdroidDebug -PABI_FILTERS=armeabi-v7a
```

The expected artifact is under `V2rayNG/app/build/outputs/apk/fdroid/debug/`.
