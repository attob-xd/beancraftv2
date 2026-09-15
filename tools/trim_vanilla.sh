#!/bin/sh
# Builds libs-1.18.2-derived/1.18.2-mapped-trimmed.jar: the vanilla jar with every class
# this project replaces removed.
#
# The port rests on "source here beats the jar". For javac that is settled by
# -Xprefer:source. For TeaVM it was supposed to be settled by putting this project's classes
# first on the classpath - and that turned out not to hold: the jar's net.minecraft.client.
# main.Main won over src/game/java's, so Bootstrap.bootStrap() was never called and the
# client died in a registry static initialiser. It had been silently winning for other
# classes too.
#
# Ordering is the wrong mechanism for something this load-bearing. If a class exists in
# src/game/java, the jar should not carry it at all, and then which one TeaVM picks is not a
# question. The list is derived from the source tree on every build, so it cannot drift.
set -e
cd "$(dirname "$0")/.."
python - <<'PY'
import os
import zipfile

SRC = os.path.join('src', 'game', 'java')
IN = os.path.join('libs-1.18.2', '1.18.2-mapped.jar')
OUT_DIR = 'libs-1.18.2-derived'
OUT = os.path.join(OUT_DIR, '1.18.2-mapped-trimmed.jar')

# every class this project supplies as source, as a jar path prefix
replaced = set()
for root, _, files in os.walk(SRC):
    for f in files:
        if not f.endswith('.java'):
            continue
        rel = os.path.relpath(os.path.join(root, f), SRC).replace('\\', '/')
        replaced.add(rel[:-len('.java')])

os.makedirs(OUT_DIR, exist_ok=True)
kept = dropped = 0
dropped_names = []
with zipfile.ZipFile(IN) as zin, zipfile.ZipFile(OUT, 'w', zipfile.ZIP_DEFLATED) as zout:
    for info in zin.infolist():
        name = info.filename
        if name.endswith('.class'):
            stem = name[:-len('.class')]
            # Foo.class and its nested Foo$Bar.class both belong to Foo. Match the whole name
            # first: splitting on the first dollar and looking the prefix up silently misses a
            # class whose own simple name contains one, and a missed class is reported nowhere
            # - the jar's copy just wins. No vanilla class is spelled that way, so this is
            # insurance rather than a fix, but it is the same shape of bug that let gson's
            # $Gson$Types survive a full build in tools/trim_lib.sh.
            if stem in replaced or any(stem.startswith(r + '$') for r in replaced):
                dropped += 1
                dropped_names.append(name)
                continue
        zout.writestr(info, zin.read(name))
        kept += 1

print('replaced by src/game/java: %d classes' % len(replaced))
print('kept %d entries, dropped %d -> %s' % (kept, dropped, OUT))
for n in sorted(dropped_names):
    print('   dropped', n)
PY
