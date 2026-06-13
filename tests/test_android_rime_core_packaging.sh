#!/usr/bin/env bash
set -euo pipefail

root="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/.." && pwd)"
gradle_file="$root/yuyansdk/build.gradle"
app_gradle_file="$root/app/build.gradle"
workflow_file="$root/.github/workflows/build.yml"
artifact_verify_file="$root/tests/verify_android_rime_core_apk_artifact.sh"

[[ -f "$gradle_file" ]] || {
  echo "Missing yuyansdk build.gradle: $gradle_file" >&2
  exit 1
}

[[ -f "$workflow_file" ]] || {
  echo "Missing Android CI workflow: $workflow_file" >&2
  exit 1
}

[[ -f "$app_gradle_file" ]] || {
  echo "Missing Android app build.gradle: $app_gradle_file" >&2
  exit 1
}

[[ -f "$artifact_verify_file" ]] || {
  echo "Missing Android APK artifact verifier: $artifact_verify_file" >&2
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

grep -q 'librime.so' "$gradle_file" || {
  echo "yuyansdk build.gradle must package librime.so for the full Rime runtime" >&2
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

if grep -q 'libqiwo_legacy_yuyanime.so' "$gradle_file"; then
  echo "yuyansdk build.gradle must not package the legacy Rime backend for full-frost core builds" >&2
  exit 1
fi

grep -q 'libc++_shared.so' "$gradle_file" || {
  echo "yuyansdk build.gradle must package Android C++ runtime for the generated full Rime core" >&2
  exit 1
}

if grep -q "rename 'libyuyanime.so', 'libqiwo_legacy_yuyanime.so'" "$gradle_file"; then
  echo "yuyansdk build.gradle must not rename the legacy Rime runtime for full-frost core builds" >&2
  exit 1
fi

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

grep -q 'qiwoAndroidRimeCoreStlGeneratedJniLibsDir' "$gradle_file" || {
  echo "yuyansdk build.gradle must stage Android C++ runtime for the generated full Rime core" >&2
  exit 1
}

grep -q 'copyQiwoAndroidRimeCoreStlJniLibs' "$gradle_file" || {
  echo "yuyansdk build.gradle must copy libc++_shared.so from the Android NDK" >&2
  exit 1
}

grep -q 'prebuiltStlSource' "$gradle_file" || {
  echo "yuyansdk build.gradle must prefer libc++_shared.so from qiwo-android-rime-core outputs when available" >&2
  exit 1
}

grep -q 'verifyQiwoAndroidRimeCoreStlJniLibs' "$gradle_file" || {
  echo "yuyansdk build.gradle must fail clearly when libc++_shared.so is missing" >&2
  exit 1
}

if grep -q 'qiwoAndroidLegacyJniLibsDir' "$gradle_file"; then
  echo "yuyansdk build.gradle must not stage legacy JNI libs for full-frost core builds" >&2
  exit 1
fi

if grep -q 'copyQiwoAndroidLegacyJniLibs' "$gradle_file"; then
  echo "yuyansdk build.gradle must not copy legacy JNI libs for full-frost core builds" >&2
  exit 1
fi

if grep -q 'exclude fallback libyuyanime.so' "$gradle_file"; then
  echo "yuyansdk build.gradle must not rely on fallback libyuyanime.so behavior for full-frost core builds" >&2
  exit 1
fi

grep -q 'jniLibs.srcDirs' "$gradle_file" || {
  echo "yuyansdk build.gradle must configure jniLibs packaging" >&2
  exit 1
}

grep -q 'dependsOn(verifyQiwoAndroidRimeCoreJniLibs)' "$gradle_file" || {
  echo "yuyansdk preBuild must depend on verifyQiwoAndroidRimeCoreJniLibs" >&2
  exit 1
}

grep -q 'dependsOn(verifyQiwoAndroidRimeCoreStlJniLibs)' "$gradle_file" || {
  echo "yuyansdk preBuild must depend on verifyQiwoAndroidRimeCoreStlJniLibs" >&2
  exit 1
}

grep -q 'QIWO_ANDROID_RIME_CORE_DIR' "$workflow_file" || {
  echo "Android CI must set QIWO_ANDROID_RIME_CORE_DIR" >&2
  exit 1
}

grep -q 'ref: \${{ github.ref_name }}' "$workflow_file" || {
  echo "Android CI must checkout the matching qiwo-sync-core branch for cross-repo feature builds" >&2
  exit 1
}

grep -q 'repository: LeaWron/qiwo-sync-core' "$workflow_file" || {
  echo "Android CI must checkout qiwo-sync-core from LeaWron" >&2
  exit 1
}

grep -q 'QIWO_ANDROID_RIME_CORE_PACKAGE: "true"' "$workflow_file" || {
  echo "Android CI must package the generated Rime core wrapper for APK runtime verification" >&2
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

grep -q 'qiwo-debug-apk-rime-frost-core' "$workflow_file" || {
  echo "Debug APK artifact name must indicate full-frost rime-core runtime replacement" >&2
  exit 1
}

grep -q 'qiwo-release-apk-rime-frost-core' "$workflow_file" || {
  echo "Release APK artifact name must indicate full-frost rime-core runtime replacement" >&2
  exit 1
}

grep -q 'verify_android_rime_core_apk_artifact.sh' "$workflow_file" || {
  echo "Android CI must verify wrapper APK contents before uploading the artifact" >&2
  exit 1
}

grep -q 'librime.so' "$artifact_verify_file" || {
  echo "APK artifact verifier must assert the packaged full Rime runtime library" >&2
  exit 1
}

grep -q 'libqiwo_legacy_yuyanime.so' "$artifact_verify_file" || {
  echo "APK artifact verifier must reject the legacy Rime backend library" >&2
  exit 1
}

grep -q 'libc++_shared.so' "$artifact_verify_file" || {
  echo "APK artifact verifier must assert the Android C++ runtime library is packaged" >&2
  exit 1
}

grep -q 'QiwoRimeCore' "$artifact_verify_file" || {
  echo "APK artifact verifier must assert runtime diagnostic strings are packaged" >&2
  exit 1
}

grep -q 'outputFileName = "qiwoIme_' "$app_gradle_file" || {
  echo "Android final APK file name must use qiwoIme_ prefix" >&2
  exit 1
}

if grep -q 'outputFileName = "yuyanIme_' "$app_gradle_file"; then
  echo "Android final APK file name must not use yuyanIme_ prefix" >&2
  exit 1
fi
