#!/usr/bin/env bash
cd "$(dirname "$0")"

JAVA_HOME=/Users/skevo/Library/Java/JavaVirtualMachines/openjdk-23.0.2/Contents/Home ./gradlew clean build
