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
composition_mapping_file="$root/yuyansdk/src/main/java/com/yuyan/inputmethod/util/CompositionEditMapping.kt"
hidden_separator_cases_file="$root/tests/android_composition_hidden_separator_cases.tsv"

for file in "$rime_engine_file" "$kernel_file" "$decoding_file" "$listener_file" "$soft_bar_file" "$float_bar_file" "$composition_text_view_file" "$composition_magnifier_file" "$input_view_file" "$candidate_view_file"; do
  [[ -f "$file" ]] || {
    echo "Missing expected Android composition editing file: $file" >&2
    exit 1
  }
done

[[ ! -f "$composition_mapping_file" ]] || {
  echo "CompositionEditMapping belongs to the reverted hidden-segmentation edit path" >&2
  exit 1
}

[[ ! -f "$hidden_separator_cases_file" ]] || {
  echo "Hidden separator cases belong to the reverted edit path" >&2
  exit 1
}

grep -q 'private var compositionCaret: Int?' "$rime_engine_file" || {
  echo "RimeEngine must keep the composition caret state used by the editor UI" >&2
  exit 1
}

grep -q 'compositionCaretActive' "$rime_engine_file" || {
  echo "RimeEngine must track active composition editing state" >&2
  exit 1
}

for forbidden in CompositionEditMapping compositionCaretFallback compositionCaretRimeBacked replaceCompositionWithEditableText logicalSeparatorTransparentComposition 'Rime.compositionCaret' 'Rime.setCompositionCaret'; do
  if grep -q "$forbidden" "$rime_engine_file"; then
    echo "RimeEngine must not use reverted composition editing path: $forbidden" >&2
    exit 1
  fi
done

grep -q 'QwertyPinYinUtils.getQwertyComposition' "$rime_engine_file" || {
  echo "RimeEngine must keep the pre-rework qwerty composition display path" >&2
  exit 1
}

for symbol in isFullKeyboardPinyinCompositionEditable setCompositionCaret clearCompositionCaret insertCompositionAtCaret deleteCompositionBeforeCaret compositionTextForDisplay compositionTextForCaretDisplay compositionTextForEditing compositionCaretBoundary; do
  grep -q "$symbol" "$rime_engine_file" || {
    echo "RimeEngine must expose $symbol" >&2
    exit 1
  }
done

for symbol in SCHEMA_ZH_QWERTY SCHEMA_FROST; do
  grep -q "$symbol" "$rime_engine_file" || {
    echo "RimeEngine full-keyboard gate must include $symbol" >&2
    exit 1
  }
done

awk '
  /fun insertCompositionAtCaret\(/ { in_func = 1 }
  in_func && /moveNativeCompositionCaret/ { moved = 1 }
  in_func && /processCompositionTextKey/ { typed = 1 }
  in_func && /Rime\.replaceKey|replaceCompositionWithEditableText|CompositionEditMapping|Rime\.setCompositionCaret/ { exit 1 }
  in_func && /^    fun / && !/fun insertCompositionAtCaret\(/ { in_func = 0 }
  END { exit moved && typed ? 0 : 1 }
' "$rime_engine_file" || {
  echo "RimeEngine.insertCompositionAtCaret must move the native Rime caret before sending the typed key" >&2
  exit 1
}

awk '
  /fun deleteCompositionBeforeCaret\(/ { in_func = 1 }
  in_func && /if \(caret <= 0\) return true/ { guarded = 1 }
  in_func && /moveNativeCompositionCaret\(caret\)/ { moved = 1 }
  in_func && /Rime\.processKey\(getRimeKeycodeByName\("BackSpace"\), 0\)/ { deleted = 1 }
  in_func && /Rime\.replaceKey|replaceCompositionWithEditableText|CompositionEditMapping|Rime\.setCompositionCaret/ { exit 1 }
  in_func && /^    private fun / { in_func = 0 }
  END { exit guarded && moved && deleted ? 0 : 1 }
' "$rime_engine_file" || {
  echo "RimeEngine.deleteCompositionBeforeCaret must consume boundary deletes and use native Rime backspace" >&2
  exit 1
}

grep -q 'private fun moveNativeCompositionCaret' "$rime_engine_file" || {
  echo "RimeEngine must isolate native Rime caret movement" >&2
  exit 1
}

for symbol in setCompositionCaret clearCompositionCaret insertCompositionAtCaret deleteCompositionBeforeCaret compositionTextForDisplay compositionTextForCaretDisplay compositionTextForEditing compositionCaretBoundary isCompositionEditingAvailable; do
  grep -q "$symbol" "$kernel_file" || {
    echo "Kernel must expose $symbol pass-through" >&2
    exit 1
  }
  grep -q "$symbol" "$decoding_file" || {
    echo "DecodingInfo must expose $symbol" >&2
    exit 1
  }
done

grep -q 'class CompositionCaretTextView' "$composition_text_view_file" || {
  echo "CompositionCaretTextView must keep the inline composition caret view" >&2
  exit 1
}

for symbol in setComposition resolveCaretBoundary onDraw drawLine setHorizontallyScrolling textLayoutTop verticalTextOffset; do
  grep -q "$symbol" "$composition_text_view_file" || {
    echo "CompositionCaretTextView must keep same-line caret rendering via $symbol" >&2
    exit 1
  }
done

grep -q 'class CompositionCaretMagnifier' "$composition_magnifier_file" || {
  echo "CompositionCaretMagnifier must define the enlarged editing affordance" >&2
  exit 1
}

for symbol in PopupWindow show update dismiss finalizeCaret isShowing setOnCaretChanged handlePreviewTouch canonicalCaret; do
  grep -q "$symbol" "$composition_magnifier_file" || {
    echo "CompositionCaretMagnifier must keep persistent popup editing support through $symbol" >&2
    exit 1
  }
done

awk '
  /fun finalizeCaret/ { in_func = 1 }
  in_func && /updatePreview/ { updated = 1 }
  in_func && /val boundary = anchor\.resolveCaretBoundary/ { saw_boundary = 1 }
  in_func && saw_boundary && /dismiss\(\)/ { exit 1 }
  in_func && /^    val / { in_func = 0 }
  END { exit updated ? 0 : 1 }
' "$composition_magnifier_file" || {
  echo "CompositionCaretMagnifier.finalizeCaret must keep the popup open after finger up" >&2
  exit 1
}

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
    echo "$(basename "$file") must use CompositionCaretMagnifier" >&2
    exit 1
  }
  grep -q 'DecodingInfo\.compositionTextForCaretDisplay' "$file" || {
    echo "$(basename "$file") must render the composition text used for caret hit testing" >&2
    exit 1
  }
  grep -q 'DecodingInfo\.compositionCaretBoundary' "$file" || {
    echo "$(basename "$file") must render the current caret boundary" >&2
    exit 1
  }
  grep -q 'DecodingInfo\.isCompositionEditingAvailable' "$file" || {
    echo "$(basename "$file") must gate magnifier activation" >&2
    exit 1
  }
  grep -q 'compositionMagnifier.setOnCaretChanged' "$file" || {
    echo "$(basename "$file") must keep popup caret updates active while open" >&2
    exit 1
  }
  grep -q 'dispatchCompositionCaret' "$file" || {
    echo "$(basename "$file") must update active caret during repeated tap/drag" >&2
    exit 1
  }
  awk '
    /MotionEvent.ACTION_UP ->/ { in_up = 1; next }
    in_up && /compositionMagnifier\.update/ { updated = 1 }
    in_up && /compositionMagnifier\.dismiss/ { exit 1 }
    in_up && /^[[:space:]]*}/ { in_up = 0 }
    END { exit updated ? 0 : 1 }
  ' "$file" || {
    echo "$(basename "$file") must keep the magnifier open after finger up" >&2
    exit 1
  }
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
  grep -q 'compositionInsertLabel' "$file" || {
    echo "$(basename "$file") must route user-entered composition separators through targeted caret insertion" >&2
    exit 1
  }
  awk '
    /keyCode == KeyEvent\.KEYCODE_DEL ->/ { in_delete = 1; next }
    in_delete && /dismissCompositionMagnifier\(\)/ { exit 1 }
    in_delete && /^[[:space:]]*}/ { in_delete = 0 }
    END { exit 0 }
  ' "$file" || {
    echo "$(basename "$file") must keep the composition editor open during targeted backspace" >&2
    exit 1
  }
  awk '
    /Character\.isLetter/ { in_insert = 1; next }
    in_insert && /dismissCompositionMagnifier\(\)/ { exit 1 }
    in_insert && /^[[:space:]]*}/ { in_insert = 0 }
    END { exit 0 }
  ' "$file" || {
    echo "$(basename "$file") must keep the composition editor open during targeted character input" >&2
    exit 1
  }
done

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
      echo "Unknown composition action: $action" >&2
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
  echo "Expected at least 6 Android composition behavior cases" >&2
  exit 1
fi

composition_display_without_marker() {
  local display="$1"
  printf '%s' "${display//|/}"
}

composition_caret_from_display_marker() {
  local display="$1"
  local fallback="$2"
  if [[ "$display" == *"|"* ]]; then
    local before="${display%%|*}"
    before="${before//|/}"
    printf '%s' "${#before}"
  else
    printf '%s' "$fallback"
  fi
}

boundary_case_count=0
while IFS=$'\t' read -r name raw_text display_text target_boundary actions expected_text expected_caret forbidden_text; do
  [[ "$name" == "name" ]] && continue
  [[ -n "$name" ]] || continue
  text="$raw_text"
  caret="$(composition_caret_from_display_marker "$display_text" "$target_boundary")"
  display_text="$(composition_display_without_marker "$display_text")"
  caret="$(composition_clamp_caret "$caret" "$text")"
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
  if [[ -n "$forbidden_text" && "$forbidden_text" != "-" && "$text" == "$forbidden_text" ]]; then
    echo "Boundary case produced forbidden text: $name -> $text" >&2
    exit 1
  fi
  boundary_case_count=$((boundary_case_count + 1))
done < "$boundary_cases_file"

if (( boundary_case_count < 6 )); then
  echo "Expected at least 6 Android composition boundary cases" >&2
  exit 1
fi
