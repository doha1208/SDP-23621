#!/usr/bin/env bash
set -euo pipefail
PROJECT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$PROJECT_DIR"
if ! command -v javac >/dev/null 2>&1; then
  echo "JDK 17 or later is required (javac was not found)." >&2
  exit 1
fi
BUILD_DIR="$(mktemp -d "${TMPDIR:-/tmp}/dash-preview.XXXXXX")"
trap 'rm -rf "$BUILD_DIR"' EXIT
javac --release 17 -d "$BUILD_DIR" src/engine/DashVisualEffect.java src/preview/DashPreview.java
if [[ "${1:-}" == "--snapshot" ]]; then
  java -Djava.awt.headless=true -cp "$BUILD_DIR" preview.DashPreview "$@"
else
  java -cp "$BUILD_DIR" preview.DashPreview "$@"
fi
