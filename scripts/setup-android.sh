#!/usr/bin/env bash
set -euo pipefail
sdk_root="${ANDROID_SDK_ROOT:-${ANDROID_HOME:-/usr/local/lib/android/sdk}}"
sdk_tools="$(ls -1d "$sdk_root"/cmdline-tools/*/bin | tail -1)"
export PATH="$sdk_tools:$PATH"
yes | sdkmanager --licenses > /dev/null 2>&1 || true
sdkmanager --install 'platforms;android-37.0' 'build-tools;37.0.0' \
    || sdkmanager --channel=3 --install 'platforms;android-37.2' 'build-tools;37.0.0'
if [ ! -d "$sdk_root/platforms/android-37" ]; then
    platform_minor=$(ls -1d "$sdk_root"/platforms/android-37.* | head -1)
    ln -sfn "$(basename "$platform_minor")" "$sdk_root/platforms/android-37"
fi
if [ -n "${GITHUB_ENV:-}" ]; then
    echo "ANDROID_HOME=$sdk_root" >> "$GITHUB_ENV"
    echo "ANDROID_SDK_ROOT=$sdk_root" >> "$GITHUB_ENV"
fi
