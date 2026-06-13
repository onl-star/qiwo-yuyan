#!/usr/bin/env bash
set -euo pipefail

root="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/.." && pwd)"
prefs_file="$root/yuyansdk/src/main/java/com/yuyan/imemodule/prefs/AppPrefs.kt"
constant_file="$root/yuyansdk/src/main/java/com/yuyan/imemodule/application/CustomConstant.kt"
launcher_file="$root/yuyansdk/src/main/java/com/yuyan/imemodule/application/Launcher.kt"
switcher_file="$root/yuyansdk/src/main/java/com/yuyan/imemodule/manager/InputModeSwitcher.kt"
settings_container_file="$root/yuyansdk/src/main/java/com/yuyan/imemodule/keyboard/container/SettingsContainer.kt"
menu_adapter_file="$root/yuyansdk/src/main/java/com/yuyan/imemodule/adapter/MenuAdapter.kt"
candidates_menu_adapter_file="$root/yuyansdk/src/main/java/com/yuyan/imemodule/adapter/CandidatesMenuAdapter.kt"
kernel_file="$root/yuyansdk/src/main/java/com/yuyan/inputmethod/core/Kernel.kt"
rime_engine_file="$root/yuyansdk/src/main/java/com/yuyan/inputmethod/RimeEngine.kt"
workflow_file="$root/.github/workflows/build.yml"
frost_schema_file="$root/yuyansdk/src/main/assets/rime_frost/rime_frost.schema.yaml"
frost_android_schema_file="$root/yuyansdk/src/main/assets/rime_frost/rime_frost_android.schema.yaml"
frost_android_dict_file="$root/yuyansdk/src/main/assets/rime_frost/rime_frost_android.dict.yaml"

for file in "$prefs_file" "$constant_file" "$launcher_file" "$switcher_file" "$settings_container_file" "$menu_adapter_file" "$candidates_menu_adapter_file" "$kernel_file" "$rime_engine_file" "$workflow_file" "$frost_schema_file"; do
  [[ -f "$file" ]] || {
    echo "Missing Android full frost file: $file" >&2
    exit 1
  }
done

grep -q 'pinyinModeRime = string("input_method_pinyin_mode_rime", CustomConstant.SCHEMA_FROST)' "$prefs_file" || {
  echo "New installs must default pinyinModeRime to canonical rime_frost" >&2
  exit 1
}

grep -q 'SCHEMA_FROST = "rime_frost"' "$constant_file" || {
  echo "White frost schema id must be canonical rime_frost" >&2
  exit 1
}

if grep -q 'SCHEMA_FROST_FULL\|rime_frost_android\|stableSchemaForLegacyRime\|isUnsupportedFrostSchema' "$constant_file"; then
  echo "CustomConstant must not keep Android-only frost aliases or legacy fallback helpers" >&2
  exit 1
fi

if grep -q 'migrateUnsupportedFrostSchemasToLegacy\|Migrated unsupported Rime schema from' "$launcher_file"; then
  echo "Launcher must not migrate canonical frost schemas back to legacy pinyin" >&2
  exit 1
fi

awk '
  /schema_list:/ { in_list = 1; next }
  in_list && /- schema: rime_frost$/ { frost_line = NR }
  in_list && /- schema: pinyin$/ { pinyin_line = NR }
  in_list && /"menu\/page_size"/ { in_list = 0 }
  END { exit frost_line > 0 && (pinyin_line == 0 || frost_line < pinyin_line) ? 0 : 1 }
' "$launcher_file" || {
  echo "default.custom.yaml must expose canonical rime_frost before legacy pinyin" >&2
  exit 1
}

grep -q 'else -> CustomConstant.SCHEMA_FROST' "$switcher_file" || {
  echo "Full keyboard pinyin fallback must use canonical rime_frost by default" >&2
  exit 1
}

if grep -q 'CustomConstant.stableSchemaForLegacyRime' "$switcher_file" "$kernel_file"; then
  echo "Schema switching must not sanitize frost schemas to legacy pinyin" >&2
  exit 1
fi

grep -q 'keyboard_name_cn26_frost' "$settings_container_file" || {
  echo "Keyboard switch panel must expose the full frost entry" >&2
  exit 1
}

grep -q 'SkbMenuMode.Pinyin26Frost -> rimeValue == CustomConstant.SCHEMA_FROST' "$menu_adapter_file" || {
  echo "MenuAdapter must mark full frost as selectable" >&2
  exit 1
}

grep -q 'SkbMenuMode.Pinyin26Frost -> rimeValue == CustomConstant.SCHEMA_FROST' "$candidates_menu_adapter_file" || {
  echo "CandidatesMenuAdapter must mark full frost as selectable" >&2
  exit 1
}

if grep -q 'setValue(CustomConstant.SCHEMA_ZH_QWERTY)' "$kernel_file"; then
  echo "Kernel must not persist fallback to stable pinyin when canonical frost selection fails" >&2
  exit 1
fi

if grep -q 'rime_frost_android' "$workflow_file"; then
  echo "Android CI must not package Android-only frost compatibility schemas" >&2
  exit 1
fi

grep -q 'schema_id: rime_frost' "$frost_schema_file" || {
  echo "Full frost schema must declare schema_id rime_frost" >&2
  exit 1
}

grep -Eq 'lua_processor|lua_translator|lua_filter' "$frost_schema_file" || {
  echo "Full frost schema must keep Lua components enabled" >&2
  exit 1
}

if [[ -e "$frost_android_schema_file" || -e "$frost_android_dict_file" ]]; then
  echo "Android-only frost compatibility schema/dict files must be removed" >&2
  exit 1
fi

grep -q 'schema == CustomConstant.SCHEMA_ZH_QWERTY || schema == CustomConstant.SCHEMA_FROST' "$rime_engine_file" || {
  echo "Composition editing must treat canonical frost as an active full-pinyin schema" >&2
  exit 1
}
