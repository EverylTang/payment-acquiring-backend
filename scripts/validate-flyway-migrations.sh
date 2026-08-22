#!/usr/bin/env bash
set -euo pipefail

for module in platform-service trade-service fund-service; do
  dir="$module/src/main/resources/db/migration"
  [ -d "$dir" ] || { echo "missing migration directory: $dir" >&2; exit 1; }
  versions=$(find "$dir" -maxdepth 1 -type f -name 'V*__*.sql' -print | sed -E 's#^.*/V([0-9]+)__.*#\1#' | sort -n)
  [ -n "$versions" ] || { echo "no migrations: $module" >&2; exit 1; }
  duplicates=$(printf '%s\n' "$versions" | uniq -d)
  [ -z "$duplicates" ] || { echo "duplicate migration versions in $module: $duplicates" >&2; exit 1; }
  printf '%s migrations: %s\n' "$module" "$(printf '%s ' $versions)"
done
