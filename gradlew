#!/bin/sh
# Fallback launcher. The Gradle wrapper JAR (gradle/wrapper/gradle-wrapper.jar)
# is not included in this archive. If it is present, use it; otherwise use a
# system-installed Gradle 9.x. To generate the wrapper once, run:
#   gradle wrapper --gradle-version 9.3.1
APP_HOME=$(cd "$(dirname "$0")" && pwd -P)
WRAPPER_JAR="$APP_HOME/gradle/wrapper/gradle-wrapper.jar"
if [ -f "$WRAPPER_JAR" ]; then
    exec java -classpath "$WRAPPER_JAR" org.gradle.wrapper.GradleWrapperMain "$@"
fi
if ! command -v gradle >/dev/null 2>&1; then
    echo "gradle-wrapper.jar is missing and no 'gradle' command was found." >&2
    echo "Install Gradle 9.3.1 or run: gradle wrapper --gradle-version 9.3.1" >&2
    exit 1
fi
exec gradle "$@"
