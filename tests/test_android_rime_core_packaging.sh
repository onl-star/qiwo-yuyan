#!/usr/bin/env bash
set -euo pipefail

root="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/.." && pwd)"
gradle_file="$root/yuyansdk/build.gradle"
workflow_file="$root/.github/workflows/build.yml"

[[ -f "$gradle_file" ]] || {
  echo "Missing yuyansdk build.gradle: $gradle_file" >&2
  exit 1
}

[[ -f "$workflow_file" ]] || {
  echo "Missing Android CI workflow: $workflow_file" >&2
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
  echo "yuyansdk build.gradle must package libyuyanime.so for the Rime runtime" >&2
  exit 1
}

grep -q 'QIWO_ANDROID_RIME_CORE_PACKAGE' "$gradle_file" || {
  echo "yuyansdk build.gradle must gate generated Rime core packaging behind QIWO_ANDROID_RIME_CORE_PACKAGE" >&2
  exit 1
}

grep -q 'qiwoAndroidRimeCorePackage' "$gradle_file" || {
  echo "yuyansdk build.gradle must keep generated Rime core runtime adoption explicit" >&2
  exit 1
}

grep -q 'libqiwo_legacy_yuyanime.so' "$gradle_file" || {
  echo "yuyansdk build.gradle must package the legacy Rime backend under a distinct library name when generated core is enabled" >&2
  exit 1
}

grep -q "rename 'libyuyanime.so', 'libqiwo_legacy_yuyanime.so'" "$gradle_file" || {
  echo "yuyansdk build.gradle must rename the legacy Rime runtime for the generated wrapper core" >&2
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

grep -q 'qiwoAndroidLegacyJniLibsDir' "$gradle_file" || {
  echo "yuyansdk build.gradle must stage legacy JNI libs through a filtered generated directory" >&2
  exit 1
}

grep -q 'copyQiwoAndroidLegacyJniLibs' "$gradle_file" || {
  echo "yuyansdk build.gradle must copy legacy JNI libs separately from generated Rime core libs" >&2
  exit 1
}

grep -q 'if (qiwoAndroidRimeCorePackage)' "$gradle_file" || {
  echo "yuyansdk build.gradle must exclude fallback libyuyanime.so only when generated Rime core libs are explicitly packaged" >&2
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

grep -q 'QIWO_ANDROID_RIME_CORE_DIR' "$workflow_file" || {
  echo "Android CI must set QIWO_ANDROID_RIME_CORE_DIR" >&2
  exit 1
}

grep -q 'QIWO_ANDROID_RIME_CORE_PACKAGE: "false"' "$workflow_file" || {
  echo "Android CI must not package the generated Rime core until runtime parity is proven" >&2
  exit 1
}

grep -q 'Checkout qiwo-android-rime-core' "$workflow_file" || {
  echo "Android CI must checkout qiwo-android-rime-core" >&2
  exit 1
}

grep -q 'LeaWron/qiwo-android-rime-core' "$workflow_file" || {
  echo "Android CI must checkout qiwo-android-rime-core from LeaWron" >&2
  exit 1
}

grep -q 'QIWO_ANDROID_RIME_CORE_DEPLOY_KEY' "$workflow_file" || {
  echo "Android CI must use the scoped qiwo-android-rime-core deploy key for private checkout" >&2
  exit 1
}

grep -q 'Build qiwo-android-rime-core JNI libraries' "$workflow_file" || {
  echo "Android CI must build qiwo-android-rime-core JNI libraries before Gradle" >&2
  exit 1
}

grep -q 'Verify qiwo-android-rime-core JNI artifacts' "$workflow_file" || {
  echo "Android CI must verify qiwo-android-rime-core JNI artifacts before Gradle" >&2
  exit 1
}

grep -q 'Verify qiwo-android-rime-core JNI symbols' "$workflow_file" || {
  echo "Android CI must verify qiwo-android-rime-core JNI symbols before Gradle" >&2
  exit 1
}

grep -q 'scripts/verify-artifacts.sh target/android-jniLibs' "$workflow_file" || {
  echo "Android CI must fail when generated libyuyanime.so artifacts are missing" >&2
  exit 1
}

grep -q 'scripts/verify-symbols.sh target/android-jniLibs/arm64-v8a/libyuyanime.so' "$workflow_file" || {
  echo "Android CI must run qiwo-android-rime-core symbol parity verification" >&2
  exit 1
}

grep -q 'qiwo-debug-apk-rime-core-checked' "$workflow_file" || {
  echo "Debug APK artifact name must indicate rime-core verification without implying runtime replacement" >&2
  exit 1
}

grep -q 'qiwo-release-apk-rime-core-checked' "$workflow_file" || {
  echo "Release APK artifact name must indicate rime-core verification without implying runtime replacement" >&2
  exit 1
}
