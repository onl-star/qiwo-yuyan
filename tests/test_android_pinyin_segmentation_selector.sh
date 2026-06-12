#!/usr/bin/env bash
set -euo pipefail

root="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/.." && pwd)"

cases_file="$root/tests/android_pinyin_segmentation_cases.tsv"
resolver_file="$root/yuyansdk/src/main/java/com/yuyan/inputmethod/util/PinyinSegmentation.kt"
rime_engine_file="$root/yuyansdk/src/main/java/com/yuyan/inputmethod/RimeEngine.kt"
kernel_file="$root/yuyansdk/src/main/java/com/yuyan/inputmethod/core/Kernel.kt"
decoding_file="$root/yuyansdk/src/main/java/com/yuyan/imemodule/service/DecodingInfo.kt"
container_file="$root/yuyansdk/src/main/java/com/yuyan/imemodule/keyboard/container/CandidatesContainer.kt"
prefix_adapter_file="$root/yuyansdk/src/main/java/com/yuyan/imemodule/adapter/PrefixAdapter.kt"
listener_file="$root/yuyansdk/src/main/java/com/yuyan/imemodule/callback/CandidateViewListener.kt"
input_view_file="$root/yuyansdk/src/main/java/com/yuyan/imemodule/keyboard/InputView.kt"
compact_bar_file="$root/yuyansdk/src/main/java/com/yuyan/imemodule/view/CandidatesBar.kt"

for file in "$cases_file" "$resolver_file" "$rime_engine_file" "$kernel_file" "$decoding_file" "$container_file" "$prefix_adapter_file" "$listener_file" "$input_view_file" "$compact_bar_file"; do
  [[ -f "$file" ]] || {
    echo "Missing expected Android Pinyin segmentation selector file: $file" >&2
    exit 1
  }
done

grep -q 'object PinyinSegmentation' "$resolver_file" || {
  echo "PinyinSegmentation must define an object helper" >&2
  exit 1
}

for symbol in segmentations normalizeInput currentStepChoices hasSegmentableTail editDistanceAtMostOne SyllableChoice; do
  grep -q "$symbol" "$resolver_file" || {
    echo "PinyinSegmentation must implement $symbol" >&2
    exit 1
  }
done

for syllable in man mang gao ao ni hao; do
  grep -q "\"$syllable\"" "$resolver_file" || {
    echo "PinyinSegmentation syllable table must include '$syllable'" >&2
    exit 1
  }
done

grep -q 'replace("'\''", "")' "$resolver_file" || {
  echo "PinyinSegmentation must normalize existing apostrophes out of raw input" >&2
  exit 1
}

grep -q 'lowercase' "$resolver_file" || {
  echo "PinyinSegmentation must lowercase ASCII raw input before resolving" >&2
  exit 1
}

grep -Eq 'all \{.*in '\''a'\''\.\.'\''z'\''|Regex' "$resolver_file" || {
  echo "PinyinSegmentation must reject non-letter raw input" >&2
  exit 1
}

grep -Eq 'size[[:space:]]*<=[[:space:]]*1|distinctChoices\.size' "$resolver_file" || {
  echo "PinyinSegmentation must hide single-choice segmentations" >&2
  exit 1
}

for symbol in isPinyinSegmentationSelectorAvailable pinyinSegmentationChoices pinyinSegmentationDisplayChoices activePinyinSegmentationIndex selectPinyinSegmentation clearPinyinSegmentation refreshPinyinSegmentation confirmedPinyinSyllables visibleRawPinyinComposition internalPinyinQuery pinyinSegmentationContextLabel; do
  grep -q "$symbol" "$rime_engine_file" || {
    echo "RimeEngine must expose or use $symbol" >&2
    exit 1
  }
done

grep -q 'PinyinSegmentation.currentStepChoices' "$rime_engine_file" || {
  echo "RimeEngine must compute selector choices through PinyinSegmentation.currentStepChoices" >&2
  exit 1
}

grep -q 'trimConfirmedPinyinToVisibleRaw' "$rime_engine_file" || {
  echo "RimeEngine must trim confirmed stepwise segmentation state after visible composition edits" >&2
  exit 1
}

for function_name in setCompositionCaret insertCompositionAtCaret deleteCompositionBeforeCaret; do
  awk -v function_name="$function_name" '
    $0 ~ "fun " function_name "\\(" { in_func = 1 }
    in_func && /restoreVisibleRawPinyinCompositionIfNeeded\(\)/ { restored = 1 }
    in_func && /^    fun / && $0 !~ "fun " function_name "\\(" { in_func = 0 }
    in_func && /^    private fun / { in_func = 0 }
    END { exit restored ? 0 : 1 }
  ' "$rime_engine_file" || {
    echo "RimeEngine.$function_name must restore visible raw Pinyin before native caret edits" >&2
    exit 1
  }
done

grep -q 'showComposition' "$rime_engine_file" || {
  echo "RimeEngine must consider showComposition when resolving the active segmentation" >&2
  exit 1
}

grep -q 'isFullKeyboardPinyinCompositionEditable' "$rime_engine_file" || {
  echo "RimeEngine must gate selector state to full-keyboard Pinyin composition" >&2
  exit 1
}

if grep -Eq 'Rime\.replaceKey\(0,.*selected' "$rime_engine_file"; then
  echo "RimeEngine selection must not treat a displayed choice as a complete selected segmentation path" >&2
  exit 1
fi

for file in "$kernel_file" "$decoding_file"; do
  for symbol in isPinyinSegmentationSelectorAvailable pinyinSegmentationChoices pinyinSegmentationDisplayChoices activePinyinSegmentationIndex selectPinyinSegmentation pinyinSegmentationContextLabel; do
    grep -q "$symbol" "$file" || {
      echo "$(basename "$file") must expose $symbol" >&2
      exit 1
    }
  done
done

grep -q 'activeIndex' "$prefix_adapter_file" || {
  echo "PrefixAdapter must accept an activeIndex for selected segmentation styling" >&2
  exit 1
}

grep -Eq 'setTypeface|Typeface|background|setBackground' "$prefix_adapter_file" || {
  echo "PrefixAdapter must visually distinguish the active segmentation" >&2
  exit 1
}

for symbol in isPinyinSegmentationSelectorAvailable pinyinSegmentationChoices pinyinSegmentationDisplayChoices activePinyinSegmentationIndex onClickPinyinSegmentation; do
  grep -q "$symbol" "$container_file" || {
    echo "CandidatesContainer must integrate segmentation selector symbol $symbol" >&2
    exit 1
  }
done

grep -q 'InputModeSwitcher.isChineseT9' "$container_file" || {
  echo "CandidatesContainer must preserve existing T9 prefix behavior separately" >&2
  exit 1
}

grep -q 'onClickPinyinSegmentation' "$listener_file" || {
  echo "CandidateViewListener must expose onClickPinyinSegmentation" >&2
  exit 1
}

grep -q 'onClickPinyinSegmentation' "$input_view_file" || {
  echo "InputView must implement segmentation click routing" >&2
  exit 1
}

grep -q 'selectPinyinSegmentation' "$input_view_file" || {
  echo "InputView must call DecodingInfo.selectPinyinSegmentation" >&2
  exit 1
}

if grep -q 'pinyinSegmentationChoices' "$compact_bar_file"; then
  echo "Compact CandidatesBar must not render the full segmentation selector" >&2
  exit 1
fi

declare -A legal_syllables=(
  [ao]=1 [gao]=1 [hao]=1 [man]=1 [mang]=1 [mi]=1 [min]=1 [ming]=1 [ni]=1
)

has_segmentable_tail() {
  local raw="$1"
  [[ -z "$raw" ]] && return 0
  case "$raw" in
    ao|gao|hao) return 0 ;;
    *) return 1 ;;
  esac
}

edit_distance_at_most_one() {
  local left="$1"
  local right="$2"
  if [[ "$left" == "$right" ]]; then
    return 0
  fi
  local left_len="${#left}"
  local right_len="${#right}"
  local diff=$(( left_len > right_len ? left_len - right_len : right_len - left_len ))
  (( diff <= 1 )) || return 1
  local i=0 j=0 edits=0
  while (( i < left_len || j < right_len )); do
    if [[ "${left:i:1}" == "${right:j:1}" ]]; then
      i=$((i + 1))
      j=$((j + 1))
      continue
    fi
    edits=$((edits + 1))
    (( edits <= 1 )) || return 1
    if (( left_len > right_len )); then
      i=$((i + 1))
    elif (( right_len > left_len )); then
      j=$((j + 1))
    else
      i=$((i + 1))
      j=$((j + 1))
    fi
  done
  return 0
}

join_confirmed_query() {
  local confirmed="$1"
  local choice="$2"
  local raw="$3"
  local consumed="$4"
  local normalized="${raw//\'/}"
  normalized="$(printf '%s' "$normalized" | tr '[:upper:]' '[:lower:]')"
  local remaining="${normalized:consumed}"
  local query="$choice"
  if [[ -n "$confirmed" ]]; then
    query="${confirmed//,/\'}'$choice"
  fi
  if [[ -n "$remaining" ]]; then
    query="$query'$remaining"
  fi
  printf '%s' "$query"
}

context_after_selection() {
  local confirmed="$1"
  local selected="$2"
  selected="${selected%\*}"
  if [[ -n "$confirmed" ]]; then
    printf "%s'%s" "${confirmed//,/\'}" "$selected"
  else
    printf "%s'" "$selected"
  fi
}

display_choices_for() {
  local raw="$1"
  local confirmed="$2"
  local choices="$3"
  local normalized="${raw//\'/}"
  normalized="$(printf '%s' "$normalized" | tr '[:upper:]' '[:lower:]')"
  local consumed=0
  local prefix=""
  if [[ -n "$confirmed" ]]; then
    IFS=',' read -ra confirmed_parts <<< "$confirmed"
    for part in "${confirmed_parts[@]}"; do
      consumed=$((consumed + ${#part}))
    done
    prefix="${confirmed//,/\'}'"
  fi
  local display=()
  IFS=',' read -ra choice_array <<< "$choices"
  for choice in "${choice_array[@]}"; do
    [[ -n "$choice" ]] || continue
    local syllable="${choice%\*}"
    local source_length="${#syllable}"
    if [[ "$choice" == *"*" ]]; then
      for candidate_source_length in $((${#syllable} - 1)) ${#syllable} $((${#syllable} + 1)); do
        (( candidate_source_length > 0 && candidate_source_length <= ${#normalized} - consumed )) || continue
        local source="${normalized:consumed:candidate_source_length}"
        [[ "$source" != "$syllable" ]] || continue
        has_segmentable_tail "${normalized:consumed + candidate_source_length}" || continue
        if edit_distance_at_most_one "$source" "$syllable"; then
          source_length="$candidate_source_length"
          break
        fi
      done
    fi
    local remaining_after="${normalized:consumed + source_length}"
    display+=("${prefix}[$choice]$(if [[ -n "$remaining_after" ]]; then printf "'%s" "$remaining_after"; fi)")
  done
  (IFS=','; printf '%s' "${display[*]}")
}

segment_case() {
  local raw="${1//\'/}"
  local confirmed="$2"
  raw="$(printf '%s' "$raw" | tr '[:upper:]' '[:lower:]')"
  [[ "$raw" =~ ^[a-z]+$ ]] || {
    printf ''
    return
  }
  local consumed=0
  if [[ -n "$confirmed" ]]; then
    IFS=',' read -ra confirmed_parts <<< "$confirmed"
    for part in "${confirmed_parts[@]}"; do
      consumed=$((consumed + ${#part}))
    done
  fi
  local remaining="${raw:consumed}"
  local exact=()
  local correction=()
  for syllable in "${!legal_syllables[@]}"; do
    if [[ "$remaining" == "$syllable"* ]] && has_segmentable_tail "${remaining:${#syllable}}"; then
      exact+=("$syllable")
    fi
  done
  if (( ${#exact[@]} != 1 )); then
  for syllable in "${!legal_syllables[@]}"; do
    local exact_syllable=false
    for exact_syllable_value in "${exact[@]}"; do
      if [[ "$exact_syllable_value" == "$syllable" ]]; then
        exact_syllable=true
      fi
    done
    [[ "$exact_syllable" == "false" ]] || continue
    for source_len in $((${#syllable} - 1)) ${#syllable} $((${#syllable} + 1)); do
      (( source_len > 0 && source_len <= ${#remaining} )) || continue
      source="${remaining:0:source_len}"
      [[ "$source" != "$syllable" ]] || continue
      has_segmentable_tail "${remaining:source_len}" || continue
      if edit_distance_at_most_one "$source" "$syllable"; then
        correction+=("$syllable*")
        break
      fi
    done
  done
  fi
  {
    printf '%s\n' "${exact[@]}" | sort
    printf '%s\n' "${correction[@]}" | sort
  } | awk 'NF && !seen[$0]++' | paste -sd ',' -
}

tsv_field() {
  local line="$1"
  local index="$2"
  printf '%s\n' "$line" | awk -F '\t' -v index="$index" '{ print $index }'
}

choice_index() {
  local choices="$1"
  local selected="$2"
  local index=0
  IFS=',' read -ra choice_array <<< "$choices"
  for choice in "${choice_array[@]}"; do
    if [[ "$choice" == "$selected" ]]; then
      printf '%s' "$index"
      return
    fi
    ((index += 1))
  done
  printf '%s' "-1"
}

choice_at() {
  local choices="$1"
  local index="$2"
  IFS=',' read -ra choice_array <<< "$choices"
  if [[ "$index" =~ ^[0-9]+$ && "$index" -lt "${#choice_array[@]}" ]]; then
    printf '%s' "${choice_array[$index]}"
    return
  fi
  printf ''
}

case_count=0
while IFS= read -r line; do
  name="$(tsv_field "$line" 1)"
  mode="$(tsv_field "$line" 2)"
  raw="$(tsv_field "$line" 3)"
  confirmed="$(tsv_field "$line" 4)"
  expected_choices="$(tsv_field "$line" 5)"
  expected_display_choices="$(tsv_field "$line" 6)"
  expected_active="$(tsv_field "$line" 7)"
  selected_index="$(tsv_field "$line" 8)"
  expected_selected="$(tsv_field "$line" 9)"
  expected_query="$(tsv_field "$line" 10)"
  expected_visible_raw="$(tsv_field "$line" 11)"
  expected_context="$(tsv_field "$line" 12)"
  expected_visible="$(tsv_field "$line" 13)"
  [[ "$name" == "name" ]] && continue
  [[ -n "$name" ]] || continue
  ((case_count += 1))
  actual_choices="$(segment_case "$raw" "$confirmed")"
  if [[ "$mode" != "full_pinyin" ]]; then
    actual_choices=""
  fi
  if [[ "$actual_choices" != "$expected_choices" ]]; then
    echo "Fixture mismatch for $name: expected choices '$expected_choices', got '$actual_choices'" >&2
    exit 1
  fi
  actual_display_choices=""
  if [[ -n "$actual_choices" ]]; then
    actual_display_choices="$(display_choices_for "$raw" "$confirmed" "$actual_choices")"
  fi
  if [[ "$actual_display_choices" != "$expected_display_choices" ]]; then
    echo "Fixture mismatch for $name: expected display choices '$expected_display_choices', got '$actual_display_choices'" >&2
    exit 1
  fi
  actual_active="-1"
  if [[ -n "$actual_choices" ]]; then
    actual_active="0"
  fi
  if [[ "$actual_active" != "$expected_active" ]]; then
    echo "Fixture mismatch for $name: expected active index '$expected_active', got '$actual_active'" >&2
    exit 1
  fi
  if [[ "$expected_visible" == "true" && -z "$expected_choices" ]]; then
    echo "Visible selector fixture $name must define expected choices" >&2
    exit 1
  fi
  if [[ "$expected_visible" == "false" && -n "$expected_selected" ]]; then
    echo "Hidden selector fixture $name must not define a selected segmentation" >&2
    exit 1
  fi
  actual_selected="$(choice_at "$actual_choices" "$selected_index")"
  if [[ "$actual_selected" != "$expected_selected" ]]; then
    echo "Fixture mismatch for $name: expected selected segmentation '$expected_selected', got '$actual_selected'" >&2
    exit 1
  fi
  if [[ -n "$actual_selected" ]]; then
    selected_syllable="${actual_selected%\*}"
    if [[ -n "$confirmed" ]]; then
      consumed=0
      IFS=',' read -ra confirmed_parts <<< "$confirmed"
      for part in "${confirmed_parts[@]}"; do
        consumed=$((consumed + ${#part}))
      done
      consumed=$((consumed + ${#selected_syllable}))
    else
      consumed="${#selected_syllable}"
    fi
    actual_query="$(join_confirmed_query "$confirmed" "$selected_syllable" "$raw" "$consumed")"
    actual_context="$(context_after_selection "$confirmed" "$actual_selected")"
    if [[ "$actual_query" != "$expected_query" ]]; then
      echo "Fixture mismatch for $name: expected internal query '$expected_query', got '$actual_query'" >&2
      exit 1
    fi
    if [[ "$raw" != "$expected_visible_raw" ]]; then
      echo "Fixture mismatch for $name: expected visible raw '$expected_visible_raw', got '$raw'" >&2
      exit 1
    fi
    if [[ "$actual_context" != "$expected_context" ]]; then
      echo "Fixture mismatch for $name: expected context '$expected_context', got '$actual_context'" >&2
      exit 1
    fi
  fi
done < "$cases_file"

(( case_count >= 5 )) || {
  echo "Expected at least five Android Pinyin segmentation selector cases" >&2
  exit 1
}
