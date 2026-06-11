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

for symbol in segmentations normalizeInput; do
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

for symbol in isPinyinSegmentationSelectorAvailable pinyinSegmentationChoices activePinyinSegmentationIndex selectPinyinSegmentation clearPinyinSegmentation refreshPinyinSegmentation; do
  grep -q "$symbol" "$rime_engine_file" || {
    echo "RimeEngine must expose or use $symbol" >&2
    exit 1
  }
done

grep -q 'PinyinSegmentation.segmentations' "$rime_engine_file" || {
  echo "RimeEngine must compute selector choices through PinyinSegmentation.segmentations" >&2
  exit 1
}

grep -q 'isFullKeyboardPinyinCompositionEditable' "$rime_engine_file" || {
  echo "RimeEngine must gate selector state to full-keyboard Pinyin composition" >&2
  exit 1
}

grep -Eq 'Rime\.replaceKey\(0,.*selected' "$rime_engine_file" || {
  echo "RimeEngine selection must refresh candidates by replacing the current composition with the selected segmentation" >&2
  exit 1
}

for file in "$kernel_file" "$decoding_file"; do
  for symbol in isPinyinSegmentationSelectorAvailable pinyinSegmentationChoices activePinyinSegmentationIndex selectPinyinSegmentation; do
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

for symbol in isPinyinSegmentationSelectorAvailable pinyinSegmentationChoices activePinyinSegmentationIndex onClickPinyinSegmentation; do
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
  [ao]=1 [gao]=1 [hao]=1 [man]=1 [mang]=1 [ni]=1
)

segment_case() {
  local raw="${1//\'/}"
  raw="$(printf '%s' "$raw" | tr '[:upper:]' '[:lower:]')"
  [[ "$raw" =~ ^[a-z]+$ ]] || {
    printf ''
    return
  }
  case "$raw" in
    mangao) printf "man'gao,mang'ao" ;;
    nihao) printf '' ;;
    *) printf '' ;;
  esac
}

tsv_field() {
  local line="$1"
  local index="$2"
  printf '%s\n' "$line" | awk -F '\t' -v index="$index" '{ print $index }'
}

case_count=0
while IFS= read -r line; do
  name="$(tsv_field "$line" 1)"
  mode="$(tsv_field "$line" 2)"
  raw="$(tsv_field "$line" 3)"
  expected_choices="$(tsv_field "$line" 4)"
  expected_active="$(tsv_field "$line" 5)"
  selected_index="$(tsv_field "$line" 6)"
  expected_selected="$(tsv_field "$line" 7)"
  expected_visible="$(tsv_field "$line" 8)"
  [[ "$name" == "name" ]] && continue
  [[ -n "$name" ]] || continue
  ((case_count += 1))
  actual_choices="$(segment_case "$raw")"
  if [[ "$mode" != "full_pinyin" ]]; then
    actual_choices=""
  fi
  if [[ "$actual_choices" != "$expected_choices" ]]; then
    echo "Fixture mismatch for $name: expected choices '$expected_choices', got '$actual_choices'" >&2
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
done < "$cases_file"

(( case_count >= 5 )) || {
  echo "Expected at least five Android Pinyin segmentation selector cases" >&2
  exit 1
}
