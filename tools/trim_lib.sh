#!/bin/sh
# Builds libs-1.18.2-derived/<jar>-trimmed.jar: a library jar without the classes this project
# replaces under a given source package.
#
#     sh tools/trim_lib.sh com/mojang/authlib/yggdrasil authlib
#     sh tools/trim_lib.sh com/google/gson/reflect      gson
#
# Same reason as every other trim here: on TeaVM's classpath a jar wins over this project's
# sources no matter how the classpath is ordered, so a replaced class has to be removed rather
# than out-voted.
#
# The scope argument is not a convenience, it is a safety rail. src/main/java carries plenty of
# classes that shadow a library without being meant to replace it - lax1dude's GameProfile, for
# one, whose getProperties() returns a Guava Multimap where authlib's returns PropertyMap.
# Dropping the jar's copy of that would turn every vanilla call into a NoSuchMethodError. So
# each call names exactly the package it means.
#
# The check below refuses to write a jar in which a surviving class has lost a supertype - TeaVM
# resolves supertypes when the page loads, so that is a ReferenceError before any game code
# runs. It does NOT catch a replacement whose method signatures differ from the copy it
# displaces; only reading both APIs catches that.
set -e
if [ $# -ne 2 ]; then
    echo "usage: $0 <source-package-path> <jar-name-prefix>" >&2
    exit 2
fi
cd "$(dirname "$0")/.."
python - "$1" "$2" <<'PY'

import os
import re
import struct
import sys
import zipfile

SRC = os.path.join('src', 'main', 'java')
IN_DIR = 'libs-1.18.2'
OUT_DIR = 'libs-1.18.2-derived'
PREFIX = os.path.join(*sys.argv[1].split('/'))

NESTED = re.compile(r'^\s*(?:public\s+|protected\s+|private\s+|static\s+|final\s+|abstract\s+)*'
                    r'(?:class|interface|enum|@interface)\s+(\w+)')
replaced = set()
supplied = set()
root = os.path.join(SRC, PREFIX)
for base, _, files in os.walk(root):
    for f in files:
        if not f.endswith('.java'):
            continue
        rel = os.path.relpath(os.path.join(base, f), SRC).replace(os.sep, '/')
        name = rel[:-len('.java')]
        replaced.add(name)
        supplied.add(name)
        outer = name.rsplit('/', 1)[-1]
        with open(os.path.join(base, f), encoding='utf8') as fh:
            for line in fh:
                m = NESTED.match(line)
                if m and m.group(1) != outer:
                    supplied.add(name + '$' + m.group(1))

if not replaced:
    print('no sources under %s; nothing to trim' % PREFIX)
    sys.exit(0)


def hierarchy(data):
    if data[:4] != b'\xca\xfe\xba\xbe':
        return None
    n = struct.unpack('>H', data[8:10])[0]
    off = 10
    consts = {}
    i = 1
    while i < n:
        tag = data[off]
        off += 1
        if tag == 1:
            ln = struct.unpack('>H', data[off:off + 2])[0]
            off += 2
            consts[i] = ('u', data[off:off + ln].decode('utf8', 'replace'))
            off += ln
        elif tag in (7, 8, 16, 19, 20):
            consts[i] = (tag, struct.unpack('>H', data[off:off + 2])[0])
            off += 2
        elif tag == 15:
            consts[i] = (tag, 0)
            off += 3
        elif tag in (3, 4, 9, 10, 11, 12, 17, 18):
            consts[i] = (tag, struct.unpack('>HH', data[off:off + 4]))
            off += 4
        elif tag in (5, 6):
            consts[i] = (tag, 0)
            off += 8
            i += 1
        else:
            return None
        i += 1

    def utf(x):
        v = consts.get(x)
        return v[1] if v and v[0] == 'u' else None

    def cls(x):
        v = consts.get(x)
        return utf(v[1]) if v and v[0] == 7 else None

    off += 2
    this = cls(struct.unpack('>H', data[off:off + 2])[0])
    off += 2
    sup = cls(struct.unpack('>H', data[off:off + 2])[0])
    off += 2
    count = struct.unpack('>H', data[off:off + 2])[0]
    off += 2
    ifs = [cls(struct.unpack('>H', data[off + 2 * k:off + 2 * k + 2])[0]) for k in range(count)]
    return this, sup, ifs


def is_replaced(entry):
    """Is this jar entry the class a source file replaces, or one of its nested classes?

    This used to be `name.split('$', 1)[0] in replaced`, mapping Foo$Bar to Foo. That is
    right for ordinary nested classes and wrong for a class whose own simple name contains
    a dollar - gson's `$Gson$Types` splits to the empty string, so the jar's copy was never
    dropped and silently won over the replacement. It cost a whole build to notice, because
    nothing reports a class that was *not* trimmed.

    Matching against each replaced name in turn has no such blind spot: a nested class is
    exactly one whose name is a replaced name followed by a dollar. `replaced` holds a
    handful of entries, so the loop costs nothing.
    """
    if not entry.endswith('.class'):
        return False
    name = entry[:-len('.class')]
    if name in replaced:
        return True
    for r in replaced:
        if name.startswith(r + '$'):
            return True
    return False


jars = sorted(f for f in os.listdir(IN_DIR)
            if f.startswith(sys.argv[2]) and f.endswith('.jar'))
if not jars:
    print('no %s jar in %s' % (sys.argv[2], IN_DIR))
    sys.exit(1)

missing = set()
for j in jars:
    with zipfile.ZipFile(os.path.join(IN_DIR, j)) as z:
        for nm in z.namelist():
            if is_replaced(nm) and nm[:-len('.class')] not in supplied:
                missing.add(nm[:-len('.class')])

orphans = []
for j in jars:
    with zipfile.ZipFile(os.path.join(IN_DIR, j)) as z:
        for nm in z.namelist():
            if not nm.endswith('.class') or is_replaced(nm):
                continue
            h = hierarchy(z.read(nm))
            if not h:
                continue
            this, sup, ifs = h
            for parent in [sup] + ifs:
                if parent in missing:
                    orphans.append((this, parent))

if orphans:
    print('REFUSING to trim: these would lose a supertype, which is a ReferenceError when')
    print('the page loads:')
    for this, parent in sorted(set(orphans)):
        print('   %s extends/implements %s' % (this, parent))
    sys.exit(1)

os.makedirs(OUT_DIR, exist_ok=True)
for j in jars:
    out = os.path.join(OUT_DIR, j[:-len('.jar')] + '-trimmed.jar')
    kept = dropped = 0
    names = []
    with zipfile.ZipFile(os.path.join(IN_DIR, j)) as zin, \
            zipfile.ZipFile(out, 'w', zipfile.ZIP_DEFLATED) as zout:
        for info in zin.infolist():
            if is_replaced(info.filename):
                dropped += 1
                names.append(info.filename)
                continue
            zout.writestr(info, zin.read(info.filename))
            kept += 1
    print('%s: kept %d, dropped %d -> %s' % (j, kept, dropped, out))
    for nm in sorted(names):
        print('   dropped', nm)
PY
