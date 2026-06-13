#!/usr/bin/env bash
set -euo pipefail

root="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/.." && pwd)"
privacy_file="$root/yuyansdk/src/main/res/raw/privacypolicy.json"
strings_file="$root/yuyansdk/src/main/res/values/strings.xml"
settings_activity_file="$root/yuyansdk/src/main/java/com/yuyan/imemodule/ui/activity/SettingsActivity.kt"
setup_activity_file="$root/yuyansdk/src/main/java/com/yuyan/imemodule/ui/setup/SetupActivity.kt"

[[ -f "$privacy_file" ]] || {
  echo "Missing Android privacy policy resource: $privacy_file" >&2
  exit 1
}

grep -q '齐我输入法' "$privacy_file" || {
  echo "Android privacy policy must use Qiwo branding" >&2
  exit 1
}

if grep -q '语燕输入法' "$privacy_file"; then
  echo "Android privacy policy must not show legacy Yuyan branding" >&2
  exit 1
fi

if grep -q '由\[王莹\]提供' "$privacy_file"; then
  echo "Android privacy policy must not show legacy provider attribution" >&2
  exit 1
fi

if grep -q 'privacyPolicySure.getValue' "$settings_activity_file" "$setup_activity_file"; then
  echo "Android first-run setup must not be blocked by the legacy privacy policy gate" >&2
  exit 1
fi

grep -q '<string name="ime_yuyan_name">齐我输入法</string>' "$strings_file" || {
  echo "Android app label must remain Qiwo branded" >&2
  exit 1
}
