#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "$0")" && pwd)"
ASSET_DIR="$ROOT/app/src/main/assets"
mkdir -p "$ASSET_DIR"

curl --fail --location --retry 3 --retry-delay 2 \
  -o "$ASSET_DIR/hand_landmarker.task" \
  https://storage.googleapis.com/mediapipe-models/hand_landmarker/hand_landmarker/float16/latest/hand_landmarker.task

curl --fail --location --retry 3 --retry-delay 2 \
  -o "$ASSET_DIR/blaze_face_short_range.tflite" \
  https://storage.googleapis.com/mediapipe-models/face_detector/blaze_face_short_range/float16/latest/blaze_face_short_range.tflite

test -s "$ASSET_DIR/hand_landmarker.task"
test -s "$ASSET_DIR/blaze_face_short_range.tflite"
echo "MediaPipe model assets downloaded successfully."
