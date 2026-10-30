#!/usr/bin/env bash
# Renders the flags in app/src/main/assets/flags, one 4:3 WebP per country of countries.tsv,
# from the SVGs of flag-icons (https://github.com/lipis/flag-icons, MIT license).
#
# Needs curl, unzip, rsvg-convert (librsvg) and ImageMagick with WebP support.
#
# Usage: tools/build_flags.sh [work-dir]
set -euo pipefail

version="7.2.3"
width=480
height=360

root="$(cd "$(dirname "$0")/.." && pwd)"
work="${1:-/tmp/travel-diary-flags}"
out="$root/app/src/main/assets/flags"

for tool in curl unzip rsvg-convert magick; do
  if ! command -v "$tool" >/dev/null; then
    echo "$tool not found." >&2
    exit 1
  fi
done

src="$work/flag-icons-$version"
if [ ! -d "$src" ]; then
  mkdir -p "$work"
  curl -fsSL -o "$work/flag-icons.zip" "https://github.com/lipis/flag-icons/archive/refs/tags/v$version.zip"
  unzip -q -o "$work/flag-icons.zip" -d "$work"
fi

mkdir -p "$out"
cp "$src/LICENSE" "$out/LICENSE"
cut -f1 "$root/app/src/main/assets/data/countries.tsv" | tr 'A-Z' 'a-z' | while read -r code; do
  rsvg-convert -w "$width" -h "$height" "$src/flags/4x3/$code.svg" -o "$work/$code.png"
  magick "$work/$code.png" -quality 80 -define webp:method=6 "$out/$code.webp"
done
echo "Wrote $(ls "$out"/*.webp | wc -l) flags to $out"
