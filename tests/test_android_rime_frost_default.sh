#!/usr/bin/env bash
set -euo pipefail

root="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/.." && pwd)"
prefs_file="$root/yuyansdk/src/main/java/com/yuyan/imemodule/prefs/AppPrefs.kt"
constant_file="$root/yuyansdk/src/main/java/com/yuyan/imemodule/application/CustomConstant.kt"
launcher_file="$root/yuyansdk/src/main/java/com/yuyan/imemodule/application/Launcher.kt"
switcher_file="$root/yuyansdk/src/main/java/com/yuyan/imemodule/manager/InputModeSwitcher.kt"

for file in "$prefs_file" "$constant_file" "$launcher_file" "$switcher_file"; do
  [[ -f "$file" ]] || {
    echo "Missing Android Rime frost default file: $file" >&2
    exit 1
  }
done

grep -q 'pinyinModeRime = string("input_method_pinyin_mode_rime", CustomConstant.SCHEMA_FROST)' "$prefs_file" || {
  echo "New installs must default pinyinModeRime to rime_frost" >&2
  exit 1
}

grep -q 'CURRENT_RIME_DICT_DATA_VERSIOM = 2026061301' "$constant_file" || {
  echo "Rime data version must be bumped so existing installs receive the frost default migration" >&2
  exit 1
}

grep -q 'migrateDefaultSchemaToFrost()' "$launcher_file" || {
  echo "Launcher must migrate historical default pinyin installs to rime_frost" >&2
  exit 1
}

grep -q 'Migrated default Rime schema from pinyin to rime_frost' "$launcher_file" || {
  echo "Default schema migration must be visible in logcat" >&2
  exit 1
}

awk '
  /schema_list:/ { in_list = 1; next }
  in_list && /- schema: rime_frost$/ { frost_line = NR }
  in_list && /- schema: pinyin$/ { pinyin_line = NR }
  in_list && /"menu\/page_size"/ { in_list = 0 }
  END { exit frost_line > 0 && pinyin_line > 0 && frost_line < pinyin_line ? 0 : 1 }
' "$launcher_file" || {
  echo "default.custom.yaml must list rime_frost before legacy pinyin" >&2
  exit 1
}

grep -q 'else -> CustomConstant.SCHEMA_FROST' "$switcher_file" || {
  echo "Full keyboard pinyin fallback must use rime_frost by default" >&2
  exit 1
}

if grep -q 'frost 系列需手动切换' "$launcher_file" "$switcher_file" "$prefs_file"; then
  echo "Android code must not document rime_frost as manual-only after the default migration" >&2
  exit 1
fi
