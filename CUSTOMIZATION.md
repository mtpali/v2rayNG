# ARMv7 F-Droid customization

This branch is based on upstream v2rayNG 2.2.6 commit
`15b4fff8e45da9bc0acaa5cc1d80a1d3531e8712`.

## Product changes

- Uses the 2.2.6 Android service lifecycle and the matching `AndroidLibXrayLite`
  revision `3b5a9c858c4dc98b7079cefb1380537b6b5c155c`.
- Adds AmneziaWG v3 URI/INI import, export, profile persistence, Xray outbound
  mapping, and the native backend used by MobileTinaVPN.
- Removes About, Promotion, Logcat, Check for update, and the drawer header image
  from the main navigation surface.
- Adds a divider between every navigation drawer action.
- Adds `Rename Configs` for numbering all profiles in the selected subscription
  group from a user-provided prefix.
- Adds pull-to-refresh for the selected subscription group and prevents duplicate
  refresh jobs.
- Exposes Find selected configuration as a floating button and removes it and
  Restart service from the overflow menu.
- Uses the exact application label `v2rayNG` in the F-Droid build.
- Produces only the F-Droid `armeabi-v7a` APK in CI.

The AmneziaWG implementation is adapted from `mtpali/MobileTinaVPN` commit
`670b89531d274376da9af9c1fc58fc3be8152817`. Native runtime fixtures use dummy
keys and endpoints shaped like the supplied configurations, including long I1/I2
packet chains and keepalive 1500.

## Build

The GitHub Actions workflow builds and tests the installable debug APK. To reproduce
it in an Android build environment with Go, gomobile, Android SDK 37, and NDK 29:

```sh
bash scripts/build-amnezia-libv2ray.sh V2rayNG/app/libs/libv2ray.aar
cd V2rayNG
./gradlew :app:testFdroidDebugUnitTest :app:compileFdroidDebugKotlin \
  :app:assembleFdroidDebug -PABI_FILTERS=armeabi-v7a
```

## Connectivity changes and validation boundary

- Real-ping now invokes the configured native transport directly. The 2.2.6 TCP
  preflight rejected healthy UDP-only WireGuard/AmneziaWG servers before testing
  their actual outbound.
- Retains BOM/Markdown-wrapped INI normalization, long AWG packet chains, and
  original serialized fields during batch renames.
- Subscription refresh and batch rename jobs belong to the main ViewModel;
  duplicate refresh jobs are rejected and the group ID is captured at dispatch.
- Real-ping notifications use a silent minimum-importance channel.
- Version name remains 2.2.6; version code is 748 to exceed the previous custom
  2.3.7 build's code. Android still requires matching signing certificates to update.

The native patch was applied locally with zero fuzz to core commit
`50c452881eb946e23c4098f9c288447e9d36345c`. All 14 resulting source/test files
matched the reference patch plus the dummy regression fixtures. Android XML
parsing, resource-ID reference checks, shell syntax, and whitespace checks passed.

**Not run:** `:app:testFdroidDebugUnitTest`, `:app:compileFdroidDebugKotlin`, and
`:app:assembleFdroidDebug`. The local Gradle wrapper cannot reach
`services.gradle.org` (`Network is unreachable`); Go, Android SDK, and a device
are also unavailable locally. CI validation is pending publication of this revision.

**Not run:** physical-device Amnezia handshake/traffic, VPN cold start and repeated
start/stop, real-ping against the supplied servers, and drawer/refresh/rename
interaction, accessibility, persistence, and activity-recreation checks.
The source-version difference remains a hypothesis for the VPN startup failure;
this change must not be described as a confirmed connection fix before device tests.
