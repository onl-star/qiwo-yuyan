#!/usr/bin/env bash
set -euo pipefail

root="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/.." && pwd)"
asset_dir="$root/yuyansdk/src/main/assets/rime_frost"
schema_file="$asset_dir/rime_frost.schema.yaml"
dict_file="$asset_dir/rime_frost.dict.yaml"

for path in "$asset_dir" "$schema_file" "$dict_file"; do
  [[ -e "$path" ]] || {
    echo "Missing full frost resource path: $path" >&2
    exit 1
  }
done

required_dirs=(
  "cn_dicts"
  "cn_dicts_common"
  "cn_dicts_cell"
  "en_dicts"
  "lua"
  "opencc"
)

for dir_name in "${required_dirs[@]}"; do
  [[ -d "$asset_dir/$dir_name" ]] || {
    echo "Missing required full frost resource directory: $dir_name" >&2
    exit 1
  }
done

required_files=(
  "default.yaml"
  "key_bindings.yaml"
  "punctuation.yaml"
  "symbols.yaml"
  "custom_phrase.txt"
  "melt_eng.schema.yaml"
  "melt_eng.dict.yaml"
  "radical_pinyin.schema.yaml"
  "radical_pinyin.dict.yaml"
  "rime_frost_aux.schema.yaml"
  "rime_frost_aux.dict.yaml"
)

for file_name in "${required_files[@]}"; do
  [[ -f "$asset_dir/$file_name" ]] || {
    echo "Missing required full frost resource file: $file_name" >&2
    exit 1
  }
done

for section in config_version schema_list punctuator recognizer key_binder; do
  grep -q "^${section}:" "$asset_dir/default.yaml" || {
    echo "Full frost default.yaml must include root section: $section" >&2
    exit 1
  }
done

grep -q '__include: default:/punctuator/full_shape' "$schema_file" || {
  echo "Full frost schema must continue to inherit punctuator settings from default.yaml" >&2
  exit 1
}

while IFS= read -r table_name; do
  [[ -n "$table_name" ]] || continue
  table_file="$asset_dir/${table_name}.dict.yaml"
  [[ -f "$table_file" ]] || {
    echo "Missing dictionary imported by rime_frost.dict.yaml: ${table_name}.dict.yaml" >&2
    exit 1
  }
done < <(
  awk '
    /^[[:space:]]*import_tables:/ { in_tables = 1; next }
    in_tables && /^[^[:space:]#]/ { in_tables = 0 }
    in_tables && /^[[:space:]]*-[[:space:]]*/ {
      line = $0
      sub(/#.*/, "", line)
      sub(/^[[:space:]]*-[[:space:]]*/, "", line)
      sub(/[[:space:]]*$/, "", line)
      if (line != "") print line
    }
  ' "$dict_file"
)

for lua_name in select_character date_translator lunar unicode number_translator force_gc calculator is_in_user_dict corrector autocap_filter v_filter pin_cand_filter reduce_english_filter aux_lookup_filter; do
  [[ -f "$asset_dir/lua/${lua_name}.lua" ]] || {
    echo "Missing Lua component required by full frost schema: lua/${lua_name}.lua" >&2
    exit 1
  }
done

for opencc_name in emoji.json chinese_english.json moqi_chaifen.json moqi_chaifen_all.json martian.json t2s.json s2t.json; do
  [[ -f "$asset_dir/opencc/${opencc_name}" ]] || {
    echo "Missing OpenCC config required by full frost resources: opencc/${opencc_name}" >&2
    exit 1
  }
done

if compgen -G "$asset_dir/rime_frost_android.*.yaml" >/dev/null; then
  echo "Android-only frost compatibility schema/dict files must not remain in full frost resources" >&2
  exit 1
fi
