#!/usr/bin/env bash
set -euo pipefail

root="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/.." && pwd)"

rime_engine_file="$root/yuyansdk/src/main/java/com/yuyan/inputmethod/RimeEngine.kt"
kernel_file="$root/yuyansdk/src/main/java/com/yuyan/inputmethod/core/Kernel.kt"
decoding_file="$root/yuyansdk/src/main/java/com/yuyan/imemodule/service/DecodingInfo.kt"
listener_file="$root/yuyansdk/src/main/java/com/yuyan/imemodule/callback/CandidateViewListener.kt"
soft_bar_file="$root/yuyansdk/src/main/java/com/yuyan/imemodule/view/CandidatesBar.kt"
float_bar_file="$root/yuyansdk/src/main/java/com/yuyan/imemodule/candidate/FloatCandidateBar.kt"
composition_text_view_file="$root/yuyansdk/src/main/java/com/yuyan/imemodule/view/CompositionCaretTextView.kt"
composition_magnifier_file="$root/yuyansdk/src/main/java/com/yuyan/imemodule/view/CompositionCaretMagnifier.kt"
input_view_file="$root/yuyansdk/src/main/java/com/yuyan/imemodule/keyboard/InputView.kt"
candidate_view_file="$root/yuyansdk/src/main/java/com/yuyan/imemodule/candidate/CandidateView.kt"
behavior_cases_file="$root/tests/android_composition_editing_cases.tsv"
boundary_cases_file="$root/tests/android_composition_boundary_cases.tsv"

for file in "$rime_engine_file" "$kernel_file" "$decoding_file" "$listener_file" "$soft_bar_file" "$float_bar_file" "$composition_text_view_file" "$composition_magnifier_file" "$input_view_file" "$candidate_view_file"; do
  [[ -f "$file" ]] || {
    echo "Missing expected Android composition editing file: $file" >&2
    exit 1
  }
done

grep -q 'compositionCaret' "$rime_engine_file" || {
  echo "RimeEngine must own compositionCaret state" >&2
  exit 1
}

grep -q 'isFullKeyboardPinyinCompositionEditable' "$rime_engine_file" || {
  echo "RimeEngine must gate editing to full-keyboard Pinyin composition" >&2
  exit 1
}

grep -q 'SCHEMA_ZH_QWERTY' "$rime_engine_file" || {
  echo "RimeEngine full-keyboard gate must include legacy qwerty Pinyin schema" >&2
  exit 1
}

grep -q 'SCHEMA_FROST' "$rime_engine_file" || {
  echo "RimeEngine full-keyboard gate must include Qiwo frost full Pinyin schema" >&2
  exit 1
}

if grep -Eq 'SCHEMA_ZH_T9|SCHEMA_FROST_T9|SCHEMA_ZH_DOUBLE_LX17|SCHEMA_FROST_DOUBLE_PREFIX|SCHEMA_ZH_STROKE|SCHEMA_ZH_HANDWRITING' "$rime_engine_file"; then
  grep -q 'isFullKeyboardPinyinCompositionEditable' "$rime_engine_file" || {
    echo "Unsupported schema references must remain outside composition editing enablement" >&2
    exit 1
  }
fi

grep -q 'insertCompositionAtCaret' "$rime_engine_file" || {
  echo "RimeEngine must expose insertion at composition caret" >&2
  exit 1
}

grep -q 'deleteCompositionBeforeCaret' "$rime_engine_file" || {
  echo "RimeEngine must expose deletion before composition caret" >&2
  exit 1
}

grep -Eq 'Rime\.replaceKey\(.*0, key' "$rime_engine_file" || {
  echo "Caret insertion must use Rime.replaceKey(caret, 0, key)" >&2
  exit 1
}

grep -Eq 'Rime\.replaceKey\(.*1, ""' "$rime_engine_file" || {
  echo "Caret backspace must use Rime.replaceKey(caret - 1, 1, empty replacement)" >&2
  exit 1
}

for symbol in setCompositionCaret clearCompositionCaret insertCompositionAtCaret deleteCompositionBeforeCaret compositionTextForDisplay; do
  grep -q "$symbol" "$kernel_file" || {
    echo "Kernel must expose $symbol pass-through" >&2
    exit 1
  }
  grep -q "$symbol" "$decoding_file" || {
    echo "DecodingInfo must expose $symbol" >&2
    exit 1
  }
done

for symbol in isCompositionEditingAvailable compositionTextForEditing compositionCaretBoundary; do
  grep -q "$symbol" "$rime_engine_file" || {
    echo "RimeEngine must expose read-only $symbol" >&2
    exit 1
  }
  grep -q "$symbol" "$kernel_file" || {
    echo "Kernel must expose read-only $symbol pass-through" >&2
    exit 1
  }
  grep -q "$symbol" "$decoding_file" || {
    echo "DecodingInfo must expose read-only $symbol" >&2
    exit 1
  }
done

grep -q 'class CompositionCaretTextView' "$composition_text_view_file" || {
  echo "CompositionCaretTextView must define the shared composition caret text view" >&2
  exit 1
}

for symbol in setComposition resolveCaretBoundary onDraw drawLine; do
  grep -q "$symbol" "$composition_text_view_file" || {
    echo "CompositionCaretTextView must implement $symbol" >&2
    exit 1
  }
done

grep -q 'class CompositionCaretMagnifier' "$composition_magnifier_file" || {
  echo "CompositionCaretMagnifier must define the enlarged composition affordance" >&2
  exit 1
}

for symbol in PopupWindow show update dismiss finalizeCaret; do
  grep -q "$symbol" "$composition_magnifier_file" || {
    echo "CompositionCaretMagnifier must implement $symbol" >&2
    exit 1
  }
done

grep -q 'onClickCompositionCaret' "$listener_file" || {
  echo "CandidateViewListener must expose onClickCompositionCaret" >&2
  exit 1
}

for file in "$soft_bar_file" "$float_bar_file"; do
  grep -q 'setOnTouchListener' "$file" || {
    echo "$(basename "$file") must handle composition tap events" >&2
    exit 1
  }
  grep -q 'CompositionCaretTextView' "$file" || {
    echo "$(basename "$file") must render composition through CompositionCaretTextView" >&2
    exit 1
  }
  grep -q 'CompositionCaretMagnifier' "$file" || {
    echo "$(basename "$file") must use CompositionCaretMagnifier for enlarged caret placement" >&2
    exit 1
  }
  grep -q 'DecodingInfo\.compositionTextForEditing' "$file" || {
    echo "$(basename "$file") must render raw composition text for editing" >&2
    exit 1
  }
  grep -q 'DecodingInfo\.compositionCaretBoundary' "$file" || {
    echo "$(basename "$file") must render the current composition caret boundary" >&2
    exit 1
  }
  grep -q 'DecodingInfo\.isCompositionEditingAvailable' "$file" || {
    echo "$(basename "$file") must gate magnifier activation to editable full-keyboard Pinyin composition" >&2
    exit 1
  }
  grep -q 'onClickCompositionCaret' "$file" || {
    echo "$(basename "$file") must notify composition caret clicks" >&2
    exit 1
  }
  if grep -q 'displayText\.replace("|", "")' "$file"; then
    echo "$(basename "$file") must not derive hit-test text by stripping a visible caret marker" >&2
    exit 1
  fi
done

for file in "$input_view_file" "$candidate_view_file"; do
  grep -q 'onClickCompositionCaret' "$file" || {
    echo "$(basename "$file") must implement composition caret click callback" >&2
    exit 1
  }
  grep -q 'insertCompositionAtCaret' "$file" || {
    echo "$(basename "$file") must route alphabetic input through caret insertion" >&2
    exit 1
  }
  grep -q 'deleteCompositionBeforeCaret' "$file" || {
    echo "$(basename "$file") must route backspace through caret deletion" >&2
    exit 1
  }
done

grep -q 'clearCompositionCaret' "$input_view_file" || {
  echo "InputView must clear composition caret on reset/commit paths" >&2
  exit 1
}

grep -q 'clearCompositionCaret' "$candidate_view_file" || {
  echo "CandidateView must clear composition caret on reset/commit paths" >&2
  exit 1
}

[[ -f "$behavior_cases_file" ]] || {
  echo "Missing Android composition editing behavior cases: $behavior_cases_file" >&2
  exit 1
}

[[ -f "$boundary_cases_file" ]] || {
  echo "Missing Android composition boundary cases: $boundary_cases_file" >&2
  exit 1
}

composition_clamp_caret() {
  local caret="$1"
  local text="$2"
  local length="${#text}"
  if (( caret < 0 )); then
    echo 0
  elif (( caret > length )); then
    echo "$length"
  else
    echo "$caret"
  fi
}

composition_display_with_caret() {
  local text="$1"
  local caret="$2"
  caret="$(composition_clamp_caret "$caret" "$text")"
  printf '%s|%s' "${text:0:caret}" "${text:caret}"
}

composition_apply_action() {
  local text_ref="$1"
  local caret_ref="$2"
  local action="$3"
  local current_text="${!text_ref}"
  local current_caret="${!caret_ref}"
  current_caret="$(composition_clamp_caret "$current_caret" "$current_text")"

  case "$action" in
    none)
      ;;
    backspace)
      if (( current_caret > 0 )); then
        current_text="${current_text:0:current_caret-1}${current_text:current_caret}"
        current_caret=$((current_caret - 1))
      fi
      ;;
    insert:?)
      local key="${action#insert:}"
      current_text="${current_text:0:current_caret}${key}${current_text:current_caret}"
      current_caret=$((current_caret + ${#key}))
      ;;
    *)
      echo "Unknown composition behavior action: $action" >&2
      exit 1
      ;;
  esac

  printf -v "$text_ref" '%s' "$current_text"
  printf -v "$caret_ref" '%s' "$current_caret"
}

case_count=0
while IFS=$'\t' read -r name initial_text initial_caret actions expected_text expected_caret expected_display; do
  [[ "$name" == "name" ]] && continue
  [[ -n "$name" ]] || continue
  text="$initial_text"
  caret="$(composition_clamp_caret "$initial_caret" "$text")"
  IFS=',' read -ra action_list <<< "$actions"
  for action in "${action_list[@]}"; do
    composition_apply_action text caret "$action"
  done
  display="$(composition_display_with_caret "$text" "$caret")"
  if [[ "$text" != "$expected_text" || "$caret" != "$expected_caret" || "$display" != "$expected_display" ]]; then
    echo "Behavior case failed: $name" >&2
    echo "  got text=$text caret=$caret display=$display" >&2
    echo "  expected text=$expected_text caret=$expected_caret display=$expected_display" >&2
    exit 1
  fi
  case_count=$((case_count + 1))
done < "$behavior_cases_file"

if (( case_count < 6 )); then
  echo "Expected at least 6 Android composition editing behavior cases" >&2
  exit 1
fi

composition_raw_from_display() {
  local display="$1"
  printf '%s' "${display//|/}"
}

composition_caret_from_display() {
  local display="$1"
  if [[ "$display" == *"|"* ]]; then
    local before="${display%%|*}"
    before="${before//|/}"
    printf '%s' "${#before}"
  else
    printf '%s' ""
  fi
}

boundary_case_count=0
while IFS=$'\t' read -r name display_text target_boundary actions expected_text expected_caret forbidden_text; do
  [[ "$name" == "name" ]] && continue
  [[ -n "$name" ]] || continue
  text="$(composition_raw_from_display "$display_text")"
  marker_caret="$(composition_caret_from_display "$display_text")"
  if [[ -n "$marker_caret" ]]; then
    caret="$(composition_clamp_caret "$marker_caret" "$text")"
  else
    caret="$(composition_clamp_caret "$target_boundary" "$text")"
  fi
  IFS=',' read -ra action_list <<< "$actions"
  for action in "${action_list[@]}"; do
    composition_apply_action text caret "$action"
  done
  if [[ "$text" != "$expected_text" || "$caret" != "$expected_caret" ]]; then
    echo "Boundary case failed: $name" >&2
    echo "  got text=$text caret=$caret" >&2
    echo "  expected text=$expected_text caret=$expected_caret" >&2
    exit 1
  fi
  if [[ -n "$forbidden_text" && "$text" == "$forbidden_text" ]]; then
    echo "Boundary case produced forbidden text: $name -> $text" >&2
    exit 1
  fi
  boundary_case_count=$((boundary_case_count + 1))
done < "$boundary_cases_file"

if (( boundary_case_count < 5 )); then
  echo "Expected at least 5 Android composition boundary cases" >&2
  exit 1
fi
