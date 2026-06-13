#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/.." && pwd)"
SOURCE_DIR="${QIWO_RIME_FROST_SOURCE_DIR:-}"
TARGET_DIR="${ROOT}/yuyansdk/src/main/assets/rime_frost"

if [[ -z "${SOURCE_DIR}" ]]; then
  echo "QIWO_RIME_FROST_SOURCE_DIR must point to a complete rime-frost source directory" >&2
  exit 1
fi

if [[ ! -d "${SOURCE_DIR}" ]]; then
  echo "Missing rime-frost source directory: ${SOURCE_DIR}" >&2
  exit 1
fi

mkdir -p "${TARGET_DIR}"

required_dirs=(
  cn_dicts
  cn_dicts_common
  cn_dicts_cell
  en_dicts
  lua
  opencc
)

for dir_name in "${required_dirs[@]}"; do
  if [[ ! -d "${SOURCE_DIR}/${dir_name}" ]]; then
    echo "Missing required source directory: ${dir_name}" >&2
    exit 1
  fi
  rm -rf "${TARGET_DIR:?}/${dir_name}"
  cp -R "${SOURCE_DIR}/${dir_name}" "${TARGET_DIR}/${dir_name}"
done

required_files=(
  key_bindings.yaml
  punctuation.yaml
  symbols.yaml
  custom_phrase.txt
  melt_eng.schema.yaml
  melt_eng.dict.yaml
  radical_pinyin.schema.yaml
  radical_pinyin.dict.yaml
  rime_frost.schema.yaml
  rime_frost.dict.yaml
  rime_frost_aux.schema.yaml
  rime_frost_aux.dict.yaml
)

for file_name in "${required_files[@]}"; do
  if [[ ! -f "${SOURCE_DIR}/${file_name}" ]]; then
    echo "Missing required source file: ${file_name}" >&2
    exit 1
  fi
  cp "${SOURCE_DIR}/${file_name}" "${TARGET_DIR}/${file_name}"
done
