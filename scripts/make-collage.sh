#!/usr/bin/env bash
set -euo pipefail

# Build a collage from screenshots into docs/collage.png
# Usage: scripts/make-collage.sh

if ! command -v montage >/dev/null 2>&1; then
  echo "Error: ImageMagick 'montage' is required." >&2
  exit 1
fi

mkdir -p docs
frames=(
  images/screenshot-2.png
  images/screenshot-5.png
  images/screenshot-6.png
  images/screenshot-8.png
  images/screenshot-10.png
  images/screenshot-12.png
  images/screenshot-15.png
  images/screenshot-16.png
  images/screenshot-17.png
)
use_frames=()
for f in "${frames[@]}"; do
  [[ -f "$f" ]] && use_frames+=("$f")
done

if [[ ${#use_frames[@]} -eq 0 ]]; then
  echo "No screenshots found (images/screenshot-*.png)." >&2
  exit 0
fi

montage "${use_frames[@]}" -geometry 600x -tile 3x -background none docs/collage.png

echo "Wrote docs/collage.png (${#use_frames[@]} images)"
