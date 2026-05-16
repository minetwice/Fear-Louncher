#!/bin/sh

DIR="$(cd "$(dirname "$0")" && pwd)"

JAVA_CMD="java"

CLASSPATH=$DIR/gradle/wrapper/gradle-wrapper.jar

exec "$JAVA_CMD" -classpath "$CLASSPATH" org.gradle.wrapper.GradleWrapperMain "$@"
