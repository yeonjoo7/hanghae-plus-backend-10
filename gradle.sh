#!/bin/bash
# Gradle wrapper with Java 21 path

export PATH="/opt/homebrew/opt/openjdk@21/bin:$PATH"
./gradlew "$@"
