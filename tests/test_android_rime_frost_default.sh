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
workflow_file="$root/.github/workflows/build.yml"
frost_android_schema_file="$root/yuyansdk/src/main/assets/rime_frost/rime_frost_android.schema.yaml"
frost_android_dict_file="$root/yuyansdk/src/main/assets/rime_frost/rime_frost_android.dict.yaml"

for file in "$prefs_file" "$constant_file" "$launcher_file" "$switcher_file" "$settings_container_file" "$menu_adapter_file" "$candidates_menu_adapter_file" "$kernel_file" "$workflow_file" "$frost_android_schema_file" "$frost_android_dict_file"; do
  [[ -f "$file" ]] || {
    echo "Missing Android Rime schema safety file: $file" >&2
    exit 1
  }
done

grep -q 'pinyinModeRime = string("input_method_pinyin_mode_rime", CustomConstant.SCHEMA_ZH_QWERTY)' "$prefs_file" || {
  echo "New installs must default pinyinModeRime to stable legacy pinyin" >&2
  exit 1
}

grep -q 'SCHEMA_FROST = "rime_frost_android"' "$constant_file" || {
  echo "White frost schema id must remain documented for future native backend work" >&2
  exit 1
}

grep -q 'SCHEMA_FROST_FULL = "rime_frost"' "$constant_file" || {
  echo "Full rime_frost schema id must remain documented for future librime-lua native backend work" >&2
  exit 1
}

grep -q 'CURRENT_RIME_DICT_DATA_VERSIOM = 2026061303' "$constant_file" || {
  echo "Rime data version must be bumped so existing frost installs receive the fallback migration" >&2
  exit 1
}

grep -q 'fun stableSchemaForLegacyRime(schema: String): String' "$constant_file" || {
  echo "Unsupported frost schema mapping must be centralized" >&2
  exit 1
}

grep -q 'schema == SCHEMA_FROST || schema == SCHEMA_FROST_FULL -> SCHEMA_ZH_QWERTY' "$constant_file" || {
  echo "Full-pinyin frost schemas must map to stable pinyin under the legacy backend" >&2
  exit 1
}

grep -q 'schema == SCHEMA_FROST_T9 -> SCHEMA_ZH_T9' "$constant_file" || {
  echo "Frost T9 schema must map to stable T9 under the legacy backend" >&2
  exit 1
}

grep -q 'schema.startsWith(SCHEMA_FROST_DOUBLE_PREFIX)' "$constant_file" || {
  echo "Frost double-pinyin schemas must map to stable double-pinyin under the legacy backend" >&2
  exit 1
}

grep -q 'migrateUnsupportedFrostSchemasToLegacy()' "$launcher_file" || {
  echo "Launcher must migrate unsupported frost schemas away from legacy Rime" >&2
  exit 1
}

grep -q 'Migrated unsupported Rime schema from' "$launcher_file" || {
  echo "Frost fallback migration must be visible in logcat" >&2
  exit 1
}

awk '
  /schema_list:/ { in_list = 1; next }
  in_list && /- schema: rime_frost/ { frost_line = NR }
  in_list && /- schema: pinyin$/ { pinyin_line = NR }
  in_list && /"menu\/page_size"/ { in_list = 0 }
  END { exit pinyin_line > 0 && frost_line == 0 ? 0 : 1 }
' "$launcher_file" || {
  echo "default.custom.yaml must not expose frost schemas while the legacy backend is active" >&2
  exit 1
}

grep -q 'else -> CustomConstant.SCHEMA_ZH_QWERTY' "$switcher_file" || {
  echo "Full keyboard pinyin fallback must use stable pinyin by default" >&2
  exit 1
}

grep -q 'CustomConstant.stableSchemaForLegacyRime(value.second)' "$switcher_file" || {
  echo "Settings mode switch must sanitize unsupported frost schemas before persisting" >&2
  exit 1
}

if grep -q 'savedSchema.startsWith(CustomConstant.SCHEMA_FROST_DOUBLE_PREFIX)\|savedSchema == CustomConstant.SCHEMA_FROST' "$switcher_file"; then
  echo "Input mode switcher must not route frost schemas into legacy Rime" >&2
  exit 1
fi

if grep -q 'keyboard_name_cn26_frost' "$settings_container_file"; then
  echo "Keyboard switch panel must hide frost entries until the native core supports them" >&2
  exit 1
fi

if grep -q 'SkbMenuMode.Pinyin26Frost -> rimeValue == CustomConstant.SCHEMA_FROST' "$menu_adapter_file" "$candidates_menu_adapter_file"; then
  echo "Menu adapters must not mark frost as selectable while legacy Rime is active" >&2
  exit 1
fi

grep -q 'CustomConstant.stableSchemaForLegacyRime(schema)' "$kernel_file" || {
  echo "Kernel must sanitize unsupported frost schemas before native selection" >&2
  exit 1
}

grep -q 'setValue(CustomConstant.SCHEMA_ZH_QWERTY)' "$kernel_file" || {
  echo "Kernel must persist fallback to stable pinyin when schema selection fails" >&2
  exit 1
}

if grep -q 'Precompile rime-frost schemas\|rime_deployer\|rime_frost_android' "$workflow_file"; then
  echo "Android CI must not package desktop-precompiled frost tables for the legacy backend" >&2
  exit 1
fi

grep -q 'schema_id: rime_frost_android' "$frost_android_schema_file" || {
  echo "Android-compatible frost schema must declare schema_id rime_frost_android" >&2
  exit 1
}

if grep -q 'lua_processor\|lua_translator\|lua_filter' "$frost_android_schema_file"; then
  echo "Android-compatible frost schema must not require librime-lua while using the legacy native backend" >&2
  exit 1
fi

grep -q 'dictionary: rime_frost_android' "$frost_android_schema_file" || {
  echo "Android-compatible frost schema must use its trimmed Android dictionary" >&2
  exit 1
}

if grep -q 'cn_dicts_cell' "$frost_android_dict_file"; then
  echo "Android-compatible frost dictionary must not import missing cn_dicts_cell tables" >&2
  exit 1
fi

if grep -q 'migrateDefaultSchemaToFrost\|New installs must default pinyinModeRime to rime_frost\|else -> CustomConstant.SCHEMA_FROST' "$launcher_file" "$switcher_file" "$prefs_file"; then
  echo "Android code/tests must not force rime_frost as the default until it is runtime-verified" >&2
  exit 1
fi

if grep -q 'schema == CustomConstant.SCHEMA_ZH_QWERTY || schema == CustomConstant.SCHEMA_FROST' "$root/yuyansdk/src/main/java/com/yuyan/inputmethod/RimeEngine.kt"; then
  echo "Composition editing must not treat frost schemas as active legacy full-pinyin schemas" >&2
  exit 1
fi
