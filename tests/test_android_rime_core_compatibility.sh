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

grep -q 'UnsatisfiedLinkError' "$rime_file" || {
  echo "Rime.kt must fall back safely when the packaged legacy libyuyanime.so lacks direct caret JNI symbols" >&2
  exit 1
}

grep -q 'moveLegacyCompositionCaret' "$rime_file" || {
  echo "Rime.kt must keep legacy caret movement for the current runtime library" >&2
  exit 1
}

grep -q 'QiwoRimeCore' "$rime_file" || {
  echo "Rime.kt must emit QiwoRimeCore logcat diagnostics for runtime verification" >&2
  exit 1
}

grep -q 'loaded JNI library yuyanime' "$rime_file" || {
  echo "Rime.kt must log JNI library load success for device diagnosis" >&2
  exit 1
}

grep -q 'direct composition caret JNI' "$rime_file" || {
  echo "Rime.kt must log direct caret JNI availability for device diagnosis" >&2
  exit 1
}

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

grep -q 'resolveRimeKeyCode(event)' "$rime_engine_file" || {
  echo "RimeEngine must resolve soft-key Android KeyEvent values before passing them to full Rime" >&2
  exit 1
}

grep -q 'rimeModifierMask(event)' "$rime_engine_file" || {
  echo "RimeEngine must pass a real Rime modifier mask instead of KeyEvent action" >&2
  exit 1
}

awk '
  /fun onNormalKey\(event: KeyEvent\)/ { in_func = 1 }
  in_func && /Rime\.processKey\(keyChar, rimeModifierMask\(event\)\)/ { correct = 1 }
  in_func && /Rime\.processKey\(.*event\.action/ { exit 1 }
  in_func && /^    private fun / { in_func = 0 }
  END { exit correct ? 0 : 1 }
' "$rime_engine_file" || {
  echo "RimeEngine.onNormalKey must not pass KeyEvent.action as the Rime mask" >&2
  exit 1
}

for symbol in 'KEYCODE_A..KeyEvent.KEYCODE_Z' 'KEYCODE_0..KeyEvent.KEYCODE_9' 'RIME_SHIFT_MASK' 'RIME_CONTROL_MASK' 'RIME_ALT_MASK'; do
  grep -q "$symbol" "$rime_engine_file" || {
    echo "RimeEngine must keep full-Rime soft-key mapping coverage: $symbol" >&2
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
