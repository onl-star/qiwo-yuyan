#!/usr/bin/env bash
set -euo pipefail

root="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/.." && pwd)"
gradle_file="$root/yuyansdk/build.gradle"

[[ -f "$gradle_file" ]] || {
  echo "Missing yuyansdk build.gradle: $gradle_file" >&2
  exit 1
}

grep -q 'qiwo-android-rime-core' "$gradle_file" || {
  echo "yuyansdk build.gradle must reference qiwo-android-rime-core native output" >&2
  exit 1
}

grep -q 'QIWO_ANDROID_RIME_CORE_DIR' "$gradle_file" || {
  echo "yuyansdk build.gradle must allow CI to override qiwo-android-rime-core location" >&2
  exit 1
}

grep -q 'libyuyanime.so' "$gradle_file" || {
  echo "yuyansdk build.gradle must package libyuyanime.so from the generated Android Rime core" >&2
  exit 1
}

grep -q 'copyQiwoAndroidRimeCoreJniLibs' "$gradle_file" || {
  echo "yuyansdk build.gradle must copy qiwo-android-rime-core JNI libraries into generated jniLibs" >&2
  exit 1
}

grep -q 'verifyQiwoAndroidRimeCoreJniLibs' "$gradle_file" || {
  echo "yuyansdk build.gradle must fail clearly when generated libyuyanime.so artifacts are missing" >&2
  exit 1
}

grep -q 'qiwoAndroidRimeCorePrebuiltJniLibsDir' "$gradle_file" || {
  echo "yuyansdk build.gradle must support prebuilt qiwo-android-rime-core target/android-jniLibs outputs" >&2
  exit 1
}

grep -q 'qiwoAndroidRimeCoreGeneratedJniLibsDir' "$gradle_file" || {
  echo "yuyansdk build.gradle must stage qiwo-android-rime-core outputs under build/generated/jniLibs" >&2
  exit 1
}

grep -q 'jniLibs.srcDirs' "$gradle_file" || {
  echo "yuyansdk build.gradle must configure jniLibs packaging" >&2
  exit 1
}

grep -q 'dependsOn(verifyQiwoAndroidRimeCoreJniLibs)' "$gradle_file" || {
  echo "yuyansdk preBuild must depend on verifyQiwoAndroidRimeCoreJniLibs" >&2
  exit 1
}
