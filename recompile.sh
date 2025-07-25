#! /bin/sh
clear
./gradlew clean
./gradlew :gapl-example:build
./gradlew :basys:build
