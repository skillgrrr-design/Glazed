#!/usr/bin/env bash
set -euo pipefail

# build_and_push.sh
# Usage: ./scripts/build_and_push.sh [commit message]
# Exports JAVA_HOME (default to Java 21 path), compiles with Gradle wrapper,
# then commits & pushes only if there are changes.

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$REPO_ROOT"

JAVA_HOME="${JAVA_HOME:-/usr/lib/jvm/java-21-openjdk-amd64}"
export JAVA_HOME
export PATH="$JAVA_HOME/bin:$PATH"

echo "Using JAVA_HOME=$JAVA_HOME"

if [ ! -f ./gradlew ]; then
  echo "Error: gradlew not found in repo root"
  exit 1
fi

echo "Running Gradle build..."
./gradlew build --no-daemon

echo "Gradle build finished. Checking git status..."

# Stage all changes
git add -A

# If there are staged changes, commit and push
if git diff --cached --quiet; then
  echo "No changes to commit."
else
  MSG="${1:-auto: build and changes}"
  git commit -m "$MSG"
  echo "Pushing to remote..."
  git push
fi

echo "Done."
