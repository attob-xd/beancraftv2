"""Find regexes that TeaVM's regex engine refuses to compile, or silently misreads.

    python tools/scan_bad_regex.py [root ...]      # defaults to the vanilla reference tree

TeaVM's TLexer has an explicit list of escape characters it throws on, and it raises
TPatternSyntaxException with an *empty* description - so all that reaches the log is an index
and the pattern, with nothing saying which escape was at fault. `\\h` is on that list, and
vanilla's GlslPreprocessor uses it, so every shader failed to preload and the client died with
"could not preload blit shader" before drawing a menu.

Worse than the ones it rejects are the ones it accepts and gets wrong. `\\v` falls into the
lexer's literal-escape group, so TeaVM reads it as the letter v where Java reads it as the
vertical-whitespace class - no exception, just different matching. That is reported separately
below, because nothing at runtime will ever point at it.

Finding these by running the client costs a 20-minute build each. This reads the source
instead and finds them all at once.

The lists are read straight off TLexer's switch statement in teavm-classlib 0.9.2. Escapes it
*does* support: \\d \\D \\s \\S \\w \\W \\p{...} \\P{...} \\b \\B \\A \\G \\Z \\z \\Q \\E \\n
\\r \\t \\f \\a \\e \\cX \\0nnn \\xNN and four-hex-digit unicode escapes - so a rejected class
can normally be written out, which is what GlslPreprocessor does.
"""
import os
import re
import sys

# Char codes TLexer throws on, as letters.
REJECTED = set('CEFHIJKLMNORTUVXY') | set('ghijklmoqy')

# Escapes TLexer treats as the bare literal character while Java gives them a meaning.
# 'v' is the only letter in TLexer's literal group; the rest of that group is punctuation,
# which Java also treats as literal, so it agrees there.
MISREAD = {'v': 'vertical whitespace in Java, the letter v in TeaVM'}

# The ways a regex literal reaches the engine.
CALLS = [
    r'Pattern\.compile\(\s*"((?:[^"\\]|\\.)*)"',
    r'\.split\(\s*"((?:[^"\\]|\\.)*)"',
    r'\.matches\(\s*"((?:[^"\\]|\\.)*)"',
    r'\.replaceAll\(\s*"((?:[^"\\]|\\.)*)"',
    r'\.replaceFirst\(\s*"((?:[^"\\]|\\.)*)"',
]
CALL_RES = [re.compile(c) for c in CALLS]

SENTINEL = '\x00'


def unescape_java_literal(literal):
    """One level of Java string unescaping, enough to see the regex the compiler gets."""
    out = literal.replace('\\\\', SENTINEL)
    out = out.replace('\\"', '"')
    return out.replace(SENTINEL, '\\')


def classify(regex):
    """Returns (rejected escapes, silently misread escapes) present in the pattern."""
    rejected = set()
    misread = set()
    i = 0
    while i < len(regex) - 1:
        if regex[i] == '\\':
            nxt = regex[i + 1]
            if nxt in REJECTED:
                rejected.add(nxt)
            if nxt in MISREAD:
                misread.add(nxt)
            i += 2
            continue
        i += 1
    return rejected, misread


def scan(root):
    fatal = []
    silent = []
    scanned = 0
    for dirpath, dirnames, filenames in os.walk(root):
        dirnames[:] = [d for d in dirnames if d != 'build']
        for name in filenames:
            if not name.endswith('.java'):
                continue
            path = os.path.join(dirpath, name)
            scanned += 1
            with open(path, encoding='utf8', errors='replace') as fh:
                source = fh.read()
            for call_re in CALL_RES:
                for match in call_re.finditer(source):
                    regex = unescape_java_literal(match.group(1))
                    rejected, misread = classify(regex)
                    rel = os.path.relpath(path, root).replace(os.sep, '/')
                    if rejected:
                        fatal.append((rel, sorted(rejected), regex))
                    if misread:
                        silent.append((rel, sorted(misread), regex))
    return scanned, fatal, silent


def report(title, hits, explain):
    print('%s: %d' % (title, len(hits)))
    for rel, bad, regex in hits:
        print('  %s' % rel)
        print('      %s: %s' % (explain, ', '.join('\\' + b for b in bad)))
        print('      pattern: %s' % regex)


def main(roots):
    total = 0
    fatal = []
    silent = []
    for root in roots:
        if not os.path.isdir(root):
            print('skipping %s (not a directory)' % root)
            continue
        n, f, s = scan(root)
        print('scanned %d files under %s' % (n, root))
        total += n
        fatal += f
        silent += s
    print()
    report('patterns TeaVM will REFUSE to compile', fatal, 'rejected escapes')
    print()
    report('patterns TeaVM will SILENTLY misread', silent, 'misread escapes')
    return 1 if fatal else 0


if __name__ == '__main__':
    sys.exit(main(sys.argv[1:] or ['decompiled-1.18.2-reference']))
