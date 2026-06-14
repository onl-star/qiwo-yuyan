#!/usr/bin/env bash
set -euo pipefail

root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

sync_bridge_file="$root/yuyansdk/src/main/java/com/qiwo/sync/QiwoSync.kt"
sync_models_file="$root/yuyansdk/src/main/java/com/yuyan/imemodule/sync/SyncModels.kt"
native_sync_engine_file="$root/yuyansdk/src/main/java/com/yuyan/imemodule/sync/NativeSyncEngine.kt"
settings_activity_file="$root/yuyansdk/src/main/java/com/yuyan/imemodule/ui/activity/SettingsActivity.kt"
setup_activity_file="$root/yuyansdk/src/main/java/com/yuyan/imemodule/ui/setup/SetupActivity.kt"
privacy_file="$root/yuyansdk/src/main/res/raw/privacypolicy.json"
app_gradle_file="$root/app/build.gradle"
workflow_file="$root/.github/workflows/build.yml"
other_settings_file="$root/yuyansdk/src/main/java/com/yuyan/imemodule/ui/fragment/OtherSettingsFragment.kt"
about_fragment_file="$root/yuyansdk/src/main/java/com/yuyan/imemodule/ui/fragment/AboutFragment.kt"

for file in \
  "$sync_bridge_file" \
  "$sync_models_file" \
  "$native_sync_engine_file" \
  "$settings_activity_file" \
  "$setup_activity_file" \
  "$privacy_file" \
  "$app_gradle_file" \
  "$workflow_file" \
  "$other_settings_file" \
  "$about_fragment_file"; do
  [[ -f "$file" ]] || {
    echo "Missing required file: $file" >&2
    exit 1
  }
done

grep -q 'put("frontend", "qiwo-yuyan")' "$sync_bridge_file" || {
  echo "Android sync bridge must emit frontend=qiwo-yuyan" >&2
  exit 1
}

if grep -Eq 'put\("frontend", "(yuyanime|qiwoime|QiwoIme)"\)' "$sync_bridge_file"; then
  echo "Android sync bridge must not emit legacy frontend identity" >&2
  exit 1
fi

grep -q 'val frontend: String = "qiwo-yuyan"' "$sync_models_file" || {
  echo "Android sync request model must default frontend=qiwo-yuyan" >&2
  exit 1
}

if grep -q 'SyncEngine().execute' "$native_sync_engine_file"; then
  echo "NativeSyncEngine must not fall back to the Kotlin SyncEngine" >&2
  exit 1
fi

grep -q 'Native sync unavailable' "$native_sync_engine_file" || {
  echo "NativeSyncEngine must return a clear native-sync-unavailable error" >&2
  exit 1
}

if grep -q 'privacyPolicySure.getValue' "$settings_activity_file"; then
  echo "First launch must not be gated by the legacy privacy page" >&2
  exit 1
fi

if grep -q 'privacyPolicySure.getValue' "$setup_activity_file"; then
  echo "SetupActivity must not bounce users back to the legacy privacy page" >&2
  exit 1
fi

if grep -q 'action_settingsFragment_to_privacyPolicyFragment' "$settings_activity_file"; then
  echo "SettingsActivity must not route fresh launch to privacyPolicyFragment" >&2
  exit 1
fi

if grep -q '由\[王莹\]提供' "$privacy_file"; then
  echo "Privacy/onboarding resources must not contain legacy provider attribution" >&2
  exit 1
fi

if grep -Eq '语燕|Yuyan|yuyan' "$privacy_file"; then
  echo "Privacy/onboarding resources must not contain legacy Yuyan branding" >&2
  exit 1
fi

grep -q 'outputFileName = "qiwoIme_' "$app_gradle_file" || {
  echo "APK output file name must use qiwoIme_ prefix" >&2
  exit 1
}

if grep -q 'outputFileName = "yuyanIme_' "$app_gradle_file"; then
  echo "APK output file name must not use yuyanIme_ prefix" >&2
  exit 1
fi

grep -q '"qiwoIme_${TimeUtils.iso8601UTCDateTime(exportTimestamp)}.zip"' "$other_settings_file" || {
  echo "User data export file name must use qiwoIme_ prefix" >&2
  exit 1
}

grep -q '"qiwoIme_crash_log${TimeUtils.iso8601UTCDateTime(exportTimestamp)}.zip"' "$about_fragment_file" || {
  echo "Crash log export file name must use qiwoIme_ prefix" >&2
  exit 1
}

grep -q 'CustomConstant.QIWO_IME_REPO' "$about_fragment_file" || {
  echo "About page repository links must use QIWO_IME_REPO" >&2
  exit 1
}

if grep -q 'CustomConstant.YUYAN_IME_REPO' "$about_fragment_file"; then
  echo "About page repository links must not reference removed YUYAN_IME_REPO" >&2
  exit 1
fi

if grep -q 'yuyanIme_' "$other_settings_file" "$about_fragment_file"; then
  echo "Export file names must not use yuyanIme_ prefix" >&2
  exit 1
fi

grep -q 'qiwo-debug-apk-rime-frost-core' "$workflow_file" || {
  echo "Debug CI artifact name must use Qiwo naming" >&2
  exit 1
}

grep -q 'qiwo-release-apk-rime-frost-core' "$workflow_file" || {
  echo "Release CI artifact name must use Qiwo naming" >&2
  exit 1
}
