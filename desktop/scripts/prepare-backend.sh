#!/usr/bin/env bash
# Puts FocusQuest inside the desktop app: the boot jar (backend and web app) and a JRE cut down to
# the modules it needs, in src-tauri/resources/backend/, which `tauri build` bundles.
# Needs JDK 21 (for jlink) and npm; run from anywhere.
set -euo pipefail

desktop="$(cd "$(dirname "$0")/.." && pwd)"
repo="$(dirname "$desktop")"
out="$desktop/src-tauri/resources/backend"

# The modules a Spring Boot app with H2, JPA and Flyway uses. jdeps cannot see inside a boot jar's
# nested jars, so the list is fixed; if the backend fails with NoClassDefFoundError on a java.* or
# jdk.* class, add its module here.
modules=java.base,java.compiler,java.desktop,java.instrument,java.logging,java.management,java.naming,\
java.net.http,java.prefs,java.rmi,java.scripting,java.security.jgss,java.security.sasl,java.sql,\
java.transaction.xa,java.xml,jdk.crypto.ec,jdk.management,jdk.naming.dns,jdk.unsupported,jdk.zipfs

java_home="${JAVA_HOME:-$(/usr/libexec/java_home -v 21)}"

echo "Building the boot jar, with the web app..."
(cd "$repo/backend" && ./gradlew --console=plain --quiet bootJar)
jar="$(ls "$repo"/backend/build/libs/focusquest-backend-*-SNAPSHOT.jar | grep -v plain | head -1)"

echo "Making the JRE..."
rm -rf "$out/jre" "$out/focusquest.jar"
"$java_home/bin/jlink" --add-modules "$modules" --strip-debug --no-man-pages --no-header-files \
  --compress=zip-6 --output "$out/jre"
cp "$jar" "$out/focusquest.jar"

echo "Ready in $out ($(du -sh "$out" | cut -f1))."
