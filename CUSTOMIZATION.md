# v2rayNG 2.3.11 customization

This branch imports the complete upstream **2.3.11** source at
`e1b73f2fa8487dfc303da52d109719c1e32386b4`, retaining the custom behavior from
`feature/armv7-fdroid-amneziawg` head `56a546d336f7c36d5012bd57db760e3b361134b1`.
The Compose interface, upstream protocol fixes, and dependency versions come from
that release. This is a source migration, not a version-label change.

## Preserved behavior

- AmneziaWG v3 INI and URI import/export, all stored AWG options and ranges,
  explicit-zero keepalive, default mobile keepalive, AllowedIPs and profile DNS.
- Profile DNS stays inside the WireGuard/Amnezia tunnel; bootstrap/global DNS is
  not overwritten. New upstream WireGuard remoteDNS and finalMask are retained.
- Ordered native shutdown, descriptor cleanup before restart, screen-off sampling
  pause, long-idle wake recovery, physical-network readiness and explicit-stop
  cancellation. Setup and native shutdown use service-owned/background scopes.
- Cumulative upload/download per profile GUID, hidden zero-usage rows, and an
  atomic reset with generation protection against late samples.
- Compose drawer: Rename Configs, VPN Hotspot, separators between actions, no
  header, About, Promotion, Logcat, update checker or translation credits.
- Subscription-group pull-to-refresh, duplicate-refresh prevention, numbered
  batch rename with rollback and preservation of unknown stored JSON fields.
- Locate-selected floating button; no restart/locate overflow items, no
  real-ping task counter and no extra speed-test notification.
- English-only resources/locales, exact v2rayNG application label, R8 and resource
  shrinking, ARMv7 and ARM64 F-Droid release APKs, ARM64 version-code preference.

## Native source and build

`AndroidLibXrayLite` is pinned to the release gitlink
`1f3e2c5df1c248ed42630cee2bf747643d303ecc`; its Xray dependency is
`v1.260327.1-0.20261010092107-701af60772cd` (upstream v26.10.10).
The Amnezia patch is rebased onto that source. AWG remains pinned to
`v3.1.20260828` with the S4 padding correction. Protobuf descriptors are regenerated
from the patched schema, preserving upstream remoteDNS field 10.
`hev-socks5-tunnel` uses release gitlink `d9dca26c7ad0e494492244f0309e80ee583e739e`.

The workflow runs native configuration, transport, teardown/race and ARM QEMU
checks, then both distribution JVM tests/compilation, Play Store debug assembly,
F-Droid release/R8 assembly, lint and APK ABI/library verification.
It uploads separate ARMv7/ARM64 APK ZIPs and the matching R8 mapping. Release
artifacts currently use the workflow debug signing configuration.

## Validation status

Passed locally: manifest/resource XML parsing, shell syntax and whitespace checks.
GitHub Actions validation is pending for this migration candidate.

**Not run locally:** Gradle tests, compile, assemble and lint: the environment has
no Android SDK/Go toolchain and cannot reach the Gradle distribution endpoint.

**Not run:** Real Android device tests for cold/duplicate starts, setup failure,
normal/repeated stop, stop during setup/recovery, repeat sleep/wake and network
handovers in VPN/proxy/root modes; real server handshake/traffic; rooted hotspot
clients; release JNI/Gson behavior; Compose light/dark/dynamic color and one/two
columns, IME/dialogs, touch/keyboard/D-pad/TalkBack, persistence and recreation.
No emulator/phone is available. The earlier 2.2.6 device success is not a device
verification of this migration. Keep the PR Draft until device validation.
