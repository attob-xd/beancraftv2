#!/bin/sh
# Builds libs-1.18.2-derived/lwjgl-*-trimmed.jar: LWJGL with every class this project
# replaces removed.
#
# 1.18.2 talks to the desktop through LWJGL - GLFW for the window and input, MemoryUtil for
# off-heap buffers, a thin slice of GL11 - and 29 vanilla classes name it directly. The
# earlier assumption here was that none of it was reachable, so the jars could stay whole and
# only supply metadata. That was wrong: RenderSystem.initBackendSystem() calls
# GLX._initGlfw(), which calls GLFW.glfwInit(), and the client died there before drawing
# anything.
#
# Rather than rewrite those 29 vanilla classes, this port replaces LWJGL itself (see
# src/main/java/org/lwjgl) and cuts one seam in vanilla, GLX. That keeps MouseHandler,
# KeyboardHandler and the whole of RenderSystem running unmodified.
#
# The jar has to lose what we supply, for the same reason trim_vanilla.sh exists: classpath
# order does not settle a duplicate, the jar wins.
#
# The check at the end is the part that matters. LWJGL's classes are deeply subclassed - if a
# replaced class is still someone's superclass, dropping it turns into a missing class, and
# TeaVM evaluates supertypes when the page loads, so that is a ReferenceError before a line
# of game code runs rather than a lazy failure. Three classes were already withdrawn from the
# replacement set for exactly that reason (Pointer, whose Default is Callback's superclass;
# CustomBuffer, which StructBuffer extends; and GLFWVidMode, which is a view onto a native
# struct). This check refuses to write a jar that would repeat it.
set -e
cd "$(dirname "$0")/.."
python - <<'PY'
import os
import re
import struct
import sys
import zipfile

SRC = os.path.join('src', 'main', 'java')
IN_DIR = 'libs-1.18.2'
OUT_DIR = 'libs-1.18.2-derived'

# Every org.lwjgl class this project supplies as source. `replaced` holds the top-level
# names, which is what decides whether a jar entry is dropped; `supplied` also holds the
# nested types those files declare, because MemoryUtil.MemoryAllocator and the like are
# still provided even though the jar entry MemoryUtil$MemoryAllocator is dropped with its
# outer class. The supertype check below has to know the difference or it flags every
# nested type as missing.
NESTED = re.compile(r'^\s*(?:public\s+|protected\s+|private\s+|static\s+|final\s+|abstract\s+)*'
                    r'(?:class|interface|enum|@interface)\s+(\w+)')
replaced = set()
supplied = set()
root_lwjgl = os.path.join(SRC, 'org', 'lwjgl')
for root, _, files in os.walk(root_lwjgl):
    for f in files:
        if not f.endswith('.java'):
            continue
        rel = os.path.relpath(os.path.join(root, f), SRC).replace(os.sep, '/')
        name = rel[:-len('.java')]
        replaced.add(name)
        supplied.add(name)
        outer = name.rsplit('/', 1)[-1]
        with open(os.path.join(root, f), encoding='utf8') as fh:
            for line in fh:
                m = NESTED.match(line)
                if m and m.group(1) != outer:
                    supplied.add(name + '$' + m.group(1))

if not replaced:
    print('no org.lwjgl sources found; nothing to trim')
    sys.exit(0)


def hierarchy(data):
    """(this, superclass, interfaces) from a class file, or None if unparseable."""
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
            consts[i] = (tag, data[off], struct.unpack('>H', data[off + 1:off + 3])[0])
            off += 3
        elif tag in (3, 4, 9, 10, 11, 12, 17, 18):
            consts[i] = (tag, struct.unpack('>HH', data[off:off + 4]))
            off += 4
        elif tag in (5, 6):
            consts[i] = (tag, data[off:off + 8])
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

    off += 2                                              # access flags
    this = cls(struct.unpack('>H', data[off:off + 2])[0])
    off += 2
    sup = cls(struct.unpack('>H', data[off:off + 2])[0])
    off += 2
    count = struct.unpack('>H', data[off:off + 2])[0]
    off += 2
    ifs = [cls(struct.unpack('>H', data[off + 2 * k:off + 2 * k + 2])[0]) for k in range(count)]
    return this, sup, ifs


def is_replaced(entry):
    if not entry.endswith('.class'):
        return False
    # Foo.class and its nested Foo$Bar.class both belong to Foo
    return entry[:-len('.class')].split('$', 1)[0] in replaced


os.makedirs(OUT_DIR, exist_ok=True)
jars = sorted(f for f in os.listdir(IN_DIR) if f.startswith('lwjgl') and f.endswith('.jar'))
if not jars:
    print('no lwjgl jars in %s' % IN_DIR)
    sys.exit(1)

# Pass one: what would go missing, across all the jars together - dropped from the jar and
# not supplied back as source.
dropped_names = set()
for j in jars:
    with zipfile.ZipFile(os.path.join(IN_DIR, j)) as z:
        for nm in z.namelist():
            if is_replaced(nm):
                stem = nm[:-len('.class')]
                if stem not in supplied:
                    dropped_names.add(stem)

# Pass two: does anything that survives still extend one of them?
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
                if parent in dropped_names:
                    orphans.append((this, parent))

if orphans:
    print('REFUSING to trim: these classes would lose a supertype, which TeaVM resolves when')
    print('the page loads - the result is a ReferenceError before any game code runs.')
    for this, parent in sorted(set(orphans)):
        print('   %s extends/implements %s' % (this, parent))
    print('Either supply the missing supertype in src/main/java/org/lwjgl, or stop replacing')
    print('that class and answer differently at the call site (see GLFW.glfwGetVideoModes).')
    sys.exit(1)

total_kept = total_dropped = 0
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
    total_kept += kept
    total_dropped += dropped
    print('%s: kept %d, dropped %d -> %s' % (j, kept, dropped, out))
    for nm in sorted(names):
        print('   dropped', nm)

print('replaced by src/main/java/org/lwjgl: %d classes' % len(replaced))
print('totals: kept %d, dropped %d' % (total_kept, total_dropped))
PY
