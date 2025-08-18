#!/usr/bin/env bash
set -euo pipefail

# Local helper to build docs/demo.gif from repository screenshots using ImageMagick.
# Usage: scripts/make-demo-gif.sh [fps]
# Default FPS is 3 (slower). Requires `convert` (ImageMagick) installed.

FPS=${1:-3}
if ! command -v convert >/dev/null 2>&1; then
  echo "Error: ImageMagick 'convert' is required. Install it and retry." >&2
  exit 1
fi

mkdir -p docs

# Curate a nice tour order; include only files that exist.
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
  echo "No screenshots found (screenshot-*.png)." >&2
  exit 1
fi

# Map FPS to ImageMagick delay (centiseconds per frame)
if ! [[ "$FPS" =~ ^[0-9]+$ ]]; then FPS=3; fi
DELAY=$(( 100 / (FPS>0?FPS:3) ))

# Build optimized GIF at a readable width
convert \
  -dispose previous \
  -delay "$DELAY" \
  -loop 0 \
  -resize 1200x \
  "${use_frames[@]}" \
  -layers Optimize docs/demo.gif

echo "Wrote docs/demo.gif (${#use_frames[@]} frames, ${FPS} fps)"
