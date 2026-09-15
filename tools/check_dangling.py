"""Find symbols the emitted JavaScript references but never defines.

Run this on javascript/classes.js after every build, BEFORE testing in a browser:

    python tools/check_dangling.py javascript/classes.js

Why it exists: TeaVM writes each class's supertypes, field types and parameter types into
metadata that the browser evaluates as the script loads. If one of those classes was not
emitted, the result is a bare `ReferenceError: Xyz is not defined` before a single line of
game code runs - the page does not start at all, and the error names an obfuscated symbol
that says nothing about which Java class is missing.

TeaVM's own diagnostics do not reliably catch this. The build that prompted this script
reported 16 missing classes, and `java.awt.event.ActionListener` - the one that actually
broke the page - was not among them. It was reachable only through metadata, so the compiler
pruned it without complaint and then referred to it anyway.

The script also resolves each dangling symbol back to the Java classes that reference it, by
reading the names TeaVM records in its $rt_metadata tables. That is the part that turns
"Y7v is not defined" into "Realms' FileDownload declares a java.awt.event.ActionListener
field", which is what you actually need to fix it.
"""
import collections
import re
import sys

# TeaVM declares a class as `function X(){...}`, `var X=...` or `X=$rt_class...`.
DECL_PATTERNS = [
    re.compile(r'\bfunction\s+([A-Za-z_$][A-Za-z0-9_$]*)\s*\('),
    re.compile(r'\bvar\s+([A-Za-z_$][A-Za-z0-9_$]*)\s*='),
    re.compile(r'\b([A-Za-z_$][A-Za-z0-9_$]*)\s*=\s*\$rt_class'),
]
# Metadata positions that name a class: field types, parameter lists and return types.
#
# returnType was missed at first, and it cost a build. guava's Sets.newCopyOnWriteArraySet
# returns java.util.concurrent.CopyOnWriteArraySet, which TeaVM's class library does not
# have; the symbol appeared *only* as a return type, so the page loaded and then threw
# "ReferenceError: AHV4 is not defined" the moment anything touched that class's metadata -
# which vanilla swallowed as "Couldn't load pack metadata".
#
# Note that TYPE_RE matches "returnType:" too, since it ends in "type:" - but only by
# accident of the suffix, and only for the lowercase form. It is matched explicitly here so
# the coverage is deliberate rather than incidental.
TYPE_RE = re.compile(r'\btype:([A-Za-z_$][A-Za-z0-9_$]*)')
RETURN_RE = re.compile(r'returnType:([A-Za-z_$][A-Za-z0-9_$]*)')
PARAMS_RE = re.compile(r'parameterTypes:\[([^\]]*)\]')
IDENT_RE = re.compile(r'^[A-Za-z_$][A-Za-z0-9_$]*$')
# The positional table - $rt_metadata([Cls,"Name",pkgIdx,Super,[Interfaces],...]) - carries
# the interface list, which the patterns above do not see, and a missing interface is just as
# fatal as a missing field type. It is matched by its shape: a name string, a package index,
# a superclass symbol, then the bracketed interface list.
#
# Known gap: the superclass slot itself is not checked. Sweeping every identifier in these
# tables would cover it, but the tables span most of a 63 MB file and that took over ten
# minutes, which is long enough that nobody would run it. Every dangling symbol found so far
# appeared in a field or parameter type as well, so this is the cheap 95% rather than the
# expensive 100%.
IFACES_RE = re.compile(r'"[^"]*",\s*\d+\s*,\s*[A-Za-z_$][A-Za-z0-9_$]*\s*,\s*\[([^\]]*)\]')
# `SYM.$meta.methods=` / `SYM.$meta.fields=` marks whose metadata a reference sits in.
OWNER_RE = re.compile(r'([A-Za-z_$][A-Za-z0-9_$]*)\.\$meta\.(?:methods|fields)=')


def main(path):
    with open(path, encoding='utf8', errors='replace') as fh:
        src = fh.read()
    print('read %s (%d bytes)' % (path, len(src)))

    declared = set()
    for pattern in DECL_PATTERNS:
        for match in pattern.finditer(src):
            declared.add(match.group(1))
    print('declared symbols: %d' % len(declared))

    used = collections.Counter()
    for match in TYPE_RE.finditer(src):
        used[match.group(1)] += 1
    for match in RETURN_RE.finditer(src):
        used[match.group(1)] += 1
    for match in PARAMS_RE.finditer(src):
        for part in match.group(1).split(','):
            part = part.strip()
            if IDENT_RE.match(part):
                used[part] += 1
    for match in IFACES_RE.finditer(src):
        for part in match.group(1).split(','):
            part = part.strip()
            if IDENT_RE.match(part):
                used[part] += 1

    dangling = {k: v for k, v in used.items()
                if k not in declared and not k.startswith('$rt')}
    if not dangling:
        print('no dangling metadata symbols - the script should load')
        return 0

    print('DANGLING metadata symbols: %d '
          '(each is a ReferenceError when the page loads)' % len(dangling))
    # symbol -> Java name, from the `Sym,"Name"` pairs in the positional metadata tables.
    names = dict(re.findall(r'([A-Za-z_$][A-Za-z0-9_$]*),"([^"]{2,120})"', src))
    # One pass to find, for every dangling symbol, the metadata block it sits in.
    pattern = re.compile(r'\b(%s)\b' % '|'.join(re.escape(s) for s in dangling))
    owners = collections.defaultdict(set)
    for match in pattern.finditer(src):
        window = src[max(0, match.start() - 4000):match.start()]
        found = OWNER_RE.findall(window)
        if found:
            owners[match.group(1)].add(names.get(found[-1], found[-1]))
    for sym, count in sorted(dangling.items(), key=lambda kv: -kv[1]):
        who = ', '.join(sorted(owners.get(sym, ()))) or 'unknown'
        print('  %-8s %d refs  <- %s' % (sym, count, who))
    return 1


if __name__ == '__main__':
    sys.exit(main(sys.argv[1] if len(sys.argv) > 1 else 'javascript/classes.js'))
