"""List (and optionally extract) the entries inside an EaglercraftX .epk pack.

    python tools/list_epk.py [javascript/assets.epk] [substring filter]
    python tools/list_epk.py javascript/assets.epk font/ --extract out_dir

Why this exists: the browser reads **every** game resource out of `javascript/assets.epk`
and never off disk, so "is this file actually in the pack?" is the first question whenever
something works from `desktopRuntime/resources` but not in the page. Grepping the .epk cannot
answer it - names are deflated along with the data - and running EaglercraftX's own
`EPKDecompiler` on a desktop JVM drags in TeaVM's platform classes. This reimplements the
reader, which is small.

Format (ver2, from src/main/java/.../export/EPKDecompiler.java):

    "EAGPKG$$"                        8-byte magic
    <ascii>  version, must start "ver2."      (1 length byte, then that many bytes)
    <ascii>  original filename
    uint16 + bytes                    comment
    8 bytes                           millis date
    int32                             file count
    1 byte                            compression: 'G' gzip, 'Z' zlib, '0' none
    ...then, in the compressed stream, `count` entries of:
        4 bytes   type ("FILE", "DIR$", ...)
        <ascii>   name
        int32     length
        for FILE: int32 crc32, (length-5) bytes data, then ':'
        else:     length bytes
        1 byte    '>'
    ...followed by "END$" and, at the very end of the OUTER file, ":::YEE:>".

All integers are big-endian.
"""
import binascii
import io
import os
import sys
import zlib

MAGIC = b'EAGPKG$$'
LEGACY_MAGIC = b'EAGPKG!!'
END_CODE = b':::YEE:>'


class Reader:
    def __init__(self, data):
        self.d = data
        self.i = 0

    def read(self, n):
        if self.i + n > len(self.d):
            raise IOError('unexpected end of pack')
        out = self.d[self.i:self.i + n]
        self.i += n
        return out

    def u8(self):
        return self.read(1)[0]

    def u16(self):
        b = self.read(2)
        return (b[0] << 8) | b[1]

    def i32(self):
        b = self.read(4)
        v = (b[0] << 24) | (b[1] << 16) | (b[2] << 8) | b[3]
        return v - (1 << 32) if v >= (1 << 31) else v

    def ascii(self):
        return self.read(self.u8()).decode('latin-1')


def read_epk(path):
    raw = open(path, 'rb').read()
    if raw[:8] == LEGACY_MAGIC:
        raise SystemExit('legacy EPK format is not supported')
    if raw[:8] != MAGIC:
        raise SystemExit('not an EPK file: ' + path)
    if raw[-8:] != END_CODE:
        raise SystemExit('EPK is missing its end code (:::YEE:>) - truncated?')

    head = Reader(raw[8:len(raw) - 8])
    version = head.ascii()
    if not version.startswith('ver2.'):
        raise SystemExit('unsupported EPK version: ' + version)
    head.ascii()                       # original filename
    head.read(head.u16())              # comment
    head.read(8)                       # millis date
    count = head.i32()
    compression = chr(head.u8())

    body = head.d[head.i:]
    if compression == 'G':
        body = zlib.decompress(body, 16 + zlib.MAX_WBITS)
    elif compression == 'Z':
        body = zlib.decompress(body)
    elif compression != '0':
        raise SystemExit('unsupported EPK compression: ' + compression)

    r = Reader(body)
    entries = []
    for _ in range(count):
        kind = r.read(4).decode('latin-1')
        if kind == 'END$':
            raise SystemExit('unexpected END with %d entries left' % (count - len(entries)))
        name = r.ascii()
        length = r.i32()
        if kind == 'FILE':
            crc = r.i32() & 0xFFFFFFFF
            data = r.read(length - 5)
            if binascii.crc32(data) & 0xFFFFFFFF != crc:
                raise SystemExit('bad checksum for ' + name)
            if r.read(1) != b':':
                raise SystemExit('truncated file ' + name)
        else:
            data = r.read(length)
        if r.read(1) != b'>':
            raise SystemExit('truncated object ' + name)
        entries.append((kind, name, data))
    return version, entries


def main():
    args = [a for a in sys.argv[1:] if not a.startswith('--')]
    path = args[0] if args else 'javascript/assets.epk'
    needle = args[1] if len(args) > 1 else None
    extract = None
    if '--extract' in sys.argv:
        extract = sys.argv[sys.argv.index('--extract') + 1]

    version, entries = read_epk(path)
    files = [e for e in entries if e[0] == 'FILE']
    matched = [e for e in files if needle is None or needle in e[1]]

    print('%s  (%s, %d entries, %d files)' % (path, version, len(entries), len(files)))
    if needle is not None:
        print('matching "%s": %d' % (needle, len(matched)))
    for kind, name, data in matched[:200]:
        print('   %-70s %8d bytes' % (name, len(data)))
    if len(matched) > 200:
        print('   ... and %d more' % (len(matched) - 200))

    if extract:
        for kind, name, data in matched:
            dest = os.path.join(extract, name.replace('/', os.sep))
            os.makedirs(os.path.dirname(dest), exist_ok=True)
            with open(dest, 'wb') as f:
                f.write(data)
        print('extracted %d file(s) into %s' % (len(matched), extract))


if __name__ == '__main__':
    main()
