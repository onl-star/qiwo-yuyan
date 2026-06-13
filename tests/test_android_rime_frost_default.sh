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

for file in "$prefs_file" "$constant_file" "$launcher_file" "$switcher_file" "$settings_container_file" "$menu_adapter_file" "$candidates_menu_adapter_file" "$kernel_file" "$workflow_file"; do
  [[ -f "$file" ]] || {
    echo "Missing Android Rime schema safety file: $file" >&2
    exit 1
  }
done

grep -q 'pinyinModeRime = string("input_method_pinyin_mode_rime", CustomConstant.SCHEMA_ZH_QWERTY)' "$prefs_file" || {
  echo "New installs must default pinyinModeRime to stable legacy pinyin" >&2
  exit 1
}

grep -q 'CURRENT_RIME_DICT_DATA_VERSIOM = 2026061302' "$constant_file" || {
  echo "Rime data version must be bumped so existing frost-default installs receive the fallback migration" >&2
  exit 1
}

grep -q 'migrateUnstableFrostDefaultToLegacyPinyin()' "$launcher_file" || {
  echo "Launcher must migrate unstable frost defaults back to pinyin" >&2
  exit 1
}

grep -q 'Migrated unstable Rime schema from rime_frost to pinyin' "$launcher_file" || {
  echo "Frost fallback migration must be visible in logcat" >&2
  exit 1
}

awk '
  /schema_list:/ { in_list = 1; next }
  in_list && /- schema: rime_frost$/ { frost_line = NR }
  in_list && /- schema: pinyin$/ { pinyin_line = NR }
  in_list && /"menu\/page_size"/ { in_list = 0 }
  END { exit frost_line > 0 && pinyin_line > 0 && pinyin_line < frost_line ? 0 : 1 }
' "$launcher_file" || {
  echo "default.custom.yaml must list stable pinyin before optional rime_frost" >&2
  exit 1
}

grep -q 'else -> CustomConstant.SCHEMA_ZH_QWERTY' "$switcher_file" || {
  echo "Full keyboard pinyin fallback must use stable pinyin by default" >&2
  exit 1
}

grep -q 'SkbMenuMode.Pinyin26Frost' "$settings_container_file" || {
  echo "Keyboard switch panel must expose a separate rime_frost full-pinyin entry" >&2
  exit 1
}

grep -q 'CustomConstant.SCHEMA_FROST' "$settings_container_file" || {
  echo "Rime frost full-pinyin entry must switch to SCHEMA_FROST" >&2
  exit 1
}

grep -q 'SkbMenuMode.Pinyin26Frost -> rimeValue == CustomConstant.SCHEMA_FROST' "$menu_adapter_file" || {
  echo "Settings menu adapter must show rime_frost selected state" >&2
  exit 1
}

grep -q 'SkbMenuMode.Pinyin26Frost -> rimeValue == CustomConstant.SCHEMA_FROST' "$candidates_menu_adapter_file" || {
  echo "Candidate menu adapter must show rime_frost selected state" >&2
  exit 1
}

grep -q 'selectSchema failed for' "$kernel_file" || {
  echo "Kernel must log schema selection failure before falling back" >&2
  exit 1
}

grep -q 'setValue(CustomConstant.SCHEMA_ZH_QWERTY)' "$kernel_file" || {
  echo "Kernel must persist fallback to stable pinyin when schema selection fails" >&2
  exit 1
}

grep -q 'cp -n "$RIME_DIR"/build/\* "$RIME_BUILD_SRC/"' "$workflow_file" || {
  echo "Android CI must copy complete rime_frost build artifacts, including schema yaml files" >&2
  exit 1
}

if grep -q 'migrateDefaultSchemaToFrost\|New installs must default pinyinModeRime to rime_frost\|else -> CustomConstant.SCHEMA_FROST' "$launcher_file" "$switcher_file" "$prefs_file"; then
  echo "Android code/tests must not force rime_frost as the default until it is runtime-verified" >&2
  exit 1
fi
