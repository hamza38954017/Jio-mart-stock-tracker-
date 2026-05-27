#!/usr/bin/env sh
set -e
DEFAULT_JVM_OPTS='"-Xmx64m" "-Xms64m"'
APP_HOME="$(cd "$(dirname "$0")" && pwd)"
CLASSPATH="$APP_HOME/gradle/wrapper/gradle-wrapper.jar"

# Download wrapper jar if missing
if [ ! -f "$CLASSPATH" ]; then
  curl -sLo "$CLASSPATH" \
    "https://github.com/gradle/gradle/raw/v8.6.0/gradle/wrapper/gradle-wrapper.jar"
fi

exec java $DEFAULT_JVM_OPTS -classpath "$CLASSPATH" \
  org.gradle.wrapper.GradleWrapperMain "$@"
