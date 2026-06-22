#!/bin/sh
set -eu

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
export GRADLE_USER_HOME="${GRADLE_USER_HOME:-/private/tmp/bigbang-nova-gradle}"
GRADLE_BIN="$(find "$HOME/.gradle/wrapper/dists/gradle-8.7-bin" -path '*/gradle-8.7/bin/gradle' -type f 2>/dev/null | head -n 1)"

if [ -n "$GRADLE_BIN" ]; then
  exec "$GRADLE_BIN" -p "$SCRIPT_DIR" "$@"
fi

if [ -f "$SCRIPT_DIR/gradle/wrapper/gradle-wrapper.jar" ]; then
  exec java -classpath "$SCRIPT_DIR/gradle/wrapper/gradle-wrapper.jar" org.gradle.wrapper.GradleWrapperMain "$@"
fi

if command -v gradle >/dev/null 2>&1; then
  exec gradle -p "$SCRIPT_DIR" "$@"
fi

echo "Gradle is not installed and gradle-wrapper.jar is missing." >&2
exit 1
