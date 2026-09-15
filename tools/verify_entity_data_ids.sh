#!/bin/sh
# Checks EntityDataAccessorCounts against the ids vanilla really produces, on a desktop JVM
# where class initialisation is ordered parent-first. See tools/VerifyEntityDataIds.java.
#
# Run ./gradlew classes first if the table has been regenerated.
#
# Classpath order matters here and is the opposite of the game build's. The untrimmed jars from
# libs-1.18.2 come FIRST so that vanilla's own SynchedEntityData and the real guava are what
# run - this test exists to compare against vanilla, so letting this project's replacements win
# would have it grade its own homework. build/classes/java/main goes last, contributing only
# EntityDataAccessorCounts, which exists nowhere else.
set -e
cd "$(dirname "$0")/.."

OUT=$(mktemp -d)
trap 'rm -rf "$OUT"' EXIT

win() {
    if command -v cygpath >/dev/null 2>&1; then cygpath -w "$1"; else printf '%s' "$1"; fi
}

if command -v cygpath >/dev/null 2>&1; then SEP=';'; else SEP=':'; fi

CP="$(win libs-1.18.2/1.18.2-mapped.jar)"
for j in libs-1.18.2/*.jar; do
    case "$j" in
        *1.18.2-mapped.jar) ;;
        *) CP="$CP$SEP$(win "$j")" ;;
    esac
done
CP="$CP${SEP}build/classes/java/main"

javac -nowarn -cp "$CP" -d "$OUT" tools/VerifyEntityDataIds.java
java -cp "$(win "$OUT")$SEP$CP" VerifyEntityDataIds
