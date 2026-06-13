#!/usr/bin/env bash
set -euo pipefail

root="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/.." && pwd)"
diagnostics_file="$root/yuyansdk/src/main/java/com/yuyan/imemodule/sync/RimeUserDictDiagnostics.kt"
rime_engine_file="$root/yuyansdk/src/main/java/com/yuyan/inputmethod/RimeEngine.kt"
rime_file="$root/yuyansdk/src/main/java/com/yuyan/inputmethod/core/Rime.kt"
sync_settings_file="$root/yuyansdk/src/main/java/com/yuyan/imemodule/ui/fragment/SyncSettingsFragment.kt"
sync_worker_file="$root/yuyansdk/src/main/java/com/yuyan/imemodule/sync/SyncWorker.kt"
gradle_file="$root/yuyansdk/build.gradle"

for file in \
  "$diagnostics_file" \
  "$rime_engine_file" \
  "$rime_file" \
  "$sync_settings_file" \
  "$sync_worker_file" \
  "$gradle_file"
do
  [[ -f "$file" ]] || {
    echo "Missing Android Rime userdb diagnostic file: $file" >&2
    exit 1
  }
done

grep -q 'QiwoUserDict' "$diagnostics_file" || {
  echo "User dictionary diagnostics must have a dedicated logcat tag" >&2
  exit 1
}

grep -q 'InstallationHelper.makeSafeId' "$diagnostics_file" || {
  echo "User dictionary diagnostics must inspect the normalized Rime sync device directory" >&2
  exit 1
}

grep -Fq '.userdb' "$diagnostics_file" || {
  echo "User dictionary diagnostics must inspect local Rime userdb files" >&2
  exit 1
}

grep -Fq '.userdb.txt' "$diagnostics_file" || {
  echo "User dictionary diagnostics must inspect exported Rime sync userdb files" >&2
  exit 1
}

grep -q 'warningForMissingLocalUserDb' "$diagnostics_file" || {
  echo "User dictionary diagnostics must expose a missing-userdb warning" >&2
  exit 1
}

grep -q 'RimeUserDictDiagnostics.logSnapshot' "$rime_engine_file" || {
  echo "Candidate selection must log user dictionary snapshots after native selection" >&2
  exit 1
}

grep -q 'candidate-select' "$rime_engine_file" || {
  echo "Candidate selection diagnostics must use a stable stage name" >&2
  exit 1
}

grep -q 'selectRimeCandidate index=' "$rime_file" || {
  echo "Rime.kt must log candidate selection native results" >&2
  exit 1
}

grep -q 'manual-export' "$sync_settings_file" || {
  echo "Manual sync must log a pre-upload Rime user data export stage" >&2
  exit 1
}

grep -q 'manual-import' "$sync_settings_file" || {
  echo "Manual sync must log a post-download Rime user data import stage" >&2
  exit 1
}

grep -q 'warningForMissingLocalUserDb' "$sync_settings_file" || {
  echo "Manual sync must warn when local Rime user dictionaries were not exported" >&2
  exit 1
}

grep -q 'worker-export' "$sync_worker_file" || {
  echo "Background sync must log a Rime user data export stage" >&2
  exit 1
}

grep -q 'worker-import' "$sync_worker_file" || {
  echo "Background sync must log a Rime user data import stage" >&2
  exit 1
}

grep -q 'warningForMissingLocalUserDb' "$sync_worker_file" || {
  echo "Background sync must log missing local Rime user dictionary diagnostics" >&2
  exit 1
}

grep -q 'prebuiltStlSource' "$gradle_file" || {
  echo "Android packaging must prefer libc++_shared.so produced by qiwo-android-rime-core" >&2
  exit 1
}
