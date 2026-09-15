"""Map TeaVM's "method was not found" diagnostics back to the classes that call them.

    python tools/who_calls_missing.py /tmp/build.log

TeaVM tells you what is missing but not who wants it, and the two questions have very
different answers. Of the 221 diagnostics in one build, almost all were library plumbing on
paths the game never takes; the handful that mattered were the ones a vanilla class on the
boot path called, and those are the ones that cost a 20-minute build cycle each when found by
running the client instead of by reading it.

So this prints, for every missing method, the classes that reference it - and sorts vanilla
and this project's own classes to the top, because those are the ones that will actually be
executed.

It reads the constant pool directly rather than resolving anything, so a reference here means
"this class names that method", not "this call is reachable". That over-reports, which is the
right way round: a name that appears nowhere is definitely safe to ignore.
"""
import collections
import os
import re
import struct
import sys
import zipfile

MISSING_RE = re.compile(r'Method ([a-zA-Z0-9_.$]+)\.([a-zA-Z0-9_<>$]+)(\([^)]*\)\S*) was not found')
# Classes worth acting on first: vanilla, Mojang's libraries, and this port's own code.
INTERESTING = ('net/minecraft/', 'com/mojang/', 'net/lax1dude/')


def constant_pool_refs(data):
    """Every (owner, name, descriptor) this class names in its constant pool."""
    if data[:4] != b'\xca\xfe\xba\xbe':
        return []
    count = struct.unpack('>H', data[8:10])[0]
    off = 10
    consts = {}
    i = 1
    while i < count:
        tag = data[off]
        off += 1
        if tag == 1:
            length = struct.unpack('>H', data[off:off + 2])[0]
            off += 2
            consts[i] = ('utf8', data[off:off + length].decode('utf8', 'replace'))
            off += length
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
            i += 1                                    # longs and doubles take two slots
        else:
            return []
        i += 1

    def utf(index):
        value = consts.get(index)
        return value[1] if value and value[0] == 'utf8' else None

    def cls(index):
        value = consts.get(index)
        return utf(value[1]) if value and value[0] == 7 else None

    out = []
    for value in consts.values():
        if value[0] in (9, 10, 11):                   # Fieldref, Methodref, InterfaceMethodref
            class_index, nat_index = value[1]
            owner = cls(class_index)
            nat = consts.get(nat_index)
            if owner and nat and nat[0] == 12:
                out.append((owner, utf(nat[1][0]), utf(nat[1][1])))
    return out


def iter_classes():
    """(source label, class name, bytes) over every class the build feeds to TeaVM."""
    for directory in ('build/classes/java/main', 'build/classes/java/niofile',
                      'build/classes/java/teavm'):
        for base, _, files in os.walk(directory):
            for name in files:
                if name.endswith('.class'):
                    path = os.path.join(base, name)
                    rel = os.path.relpath(path, directory).replace(os.sep, '/')
                    with open(path, 'rb') as fh:
                        yield 'src', rel[:-len('.class')], fh.read()
    for directory in ('libs-1.18.2-derived', 'libs-1.18.2'):
        if not os.path.isdir(directory):
            continue
        for name in sorted(os.listdir(directory)):
            if not name.endswith('.jar'):
                continue
            # The derived jars supersede their originals; skip the untrimmed ones.
            if directory == 'libs-1.18.2' and (name.startswith('lwjgl')
                                               or name == '1.18.2-mapped.jar'):
                continue
            try:
                zf = zipfile.ZipFile(os.path.join(directory, name))
            except Exception:
                continue
            for entry in zf.namelist():
                if entry.endswith('.class'):
                    yield name, entry[:-len('.class')], zf.read(entry)


def main(log_path):
    missing = set()
    with open(log_path, encoding='utf8', errors='replace') as fh:
        for line in fh:
            match = MISSING_RE.search(line)
            if match:
                owner = match.group(1).replace('.', '/')
                missing.add((owner, match.group(2), match.group(3)))
    if not missing:
        print('no "method was not found" diagnostics in %s' % log_path)
        return 0
    print('missing methods: %d' % len(missing))

    callers = collections.defaultdict(set)
    scanned = 0
    for source, name, data in iter_classes():
        scanned += 1
        for ref in constant_pool_refs(data):
            if ref in missing:
                callers[ref].add((source, name))
    print('scanned %d classes\n' % scanned)

    def rank(item):
        (owner, _, _), who = item
        interesting = any(n.startswith(INTERESTING) for _, n in who)
        return (not interesting, owner)

    for (owner, name, desc), who in sorted(callers.items(), key=rank):
        hot = sorted(n for _, n in who if n.startswith(INTERESTING))
        cold = sorted(n for _, n in who if not n.startswith(INTERESTING))
        mark = '!!' if hot else '  '
        print('%s %s.%s%s' % (mark, owner, name, desc))
        # Every caller is printed, hot ones first - not the first handful of one group.
        #
        # This used to stop at six and print "... and N more", and that is exactly how the
        # one that mattered got hidden. Collections.unmodifiableSortedMap listed guava's Maps
        # and Tables first and truncated the rest; org.apache.commons.io.Charsets was among
        # the five it did not show, and Charsets is what actually ran - vanilla's
        # ShaderInstance reads every shader through IOUtils.toString, whose first act is to
        # touch that class. The truncation cost a whole build cycle.
        #
        # The same case is why the !! ranking must not be read as a filter. A method is marked
        # hot only when a vanilla, Mojang or lax1dude class names it *directly*, so a library
        # class that vanilla calls straight through - commons-io, guava, netty - lands in the
        # quiet list and looks ignorable. It is not. Vanilla reaching it is enough to run it.
        # Read !! as "start here", never as "the rest are safe".
        for n in hot:
            print('       <- %s' % n)
        for n in cold:
            print('       <- %s' % n)
    unreferenced = missing - set(callers)
    print('\n%d missing methods are named by nothing on the classpath '
          '(TeaVM synthesised the reference)' % len(unreferenced))
    return 0


if __name__ == '__main__':
    sys.exit(main(sys.argv[1] if len(sys.argv) > 1 else '/tmp/build.log'))
