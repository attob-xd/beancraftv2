#!/bin/sh
# Builds libs-1.18.2-derived/teavm-jso-apis-trimmed.jar: TeaVM's JSO API jar without
# org/teavm/jso/indexeddb/IDBIndex.class.
#
# Upstream's IDBIndex declares
#
#     @JSBody(params = "obj", script = "return obj;")
#     @JSByRef
#     private static native String[] unwrapStringArray(JSObject obj);
#
# and @JSByRef is only legal on arrays of primitives. TeaVM validates that for every
# reachable class and fails the whole build - and a failed build writes NO output, so the
# stale classes.js from the previous run stays on disk and looks like a successful one.
# That cost real time: results were being read off a file the build had not produced.
#
# This project supplies a corrected IDBIndex in src/teavm/java. Putting it earlier on the
# classpath is not enough - the jar wins regardless of order, which is the same lesson as
# trim_vanilla.sh - so the jar's copy is removed and there is only one.
set -e
cd "$(dirname "$0")/.."
# Pin the version. The cache holds 0.9.2 alongside lax1dude's 0.11 and 0.12 forks, and an
# unpinned find picked 0.11 - whose JSO API does not match TeaVM 0.9.2, so javac failed on
# EaglercraftX's own glue with "interface expected here". It must match the plugin version.
VERSION=0.9.2
IN=$(find "$HOME/.gradle/caches/modules-2/files-2.1/org.teavm/teavm-jso-apis/$VERSION" -name "teavm-jso-apis-$VERSION.jar" | head -1)
if [ -z "$IN" ]; then
    echo "teavm-jso-apis jar not found in the Gradle cache" >&2
    exit 1
fi
OUT_DIR=libs-1.18.2-derived
OUT=$OUT_DIR/teavm-jso-apis-trimmed.jar
mkdir -p "$OUT_DIR"
python - "$IN" "$OUT" <<'PY'
import sys
import zipfile

DROP = 'org/teavm/jso/indexeddb/IDBIndex.class'
src, dst = sys.argv[1], sys.argv[2]
kept = dropped = 0
with zipfile.ZipFile(src) as zin, zipfile.ZipFile(dst, 'w', zipfile.ZIP_DEFLATED) as zout:
    for info in zin.infolist():
        if info.filename == DROP:
            dropped += 1
            continue
        zout.writestr(info, zin.read(info.filename))
        kept += 1
print('kept %d entries, dropped %d -> %s' % (kept, dropped, dst))
PY

# teavm-classlib gets the same treatment. Two source trees supply classes the class library
# already has - TRuntime (which needs availableProcessors, absent upstream) and TSpliterators
# (which needs the nested AbstractSpliterator vanilla subclasses) in src/nio-shim, and TLocale
# (which needs getISO3Country, see its header) in src/teavm/java. The split is not arbitrary:
# src/nio-shim carries no TeaVM jars on its compile path at all, so a shim that needs one -
# TLocale reads CLDRHelper and org.teavm.platform.metadata - has to live in src/teavm/java,
# which is part of the main source set and does. The jar's copies win over ours no matter how
# the classpath is ordered, so they are removed. Both trees are scanned, so the list
# maintains itself.
CLASSLIB=$(find "$HOME/.gradle/caches/modules-2/files-2.1/org.teavm/teavm-classlib/$VERSION"         -name "teavm-classlib-$VERSION.jar" | head -1)
if [ -z "$CLASSLIB" ]; then
    echo "teavm-classlib $VERSION not found in the Gradle cache" >&2
    exit 1
fi
python - "$CLASSLIB" "$OUT_DIR/teavm-classlib-trimmed.jar" <<'PY2'
import os
import sys
import zipfile

SHIMS = [os.path.join('src', 'nio-shim', 'java'), os.path.join('src', 'teavm', 'java')]
ours = set()
for shim in SHIMS:
    for root, _, files in os.walk(shim):
        for f in files:
            if f.endswith('.java'):
                rel = os.path.relpath(os.path.join(root, f), shim).replace(os.sep, '/')
                ours.add(rel[:-len('.java')])

src, dst = sys.argv[1], sys.argv[2]
kept = dropped = 0
names = []
with zipfile.ZipFile(src) as zin, zipfile.ZipFile(dst, 'w', zipfile.ZIP_DEFLATED) as zout:
    for info in zin.infolist():
        n = info.filename
        if n.endswith('.class'):
            # Match the whole name first, then the nested-class rule. Splitting on the first
            # dollar and looking up the prefix - which is what this did - silently misses any
            # class whose own name contains one, and a missed class is not reported anywhere:
            # the jar's copy simply wins and the replacement never runs. That is exactly how
            # gson's $Gson$Types survived a full build in tools/trim_lib.sh.
            name = n[:-len('.class')]
            if name in ours or any(name.startswith(o + '$') for o in ours):
                dropped += 1
                names.append(n)
                continue
        zout.writestr(info, zin.read(n))
        kept += 1
print('classlib: kept %d, dropped %d -> %s' % (kept, dropped, dst))
for n in sorted(names):
    print('   dropped', n)
PY2
