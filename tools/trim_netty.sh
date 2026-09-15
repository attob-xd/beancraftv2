#!/bin/sh
# netty-all is an uber jar: it carries the epoll and kqueue native transports, the DNS
# resolver, the SSL stack and the HTTP codecs alongside the handful of packages 1.18.2's
# Connection actually uses. In a browser build none of that is reachable in practice, but
# TeaVM's fast global analysis cannot know it, so it compiles them - and the DNS resolver
# reaches javax.naming, whose reflective provider lookup made TeaVM treat every class on
# the classpath as instantiable.
#
# io/netty/buffer is dropped for a different reason: EaglercraftX vendors its own
# TeaVM-safe ByteBuf as source (with methods real netty does not have, such as
# Unpooled.buffer(byte[], int)), and javac resolves a class found both ways by file date,
# so the 2021 jar won on incremental builds and the vendored one on full ones.
set -e
cd "$(dirname "$0")/.."
IN=libs-1.18.2/netty-all-4.1.68.Final.jar
OUT_DIR=libs-1.18.2-derived
OUT=$OUT_DIR/netty-all-4.1.68.Final-trimmed.jar
mkdir -p "$OUT_DIR"
python - "$IN" "$OUT" <<'PY'
import sys, zipfile

DROP = (
    'io/netty/buffer/',            # vendored by EaglercraftX as source
    'io/netty/channel/epoll/',     # Linux native transport
    'io/netty/channel/kqueue/',    # BSD native transport
    'io/netty/channel/unix/',
    'io/netty/resolver/dns/',      # reaches javax.naming
    'io/netty/handler/ssl/',
    'io/netty/handler/proxy/',
    'io/netty/handler/codec/http',
    'io/netty/handler/codec/dns/',
    'io/netty/handler/codec/spdy/',
    'io/netty/handler/codec/haproxy/',
    'io/netty/handler/codec/memcache/',
    'io/netty/handler/codec/mqtt/',
    'io/netty/handler/codec/redis/',
    'io/netty/handler/codec/smtp/',
    'io/netty/handler/codec/stomp/',
    'io/netty/handler/codec/socksx/',
    'io/netty/handler/codec/socks/',
    'META-INF/native/',
    # ThreadProperties declares `Thread.State state()`, and TeaVM's Thread has no State
    # enum. TeaVM writes a return type into class metadata that is evaluated when the page
    # loads, so that one signature was a ReferenceError before anything ran. EaglercraftX
    # supplies its own ThreadProperties without that method (src/main/java); dropping the
    # jar's copy makes which one wins a fact rather than a question of classpath order.
    'io/netty/util/concurrent/ThreadProperties.class',
    # TypeParameterMatcher reads a handler's generic signature to decide which messages it
    # wants, through getGenericSuperclass() and getTypeParameters() - neither of which TeaVM
    # has. SimpleChannelInboundHandler's constructor calls it, so constructing any Connection
    # threw NoSuchMethodError, which is every route into a world. The replacement is in
    # src/main/java; the prefix also takes the two nested classes.
    'io/netty/util/internal/TypeParameterMatcher',
)
src, dst = sys.argv[1], sys.argv[2]
kept = dropped = 0
with zipfile.ZipFile(src) as zin, zipfile.ZipFile(dst, 'w', zipfile.ZIP_DEFLATED) as zout:
    for info in zin.infolist():
        if info.filename.startswith(DROP):
            dropped += 1
            continue
        zout.writestr(info, zin.read(info.filename))
        kept += 1
print('kept %d entries, dropped %d -> %s' % (kept, dropped, dst))
PY
