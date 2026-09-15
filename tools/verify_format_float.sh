#!/bin/sh
# Differential test for the %f conversion added to TFormatter - see tools/VerifyFormatFloat.java.
#
# TFormatter is loaded out of build/classes/java/main and run on a desktop JVM, so run
# ./gradlew classes teavmClasses first if the source has changed. teavm-classlib supplies the
# exception types in the same package that this class throws (TIllegalFormatFlagsException and
# friends) plus org.teavm.classlib.impl.IntegerUtil.
set -e
cd "$(dirname "$0")/.."

CLASSLIB=$(find "$HOME/.gradle/caches/modules-2/files-2.1/org.teavm/teavm-classlib" \
    -name "teavm-classlib-*.jar" ! -name "*-sources.jar" | head -1)
if [ -z "$CLASSLIB" ]; then
    echo "teavm-classlib not found in the Gradle cache" >&2
    exit 1
fi

OUT=$(mktemp -d)
trap 'rm -rf "$OUT"' EXIT

javac -nowarn -d "$OUT" tools/VerifyFormatFloat.java

# Windows JVMs want ; between classpath entries and native paths; cygpath is a no-op elsewhere.
if command -v cygpath >/dev/null 2>&1; then
    CP="$(cygpath -w "$OUT");build/classes/java/main;$(cygpath -w "$CLASSLIB")"
else
    CP="$OUT:build/classes/java/main:$CLASSLIB"
fi

java -cp "$CP" VerifyFormatFloat
