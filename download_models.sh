#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "$0")" && pwd)"
ASSET_DIR="$ROOT/app/src/main/assets"
mkdir -p "$ASSET_DIR"

MODEL_URL="https://storage.googleapis.com/mediapipe-models/hand_landmarker/hand_landmarker/float16/latest/hand_landmarker.task"
MODEL_FILE="$ASSET_DIR/hand_landmarker.task"
EXPECTED_SHA256="fbc2a30080c3c557093b5ddfc334698132eb341044ccee322ccf8bcf3607cde1"

curl --fail --location --retry 3 --retry-delay 2 \
  --output "$MODEL_FILE" "$MODEL_URL"

echo "$EXPECTED_SHA256  $MODEL_FILE" | sha256sum --check --status || {
  echo "Hand Landmarker model checksum mismatch. Refusing to package this model." >&2
  rm -f "$MODEL_FILE"
  exit 1
}

echo "Verified MediaPipe Hand Landmarker model."
