#!/usr/bin/env bash
set -euo pipefail

cd "$(dirname "$0")/.."
./gradlew ktfmtCheck :packages:fonts:lintDebug
