#!/usr/bin/env bash
set -euo pipefail

cd /workspace

PROJECT_ID="${GCLOUD_PROJECT:-${FIREBASE_PROJECT_ID:-demo-biosecure}}"

echo "Starting Firebase Emulator Suite (project=${PROJECT_ID})"
echo "  auth:9099  firestore:8080  functions:5001  hub:4400  ui:4000"

if [ ! -f /workspace/functions/lib/index.js ]; then
  echo "functions/lib/index.js is missing; building TypeScript first"
  npm --prefix functions run build
fi

exec firebase emulators:start \
  --project "${PROJECT_ID}" \
  --only auth,firestore,functions,ui
