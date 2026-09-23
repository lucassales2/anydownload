#!/bin/bash
# Starts the bundled Temurin runtime. PATH is inherited, so a Mac without
# yt-dlp still opens; the app reports the missing tool.
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
JAVA="$ROOT/runtime/Contents/Home/bin/java"
if [[ ! -x "$JAVA" ]]; then
  echo "AnyDownload could not find its Java runtime. Reinstall the app." >&2
  exit 1
fi
exec "$JAVA" -jar "$ROOT/app/AnyDownload.jar"
