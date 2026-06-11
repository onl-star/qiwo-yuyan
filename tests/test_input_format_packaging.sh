#!/usr/bin/env bash
set -euo pipefail

root="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/.." && pwd)"
wrapper_file="$root/yuyansdk/src/main/java/com/qiwo/inputformat/QiwoInputFormat.kt"
prefs_file="$root/yuyansdk/src/main/java/com/yuyan/imemodule/prefs/AppPrefs.kt"
strings_file="$root/yuyansdk/src/main/res/values/strings.xml"
ime_service_file="$root/yuyansdk/src/main/java/com/yuyan/imemodule/service/ImeService.kt"
candidate_view_file="$root/yuyansdk/src/main/java/com/yuyan/imemodule/candidate/CandidateView.kt"
rime_engine_file="$root/yuyansdk/src/main/java/com/yuyan/inputmethod/RimeEngine.kt"
gradle_file="$root/yuyansdk/build.gradle"
workflow_file="$root/.github/workflows/build.yml"
android_source_root="$root/yuyansdk/src/main/java"
android_res_root="$root/yuyansdk/src/main/res"

for file in "$wrapper_file" "$prefs_file" "$strings_file" "$ime_service_file" "$candidate_view_file" "$rime_engine_file" "$gradle_file" "$workflow_file"; do
  [[ -f "$file" ]] || {
    echo "Missing expected Android input-format integration file: $file" >&2
    exit 1
  }
done

grep -q 'package com\.qiwo\.inputformat' "$wrapper_file" || {
  echo "QiwoInputFormat wrapper must use package com.qiwo.inputformat" >&2
  exit 1
}

grep -q 'object QiwoInputFormat' "$wrapper_file" || {
  echo "QiwoInputFormat wrapper must expose an object" >&2
  exit 1
}

grep -q 'loadLibrary("qiwo_input_format")' "$wrapper_file" || {
  echo "QiwoInputFormat wrapper must load libqiwo_input_format.so" >&2
  exit 1
}

grep -q 'nativeFormatCommitText' "$wrapper_file" || {
  echo "QiwoInputFormat wrapper must declare nativeFormatCommitText" >&2
  exit 1
}

grep -q 'formatCommitText(commitText: String, beforeCursor: String?, afterCursor: String?, enabled: Boolean)' "$wrapper_file" || {
  echo "QiwoInputFormat wrapper must expose formatCommitText(commitText, beforeCursor, afterCursor, enabled)" >&2
  exit 1
}

grep -q 'inputCommitAutoSpacing' "$prefs_file" || {
  echo "AppPrefs must expose distinct inputCommitAutoSpacing preference" >&2
  exit 1
}

grep -q 'input_commit_auto_spacing_enable' "$prefs_file" || {
  echo "AppPrefs must use input_commit_auto_spacing_enable key" >&2
  exit 1
}

grep -A6 'inputCommitAutoSpacing = switch' "$prefs_file" | grep -q '"input_commit_auto_spacing_enable"' || {
  echo "inputCommitAutoSpacing must use input_commit_auto_spacing_enable key" >&2
  exit 1
}

grep -A6 'inputCommitAutoSpacing = switch' "$prefs_file" | grep -q 'true' || {
  echo "inputCommitAutoSpacing must default to true" >&2
  exit 1
}

if grep -Rqs 'auto_commit_spacing' "$android_source_root" "$android_res_root"; then
  echo "Android must not depend on the desktop Rime auto_commit_spacing option" >&2
  exit 1
fi

if grep -RqsE 'Ctrl\+grave|Control\+grave|F4 switcher|switcher/save_options' "$android_source_root" "$android_res_root"; then
  echo "Android must not expose or depend on the desktop Ctrl+grave/F4 switcher entry" >&2
  exit 1
fi

grep -q 'input_commit_auto_spacing">中英数字自动空格<' "$strings_file" || {
  echo "strings.xml must expose the 中英数字自动空格 label" >&2
  exit 1
}

grep -q 'input_commit_auto_spacing_summary' "$strings_file" || {
  echo "strings.xml must expose an input commit auto spacing summary" >&2
  exit 1
}

grep -q 'import com\.qiwo\.inputformat\.QiwoInputFormat' "$ime_service_file" || {
  echo "ImeService must import QiwoInputFormat" >&2
  exit 1
}

grep -q 'formatCommitText(' "$ime_service_file" || {
  echo "ImeService commit path must call QiwoInputFormat.formatCommitText" >&2
  exit 1
}

grep -q 'inputCommitAutoSpacing\.getValue()' "$ime_service_file" || {
  echo "ImeService must read the distinct inputCommitAutoSpacing preference" >&2
  exit 1
}

grep -q 'getTextBeforeCursor' "$ime_service_file" || {
  echo "ImeService must pass before-cursor context to the formatter" >&2
  exit 1
}

grep -q 'getTextAfterCursor' "$ime_service_file" || {
  echo "ImeService must pass after-cursor context to the formatter" >&2
  exit 1
}

grep -q 'qiwo-input-format-core' "$gradle_file" || {
  echo "yuyansdk build.gradle must reference qiwo-input-format-core native output" >&2
  exit 1
}

grep -q 'QIWO_INPUT_FORMAT_CORE_DIR' "$gradle_file" || {
  echo "yuyansdk build.gradle must allow CI to override qiwo-input-format-core location" >&2
  exit 1
}

grep -q 'libqiwo_input_format.so' "$gradle_file" || {
  echo "yuyansdk build.gradle must package libqiwo_input_format.so" >&2
  exit 1
}

grep -q 'verifyQiwoInputFormatJniLibs' "$gradle_file" || {
  echo "yuyansdk build.gradle must fail clearly when libqiwo_input_format.so is missing" >&2
  exit 1
}

grep -q 'jniLibs.srcDirs' "$gradle_file" || {
  echo "yuyansdk build.gradle must configure jniLibs packaging" >&2
  exit 1
}

grep -q 'Checkout qiwo-input-format-core' "$workflow_file" || {
  echo "Android CI must checkout qiwo-input-format-core" >&2
  exit 1
}

grep -q 'Build qiwo-input-format-core JNI libraries' "$workflow_file" || {
  echo "Android CI must build qiwo-input-format-core JNI libraries" >&2
  exit 1
}

grep -q 'armv7-linux-androideabi' "$workflow_file" || {
  echo "Android CI must build qiwo-input-format-core for all yuyansdk ABIs" >&2
  exit 1
}

for file in "$candidate_view_file" "$rime_engine_file"; do
  if grep -Eq 'QiwoInputFormat|nativeFormatCommitText|formatCommitText' "$file"; then
    echo "$(basename "$file") must not format candidate/preedit values directly" >&2
    exit 1
  fi
done

grep -q 'service\.commitText' "$candidate_view_file" || {
  echo "CandidateView direct candidate commits must still call ImeService.commitText" >&2
  exit 1
}

grep -A8 'label\.isDigitsOnly()' "$candidate_view_file" | grep -q 'DecodingInfo\.isCandidatesEmpty' || {
  echo "CandidateView hardware digit input must distinguish empty candidates from numeric selection" >&2
  exit 1
}

grep -A8 'label\.isDigitsOnly()' "$candidate_view_file" | grep -q 'service\.commitText(label)' || {
  echo "CandidateView hardware digits without candidates must go through ImeService.commitText" >&2
  exit 1
}
