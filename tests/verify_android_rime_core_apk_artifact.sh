#!/usr/bin/env bash
set -euo pipefail

if [[ "$#" -lt 1 ]]; then
  echo "Usage: $0 <apk> [<apk> ...]" >&2
  exit 2
fi

for tool in zipinfo unzip strings grep; do
  command -v "${tool}" >/dev/null 2>&1 || {
    echo "Missing required tool: ${tool}" >&2
    exit 1
  }
done

for apk in "$@"; do
  [[ -f "${apk}" ]] || {
    echo "APK not found: ${apk}" >&2
    exit 1
  }

  entries="$(zipinfo -1 "${apk}")"

  grep -q '^lib/arm64-v8a/libyuyanime\.so$' <<<"${entries}" || {
    echo "APK must contain wrapper lib/arm64-v8a/libyuyanime.so: ${apk}" >&2
    exit 1
  }

  grep -q '^lib/arm64-v8a/libqiwo_legacy_yuyanime\.so$' <<<"${entries}" || {
    echo "APK must contain legacy backend lib/arm64-v8a/libqiwo_legacy_yuyanime.so: ${apk}" >&2
    exit 1
  }

  grep -q '^lib/arm64-v8a/libc++_shared\.so$' <<<"${entries}" || {
    echo "APK must contain Android C++ runtime lib/arm64-v8a/libc++_shared.so for the Rime core wrapper: ${apk}" >&2
    exit 1
  }

  zipinfo -l "${apk}" 'lib/arm64-v8a/libyuyanime.so' | grep -q 'lib/arm64-v8a/libyuyanime\.so' || {
    echo "APK wrapper library entry is not readable: ${apk}" >&2
    exit 1
  }

  zipinfo -l "${apk}" 'lib/arm64-v8a/libqiwo_legacy_yuyanime.so' | grep -q 'lib/arm64-v8a/libqiwo_legacy_yuyanime\.so' || {
    echo "APK legacy backend library entry is not readable: ${apk}" >&2
    exit 1
  }

  zipinfo -l "${apk}" 'lib/arm64-v8a/libc++_shared.so' | grep -q 'lib/arm64-v8a/libc++_shared\.so' || {
    echo "APK Android C++ runtime library entry is not readable: ${apk}" >&2
    exit 1
  }

  diagnostics_found=0
  while IFS= read -r dex; do
    dex_strings="$(unzip -p "${apk}" "${dex}" | strings)"
    if grep -q 'QiwoRimeCore' <<<"${dex_strings}"; then
      diagnostics_found=1
      break
    fi
  done < <(grep '^classes.*\.dex$' <<<"${entries}")

  if [[ "${diagnostics_found}" != "1" ]]; then
    echo "APK must contain QiwoRimeCore runtime diagnostics in dex files: ${apk}" >&2
    exit 1
  fi
done
