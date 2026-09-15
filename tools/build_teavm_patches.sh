#!/bin/sh
# Compiles src/teavm-patches into src/teavmc-classpath/resources, which the buildscript
# block puts ahead of the TeaVM jars, so these classes replace TeaVM's own. See the header
# comment in each patched file for why it exists.
set -e
cd "$(dirname "$0")/.."
G="$HOME/.gradle/caches/modules-2/files-2.1/org.teavm"
V=0.9.2
CP=""
for j in $(find "$G/teavm-core/$V" "$G/teavm-relocated-libs-hppc/$V" \
        "$G/teavm-interop/$V" -name "*.jar" 2>/dev/null); do
    # javac is a Windows binary here and cannot read MSYS-style /c/... paths.
    case "$(uname -s)" in MINGW*|MSYS*) j=$(cygpath -w "$j");; esac
    CP="$CP$j;"
done
javac -nowarn -cp "$CP" -d src/teavmc-classpath/resources \
        $(find src/teavm-patches/java -name "*.java")
echo "patched classes ->"
find src/teavmc-classpath/resources -name "*.class"
