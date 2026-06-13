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

for symbol in completePaths maxTotalEditCost maxBeamWidth; do
  grep -q "$symbol" "$resolver_file" || {
    echo "PinyinSegmentation must implement bounded full-input reachability symbol $symbol" >&2
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

if grep -q 'confirmedPinyinSyllables.isEmpty() && choices.size <= 1' "$rime_engine_file"; then
  echo "RimeEngine must not hide first-step single-choice segmentations" >&2
  exit 1
fi

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

legal_syllables=(ao gao guo hao ma mai man mang mi min ming ni)
max_total_edit_cost=2
max_edit_cost_per_syllable=2

edit_distance_bounded() {
  local left="$1"
  local right="$2"
  local max_cost="$3"
  if [[ "$left" == "$right" ]]; then
    printf '0'
    return
  fi
  local left_len="${#left}"
  local right_len="${#right}"
  local diff=$(( left_len > right_len ? left_len - right_len : right_len - left_len ))
  if (( diff > max_cost )); then
    printf '%s' "$((max_cost + 1))"
    return
  fi
  local i=0 j=0 edits=0
  while (( i < left_len || j < right_len )); do
    if [[ "${left:i:1}" == "${right:j:1}" ]]; then
      i=$((i + 1))
      j=$((j + 1))
      continue
    fi
    edits=$((edits + 1))
    if (( edits > max_cost )); then
      printf '%s' "$edits"
      return
    fi
    if (( left_len > right_len )); then
      i=$((i + 1))
    elif (( right_len > left_len )); then
      j=$((j + 1))
    else
      i=$((i + 1))
      j=$((j + 1))
    fi
  done
  printf '%s' "$edits"
}

choice_details() {
  local raw="${1//\'/}"
  local confirmed="$2"
  raw="$(printf '%s' "$raw" | tr '[:upper:]' '[:lower:]')"
  [[ "$raw" =~ ^[a-z]+$ ]] || return
  local consumed=0
  if [[ -n "$confirmed" ]]; then
    IFS=',' read -ra confirmed_parts <<< "$confirmed"
    for part in "${confirmed_parts[@]}"; do
      consumed=$((consumed + ${#part}))
    done
  fi

  collect_paths "$raw" "$consumed" 0 "" 0 0 |
    awk -F '\t' '
      NF == 4 {
        label = $1
        exactRank = (label ~ /\*$/) ? 1 : 0
        key = label
        sortKey = exactRank ":" $4 ":" $3 ":" $2 ":" label
        if (!(key in best) || sortKey < best[key]) {
          best[key] = sortKey
          row[key] = $0
        }
      }
      END {
        for (key in row) print row[key]
      }
    ' |
    sort -t "$(printf '\t')" -k1,1 |
    awk -F '\t' '
      {
        exactRank = ($1 ~ /\*$/) ? 1 : 0
        print exactRank "\t" $4 "\t" $3 "\t" $2 "\t" $1
      }
    ' |
    sort -t "$(printf '\t')" -k1,1n -k2,2n -k3,3n -k4,4n -k5,5 |
    awk -F '\t' '{ print $5 "\t" $4 "\t" $3 "\t" $2 }'
}

collect_paths() {
  local raw="$1"
  local offset="$2"
  local cost_so_far="$3"
  local first_label="$4"
  local first_source_length="$5"
  local first_cost="$6"
  if (( offset == ${#raw} )); then
    [[ -n "$first_label" ]] && printf '%s\t%s\t%s\t%s\n' "$first_label" "$first_source_length" "$first_cost" "$cost_so_far"
    return
  fi
  local remaining="${raw:offset}"
  local syllable
  local source_len
  for syllable in "${legal_syllables[@]}"; do
    local syllable_len="${#syllable}"
    local min_len=$((syllable_len - max_edit_cost_per_syllable))
    local max_len=$((syllable_len + max_edit_cost_per_syllable))
    (( min_len < 1 )) && min_len=1
    (( max_len > ${#remaining} )) && max_len="${#remaining}"
    for ((source_len = min_len; source_len <= max_len; source_len += 1)); do
      local source="${remaining:0:source_len}"
      local edge_max_cost="$max_edit_cost_per_syllable"
      if [[ -z "$first_label" ]]; then
        edge_max_cost=1
      fi
      local edit_cost
      edit_cost="$(edit_distance_bounded "$source" "$syllable" "$edge_max_cost")"
      (( edit_cost <= edge_max_cost )) || continue
      if (( edit_cost > 0 )) && [[ -z "$first_label" && "${source:0:1}" != "${syllable:0:1}" ]]; then
        continue
      fi
      if (( edit_cost > 0 )) && [[ -z "$first_label" && "$source_len" -ne "$syllable_len" ]]; then
        continue
      fi
      if (( edit_cost > 0 && source_len > syllable_len )) && [[ "$source" == "$syllable"* ]]; then
        continue
      fi
      local next_cost=$((cost_so_far + edit_cost))
      (( next_cost <= max_total_edit_cost )) || continue
      local label="$syllable"
      (( edit_cost == 0 )) || label="$syllable*"
      local next_first_label="$first_label"
      local next_first_source_length="$first_source_length"
      local next_first_cost="$first_cost"
      if [[ -z "$next_first_label" ]]; then
        next_first_label="$label"
        next_first_source_length="$source_len"
        next_first_cost="$edit_cost"
      fi
      collect_paths "$raw" "$((offset + source_len))" "$next_cost" "$next_first_label" "$next_first_source_length" "$next_first_cost"
    done
  done
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
    local source_length
    source_length="$(choice_details "$raw" "$confirmed" | awk -F '\t' -v choice="$choice" '$1 == choice { print $2; exit }')"
    [[ -n "$source_length" ]] || source_length="${#choice}"
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
  local details
  details="$(choice_details "$raw" "$confirmed")"
  local best_cost
  best_cost="$(printf '%s\n' "$details" | awk -F '\t' 'NF == 4 { if (min == "" || $4 < min) min = $4 } END { print min }')"
  printf '%s\n' "$details" |
    awk -F '\t' -v best_cost="$best_cost" '
      NF == 4 {
        label = $1
        first_cost = $3
        total_cost = $4
        if (best_cost == 0 && total_cost > 0) next
        print label
      }
    ' |
    awk 'NF && !seen[$0]++' |
    paste -sd ',' -
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
