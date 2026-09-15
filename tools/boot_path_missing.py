"""Walk the call graph from the client's entry point and report the missing methods on it.

    python tools/boot_path_missing.py /tmp/build.log [entry]

The problem this solves: a TeaVM build reports ~200 missing methods, almost all of them on
paths a browser never takes (rcon, LAN discovery, Realms HTTP, the dedicated server). Only a
handful are reached while the client starts, and each one costs a twenty-minute build to
discover by running it. This finds them by reading instead.

It walks method bodies from `net.minecraft.client.main.Main.appMain()` outward and prints
every missing method it can reach, with the chain that reaches it. That chain is the useful
part: it says *why* the call happens, which is usually enough to decide whether to shim the
JDK method, replace the vanilla class, or leave it alone.

Deliberate approximations, both in the direction of over-reporting:

  - A virtual call is resolved to its declared owner and to that owner's supertypes, not to
    every possible override. A call through an interface to an implementation the walk has
    not otherwise reached is therefore missed.
  - Reachability ignores branches, so code behind `if (IS_RUNNING_IN_IDE)` counts as reached.

So treat the output as "worth looking at", not "will definitely happen" - the chain tells you
which. Under-reporting would be the dangerous direction, and the first approximation is the
one to remember when something crashes that this did not predict.
"""
import collections
import os
import re
import struct
import sys
import zipfile

MISSING_RE = re.compile(r'Method ([a-zA-Z0-9_.$]+)\.([a-zA-Z0-9_<>$]+)(\([^)]*\)\S*) was not found')
DEFAULT_ENTRY = ('net/minecraft/client/main/Main', 'appMain', '()V')
# Opcodes that name a method in the constant pool.
INVOKES = {0xB6: 'virtual', 0xB7: 'special', 0xB8: 'static', 0xB9: 'interface'}


def parse_class(data):
    """(name, super, interfaces, {(method, desc): [(owner, name, desc)]}) or None."""
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

    def memberref(index):
        v = consts.get(index)
        if not v or v[0] not in (9, 10, 11):
            return None
        owner = cls(v[1][0])
        nat = consts.get(v[1][1])
        if not owner or not nat or nat[0] != 12:
            return None
        return owner, utf(nat[1][0]), utf(nat[1][1])

    def ownerref(index):
        """The class named by a Class entry (new) or a Fieldref (getstatic/putstatic)."""
        v = consts.get(index)
        if not v:
            return None
        if v[0] == 7:
            return utf(v[1])
        if v[0] == 9:
            return cls(v[1][0])
        return None

    off += 2
    this = cls(struct.unpack('>H', data[off:off + 2])[0])
    off += 2
    sup = cls(struct.unpack('>H', data[off:off + 2])[0])
    off += 2
    icount = struct.unpack('>H', data[off:off + 2])[0]
    off += 2
    ifaces = [cls(struct.unpack('>H', data[off + 2 * k:off + 2 * k + 2])[0]) for k in range(icount)]
    off += 2 * icount

    def skip_attrs(o):
        count = struct.unpack('>H', data[o:o + 2])[0]
        o += 2
        for _ in range(count):
            length = struct.unpack('>I', data[o + 2:o + 6])[0]
            o += 6 + length
        return o

    fcount = struct.unpack('>H', data[off:off + 2])[0]
    off += 2
    for _ in range(fcount):
        off = skip_attrs(off + 6)

    methods = {}
    mcount = struct.unpack('>H', data[off:off + 2])[0]
    off += 2
    for _ in range(mcount):
        name = utf(struct.unpack('>H', data[off + 2:off + 4])[0])
        desc = utf(struct.unpack('>H', data[off + 4:off + 6])[0])
        off += 6
        acount = struct.unpack('>H', data[off:off + 2])[0]
        off += 2
        calls = []
        for _ in range(acount):
            aname = utf(struct.unpack('>H', data[off:off + 2])[0])
            alen = struct.unpack('>I', data[off + 2:off + 6])[0]
            abody = off + 6
            if aname == 'Code':
                code_len = struct.unpack('>I', data[abody + 4:abody + 8])[0]
                code = data[abody + 8:abody + 8 + code_len]
                calls.extend(scan_code(code, memberref, ownerref))
            off = abody + alen
        methods[(name, desc)] = calls
    return this, sup, ifaces, methods


def scan_code(code, memberref, ownerref):
    """Methods this bytecode can enter: those it invokes, plus the static initialisers it
    triggers.

    The <clinit> part matters. Class initialisation is where a great deal of Minecraft's work
    happens - every registry, every Block and Item, the worldgen noise tables - and it is
    started by `new` and by reading a static field, not by a call. Without following those,
    this tool missed a real crash: Integer.toUnsignedLong, reached only through
    BlendedNoise's static initialiser during Bootstrap.

    A desynchronised scan would read operand bytes as opcodes and invent references, so if
    the walk runs past the end - meaning the operand table is wrong for some opcode in this
    method - the method is abandoned rather than half-trusted.
    """
    try:
        return _scan_code(code, memberref, ownerref)
    except (IndexError, struct.error):
        return []


def _scan_code(code, memberref, ownerref):
    out = []
    i = 0
    n = len(code)
    while i < n:
        op = code[i]
        if op in INVOKES:
            ref = memberref(struct.unpack('>H', code[i + 1:i + 3])[0])
            if ref:
                out.append(ref)
            i += 5 if op == 0xB9 else 3
        elif op == 0xBA:                                  # invokedynamic
            i += 5
        elif op in (0xBB, 0xB2, 0xB3):                    # new, getstatic, putstatic
            owner = ownerref(struct.unpack('>H', code[i + 1:i + 3])[0])
            if owner:
                out.append((owner, '<clinit>', '()V'))
            i += 3
        elif op == 0xC4:                                  # wide
            i += 6 if code[i + 1] == 0x84 else 4
        elif op in (0xAA, 0xAB):                          # tableswitch / lookupswitch
            pad = (4 - ((i + 1) % 4)) % 4
            j = i + 1 + pad
            if op == 0xAA:
                low = struct.unpack('>i', code[j + 4:j + 8])[0]
                high = struct.unpack('>i', code[j + 8:j + 12])[0]
                i = j + 12 + 4 * (high - low + 1)
            else:
                npairs = struct.unpack('>i', code[j + 4:j + 8])[0]
                i = j + 8 + 8 * npairs
        else:
            i += 1 + OPERAND_BYTES.get(op, 0)
    return out


# Operand widths for the opcodes that have them; everything else is a bare opcode. These
# ranges are inclusive of their last opcode - getting that wrong desynchronises the whole
# scan and invents method references out of operand bytes, so they are spelled out.
OPERAND_BYTES = {}
for _op in (list(range(0x15, 0x1A))            # iload, lload, fload, dload, aload
            + list(range(0x36, 0x3B))          # istore, lstore, fstore, dstore, astore
            + [0x10, 0x12, 0xA9, 0xBC]):       # bipush, ldc, ret, newarray
    OPERAND_BYTES[_op] = 1
for _op in (list(range(0x99, 0xA9))            # ifeq..jsr
            + [0x11, 0x13, 0x14, 0x84, 0xB2, 0xB3, 0xB4, 0xB5,
               0xBB, 0xBD, 0xC0, 0xC1, 0xC6, 0xC7]):
    OPERAND_BYTES[_op] = 2
OPERAND_BYTES[0xC5] = 3                        # multianewarray
OPERAND_BYTES[0xC8] = 4                        # goto_w
OPERAND_BYTES[0xC9] = 4                        # jsr_w


def load_universe():
    classes = {}
    for directory in ('build/classes/java/main', 'build/classes/java/niofile',
                      'build/classes/java/teavm'):
        for base, _, files in os.walk(directory):
            for name in files:
                if not name.endswith('.class'):
                    continue
                with open(os.path.join(base, name), 'rb') as fh:
                    parsed = parse_class(fh.read())
                if parsed:
                    classes.setdefault(parsed[0], parsed)
    for directory in ('libs-1.18.2-derived', 'libs-1.18.2'):
        if not os.path.isdir(directory):
            continue
        for name in sorted(os.listdir(directory)):
            if not name.endswith('.jar'):
                continue
            if directory == 'libs-1.18.2' and (name.startswith('lwjgl')
                                               or name.startswith('authlib')
                                               or name == '1.18.2-mapped.jar'):
                continue
            try:
                zf = zipfile.ZipFile(os.path.join(directory, name))
            except Exception:
                continue
            for entry in zf.namelist():
                if not entry.endswith('.class'):
                    continue
                parsed = parse_class(zf.read(entry))
                if parsed:
                    classes.setdefault(parsed[0], parsed)
    return classes


def main(log_path, entry_spec=None):
    missing = set()
    with open(log_path, encoding='utf8', errors='replace') as fh:
        for line in fh:
            m = MISSING_RE.search(line)
            if m:
                missing.add((m.group(1).replace('.', '/'), m.group(2), m.group(3)))
    print('missing methods reported by the build: %d' % len(missing))

    classes = load_universe()
    print('classes loaded: %d' % len(classes))

    entry = DEFAULT_ENTRY
    if entry_spec:
        owner, rest = entry_spec.rsplit('.', 1)
        name, desc = rest.split('(', 1)
        entry = (owner.replace('.', '/'), name, '(' + desc)

    def lookup(owner, name, desc):
        """The class that actually declares this method, walking up supertypes."""
        seen = set()
        stack = [owner]
        while stack:
            c = stack.pop()
            if c in seen or c not in classes:
                continue
            seen.add(c)
            _, sup, ifaces, methods = classes[c]
            if (name, desc) in methods:
                return c
            if sup:
                stack.append(sup)
            stack.extend(ifaces)
        return None

    start = (entry[0], entry[1], entry[2])
    queue = collections.deque([start])
    parent = {start: None}
    hits = {}
    visited = 0
    while queue:
        owner, name, desc = queue.popleft()
        visited += 1
        decl = lookup(owner, name, desc)
        if decl is None:
            if (owner, name, desc) in missing:
                hits[(owner, name, desc)] = (owner, name, desc)
            continue
        for callee in classes[decl][3].get((name, desc), ()):
            key = callee
            if key in parent:
                continue
            parent[key] = (owner, name, desc)
            if key in missing:
                hits[key] = key
            queue.append(key)
    print('methods walked: %d\n' % visited)

    if not hits:
        print('no missing method is reachable from %s.%s%s' % entry)
        return 0
    print('MISSING METHODS REACHABLE FROM %s.%s%s: %d\n' % (entry[0], entry[1], entry[2],
                                                            len(hits)))
    for key in sorted(hits):
        print('%s.%s%s' % key)
        chain = []
        cur = parent.get(key)
        while cur is not None and len(chain) < 6:
            chain.append(cur)
            cur = parent.get(cur)
        for step in chain:
            print('    via %s.%s%s' % step)
        print()
    return 0


if __name__ == '__main__':
    sys.exit(main(sys.argv[1] if len(sys.argv) > 1 else '/tmp/build.log',
                  sys.argv[2] if len(sys.argv) > 2 else None))
