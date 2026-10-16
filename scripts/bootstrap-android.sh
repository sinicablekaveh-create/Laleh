#!/usr/bin/env bash
set -euo pipefail

required_java_major=17

if [ -n "${JAVA_HOME:-}" ] && [ -x "${JAVA_HOME}/bin/java" ]; then
  java_bin="${JAVA_HOME}/bin/java"
else
  java_bin="$(command -v java || true)"
fi

if [ -z "${java_bin}" ] || [ ! -x "${java_bin}" ]; then
  echo "JDK ${required_java_major} is required."
  exit 1
fi

java_major="$("${java_bin}" -version 2>&1 | awk -F '[\".]' '/version/ {print $2; exit}')"
if [ "${java_major}" != "${required_java_major}" ]; then
  echo "JDK ${required_java_major} is required; found ${java_major:-unknown}."
  exit 1
fi

android_sdk_root="${ANDROID_HOME:-${ANDROID_SDK_ROOT:-}}"
if [ -z "${android_sdk_root}" ] || [ ! -d "${android_sdk_root}/platforms/android-35" ] || [ ! -x "${android_sdk_root}/build-tools/35.0.0/aapt2" ]; then
  echo "Android SDK Platform 35 and Build Tools 35.0.0 are required. Set ANDROID_HOME or ANDROID_SDK_ROOT."
  exit 1
fi

if [ ! -x "./gradlew" ]; then
  echo "Gradle Wrapper is missing or not executable."
  exit 1
fi

echo "Android environment is ready. NDK is not required because TDLib is consumed as a prebuilt dependency."
