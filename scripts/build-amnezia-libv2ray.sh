#!/usr/bin/env bash

set -euo pipefail

if [[ $# -ne 1 ]]; then
    echo "Usage: $0 <output-aar>" >&2
    exit 2
fi

repo_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
android_lib="$repo_root/AndroidLibXrayLite"
core_patch="$repo_root/core/patches/xray-core-amneziawg.patch"
awg_patch="$repo_root/core/patches/amneziawg-go-s4.patch"
awg_module="github.com/amnezia-vpn/amneziawg-go/v3"
awg_version="v3.1.20260828"
output_aar="$(realpath -m "$1")"
core_module="github.com/autorepobot/xray-core"
core_version="v0.0.0-20260704054728-50c452881eb9"
task_dir="$(mktemp -d)"
patched_core="$task_dir/xray-core"
patched_awg="$task_dir/amneziawg-go"
wrapper="$task_dir/AndroidLibXrayLite"

cleanup() {
    chmod -R u+w "$task_dir" 2>/dev/null || true
    rm -rf -- "$task_dir"
}
trap cleanup EXIT

command -v go >/dev/null
command -v gomobile >/dev/null
command -v patch >/dev/null
[[ -d "$android_lib" ]]
[[ -s "$core_patch" ]]
[[ -s "$awg_patch" ]]

module_json="$(GOWORK=off go mod download -json "$core_module@$core_version")"
core_source="$(python3 -c 'import json,sys; print(json.load(sys.stdin)["Dir"])' <<<"$module_json")"
[[ -d "$core_source" ]]

awg_json="$(GOWORK=off go mod download -json "$awg_module@$awg_version")"
awg_source="$(python3 -c 'import json,sys; print(json.load(sys.stdin)["Dir"])' <<<"$awg_json")"
[[ -d "$awg_source" ]]
mkdir -p "$patched_awg"
cp -a "$awg_source/." "$patched_awg/"
chmod -R u+w "$patched_awg"
patch --batch --forward --fuzz=0 --silent -p1 -d "$patched_awg" < "$awg_patch"

mkdir -p "$patched_core" "$wrapper" "$(dirname "$output_aar")"
cp -a "$core_source/." "$patched_core/"
cp -a "$android_lib/." "$wrapper/"
chmod -R u+w "$patched_core" "$wrapper"

patch --batch --forward --fuzz=0 --silent -p1 -d "$patched_core" < "$core_patch"

(
    cd "$patched_core"
    GOWORK=off go mod edit "-replace=$awg_module=$patched_awg"
    GOWORK=off go test ./infra/conf -run 'TestAmneziaWG' -count=1
    GOWORK=off go test ./proxy/wireguard -count=1
    # Stress teardown and packet delivery under the host race detector.
    GOWORK=off go test -race ./proxy/wireguard -run TestAmneziaWG -skip TestAmneziaWGProtectedHandshake -count=10 -timeout=90s
    GOWORK=off go test -race ./proxy/wireguard -run TestAmneziaWGProtectedHandshake -count=1 -timeout=90s

    # Exercise actual 32-bit ARM machine code; amd64 tests cannot detect ARM-only
    # startup failures. This checks Linux userspace, not Android VPN integration.
    if [[ "${AWG_TEST_ARMV7:-0}" == "1" ]]; then
        command -v qemu-arm >/dev/null
        GOWORK=off CGO_ENABLED=0 GOOS=linux GOARCH=arm GOARM=7 \
            go test -c ./infra/conf -o "$task_dir/conf-armv7.test"
        qemu-arm "$task_dir/conf-armv7.test" -test.run TestAmneziaWG -test.v -test.timeout 90s
        GOWORK=off CGO_ENABLED=0 GOOS=linux GOARCH=arm GOARM=7 \
            go test -c ./proxy/wireguard -o "$task_dir/wireguard-armv7.test"
        qemu-arm "$task_dir/wireguard-armv7.test" -test.run TestAmneziaWG -test.v -test.timeout 90s
    fi
)

(
    cd "$wrapper"
    # Dependency replacements are not transitive in Go modules.
    GOWORK=off go mod edit "-replace=$awg_module=$patched_awg"
    GOWORK=off go mod edit "-replace=github.com/xtls/xray-core=$patched_core"
    GOWORK=off go mod tidy
    GOWORK=off gomobile bind \
        -v \
        -target=android/arm \
        -androidapi 24 \
        -trimpath \
        "-ldflags=-s -w -buildid= -checklinkname=0" \
        -o "$output_aar" \
        ./
)

[[ -s "$output_aar" ]]
