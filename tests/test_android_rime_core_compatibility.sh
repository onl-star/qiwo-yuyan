#!/usr/bin/env bash
set -euo pipefail

root="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/.." && pwd)"
rime_file="$root/yuyansdk/src/main/java/com/yuyan/inputmethod/core/Rime.kt"
rime_engine_file="$root/yuyansdk/src/main/java/com/yuyan/inputmethod/RimeEngine.kt"
gradle_file="$root/yuyansdk/build.gradle"
workflow_file="$root/.github/workflows/build.yml"

for file in "$rime_file" "$rime_engine_file" "$gradle_file" "$workflow_file"; do
  [[ -f "$file" ]] || {
    echo "Missing Android Rime core compatibility file: $file" >&2
    exit 1
  }
done

for symbol in \
  startupRime exitRime setRimePageSize processRimeKey replaceRimeKey clearRimeComposition \
  getRimeCommit getRimeContext getRimeStatus setRimeOption getCurrentRimeSchema \
  selectRimeSchema selectRimeCandidate getRimeKeycodeByName getRimeAssociateList selectRimeAssociate; do
  grep -q "external fun $symbol" "$rime_file" || {
    echo "Rime.kt must keep existing JNI contract: $symbol" >&2
    exit 1
  }
done

for symbol in getRimeRawInput getRimeCompositionCaret setRimeCompositionCaret rawInput compositionCaret setCompositionCaret; do
  grep -q "$symbol" "$rime_file" || {
    echo "Rime.kt must expose direct composition caret API: $symbol" >&2
    exit 1
  }
done

for symbol in selectCandidate selectSchema setOption getAssociateList chooseAssociate getCurrentRimeSchema; do
  grep -q "$symbol" "$rime_file" || {
    echo "Rime.kt must preserve compatibility helper: $symbol" >&2
    exit 1
  }
done

for symbol in Rime.selectCandidate Rime.selectSchema Rime.setOption Rime.getAssociateList Rime.chooseAssociate Rime.getCurrentRimeSchema; do
  grep -q "$symbol" "$rime_engine_file" || {
    echo "RimeEngine must continue routing through $symbol" >&2
    exit 1
  }
done

grep -q 'CustomConstant.RIME_DICT_PATH' "$rime_file" || {
  echo "Rime startup must preserve shared/user Rime dictionary directory handling" >&2
  exit 1
}

grep -q 'QIWO_ANDROID_RIME_CORE_DIR' "$gradle_file" || {
  echo "Gradle must package the generated source-owned Android Rime core" >&2
  exit 1
}

grep -q 'LeaWron/qiwo-android-rime-core' "$workflow_file" || {
  echo "Android CI must checkout the LeaWron qiwo-android-rime-core repository" >&2
  exit 1
}
