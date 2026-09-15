"""Say what an obfuscated symbol in a browser stack trace actually is.

    python tools/whois_js.py LUA joH lx
    python tools/whois_js.py --callers LUA

Why it exists. A crash on the client thread reaches the console as a JavaScript error with
obfuscated frames - "TypeError: Cannot read properties of null (reading 'lx')" at "LUA" - and
the two obvious ways to turn that into a Java name both failed:

  - EaglercraftX's crash overlay carries a deobfuscated Java stack, but a crash on the client
    thread leaves the page unresponsive, so get_page_text, screenshots and javascript_tool all
    time out and the overlay cannot be read at all.
  - The console prints the exception object rather than the stack, and the formatter truncates
    it after the first frame or two.

So the names are recovered from classes.js instead, which is on disk and always readable.
TeaVM writes two kinds of table into it, and this reads both:

  - the positional metadata tables, `$rt_metadata([Sym,"JavaName",pkg,Super,[Ifaces],...])`,
    which name classes;
  - the virtual method tables inside them, `["javaMethodName",AHVx(Sym), ...]`, which name
    methods - and, read the other way, say which classes declare a given method slot. That is
    what turned "reading 'lx'" into "lx is InputStream.read(byte[],int,int), so the receiver
    is a null InputStream" rather than a guess about which field was null.

--callers additionally attributes every call site of a symbol to the function that contains
it, by bisecting the file's `function NAME(` offsets. Following that chain from the crashing
frame outwards is what identified the caller as Language.loadFromJson, and from there the
null was obvious: vanilla reads en_us.json with Class.getResourceAsStream, which is null under
TeaVM, and its catch clause covers JsonParseException and IOException only.
"""
import bisect
import collections
import re
import sys

CLASSES_JS = 'javascript/classes.js'

# Sym,"JavaName" pairs from the positional metadata tables.
CLASS_NAME_RE = re.compile(r'([A-Za-z_$][A-Za-z0-9_$]*),"([^"]{2,120})"')
# "javaMethodName",AHVx(Sym) pairs from the virtual method tables.
METHOD_RE = re.compile(r'"([A-Za-z_$][A-Za-z0-9_$<>]{1,60})",AHV[A-Za-z0-9_$]*\(([A-Za-z_$][A-Za-z0-9_$]*)\)')
# The slot name a class declares: ["slot",AHVx(impl), "slot",AHVx(impl)] inside a class entry.
SLOT_RE = re.compile(r'\["([A-Za-z_$][A-Za-z0-9_$]{0,40})",AHV')
FUNC_RE = re.compile(r'\nfunction ([A-Za-z_$][A-Za-z0-9_$]*)\(')


def load(path):
    with open(path, encoding='utf8', errors='replace') as fh:
        return fh.read()


def method_slot_owners(src, slot):
    """Classes whose method table declares this slot, with the Java name they give it."""
    out = []
    for m in re.finditer(r'"%s",AHV' % re.escape(slot), src):
        # The class entry looks like  Sym,"Name",pkg,Super,[...],flags,...,[ "slot",... ]
        window = src[max(0, m.start() - 700):m.start()]
        names = CLASS_NAME_RE.findall(window)
        if names:
            out.append(names[-1][1])
    return out


def describe(src, sym):
    print('=== %s ===' % sym)
    hit = re.search(r'\b%s\s*,\s*"([^"]{2,160})"' % re.escape(sym), src)
    if hit:
        print('  class metadata name : %s' % hit.group(1))
    mname = None
    for m in METHOD_RE.finditer(src):
        if m.group(2) == sym:
            mname = m.group(1)
            break
    if mname:
        print('  implements method   : %s' % mname)
    owners = method_slot_owners(src, sym)
    if owners:
        uniq = sorted(set(owners))
        print('  declared as a method slot by %d classes: %s'
              % (len(owners), ', '.join(uniq[:12]) + (' ...' if len(uniq) > 12 else '')))
    i = src.find('\nfunction %s(' % sym)
    if i >= 0:
        j = src.find('\nfunction ', i + 1)
        body = src[i + 1:j if j > 0 else i + 1200]
        print('  defined as a function, %d chars; first 300:' % len(body))
        print('    %s' % body[:300])
    if not (hit or mname or owners or i >= 0):
        print('  not found')
    print()


def callers(src, sym):
    defs = [(m.group(1), m.start()) for m in FUNC_RE.finditer(src)]
    starts = [d[1] for d in defs]

    def owner(pos):
        k = bisect.bisect_right(starts, pos) - 1
        return defs[k][0] if k >= 0 else '?'

    counts = collections.Counter()
    for m in re.finditer(r'\b%s\(' % re.escape(sym), src):
        who = owner(m.start())
        if who != sym:
            counts[who] += 1
    print('=== callers of %s ===' % sym)
    if not counts:
        print('  none found (it may be reached only through a method table)')
    for who, n in counts.most_common():
        print('  %-10s %d call site(s)' % (who, n))
    print()


def main(argv):
    want_callers = '--callers' in argv
    syms = [a for a in argv if not a.startswith('--')]
    if not syms:
        print(__doc__)
        return 2
    src = load(CLASSES_JS)
    print('read %s (%d bytes)\n' % (CLASSES_JS, len(src)))
    for sym in syms:
        describe(src, sym)
        if want_callers:
            callers(src, sym)
    return 0


if __name__ == '__main__':
    sys.exit(main(sys.argv[1:]))
