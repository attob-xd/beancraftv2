"""A WebSocket that answers nothing and reports what the client claims to be.

    python tools/eagler_handshake_probe.py [port]     (default 8099)

Then, in the client: Multiplayer -> Add Server -> ws://localhost:8099

Why this exists: the client opens an EaglercraftX handshake by announcing which Eaglercraft
protocols and which *Minecraft* protocol it speaks, and the server negotiates from that. This
port inherited `340` - Minecraft 1.12.2 - from the fork it came from, so every server was told
to speak 1.12.2 to a client that speaks 1.18.2. Checking that the fix took needs nothing more
than seeing the first packet, and seeing it does not need a Minecraft server, a plugin, or
anyone's agreement to a licence.

So this speaks just enough WebSocket to complete the upgrade, reads one binary frame, decodes
the handshake header and prints it. It never replies, so the client times out and reports a
failed connection - which is the expected and correct outcome. The question being asked is only
"what did it say about itself".

The frame format, from ConnectionHandshake.java:

    u8    packet type (0x01 = PROTOCOL_CLIENT_VERSION)
    u8    legacy protocol version
    u16   count, then that many u16  - Eaglercraft protocol versions
    u16   count, then that many u16  - Minecraft protocol versions
    u8+s  client brand
    u8+s  client version
    u8    password flag
    u8+s  username
"""
import base64
import hashlib
import socket
import struct
import sys

WS_MAGIC = b"258EAFA5-E914-47DA-95CA-C5AB0DC85B11"

# Minecraft protocol numbers worth naming in the output.
KNOWN = {
    47: "1.8.x",
    340: "1.12.2",
    754: "1.16.5",
    755: "1.17",
    756: "1.17.1",
    757: "1.18 / 1.18.1",
    758: "1.18.2",
    759: "1.19",
}


def read_http_request(conn):
    data = b""
    while b"\r\n\r\n" not in data:
        chunk = conn.recv(4096)
        if not chunk:
            return None
        data += chunk
        if len(data) > 65536:
            return None
    return data


def websocket_accept(conn, request):
    key = None
    for line in request.split(b"\r\n"):
        if line.lower().startswith(b"sec-websocket-key:"):
            key = line.split(b":", 1)[1].strip()
    if key is None:
        conn.sendall(b"HTTP/1.1 400 Bad Request\r\n\r\n")
        return False
    accept = base64.b64encode(hashlib.sha1(key + WS_MAGIC).digest())
    conn.sendall(
        b"HTTP/1.1 101 Switching Protocols\r\n"
        b"Upgrade: websocket\r\n"
        b"Connection: Upgrade\r\n"
        b"Sec-WebSocket-Accept: " + accept + b"\r\n\r\n"
    )
    return True


def read_frame(conn):
    """One WebSocket frame. Enough of RFC 6455 to read what a browser sends."""
    hdr = recv_exactly(conn, 2)
    if hdr is None:
        return None
    length = hdr[1] & 0x7F
    masked = bool(hdr[1] & 0x80)
    if length == 126:
        length = struct.unpack(">H", recv_exactly(conn, 2))[0]
    elif length == 127:
        length = struct.unpack(">Q", recv_exactly(conn, 8))[0]
    mask = recv_exactly(conn, 4) if masked else None
    payload = recv_exactly(conn, length) if length else b""
    if payload is None:
        return None
    if mask:
        payload = bytes(b ^ mask[i % 4] for i, b in enumerate(payload))
    return payload


def recv_exactly(conn, n):
    out = b""
    while len(out) < n:
        chunk = conn.recv(n - len(out))
        if not chunk:
            return None
        out += chunk
    return out


class Reader:
    def __init__(self, data):
        self.d = data
        self.i = 0

    def u8(self):
        v = self.d[self.i]
        self.i += 1
        return v

    def u16(self):
        v = struct.unpack_from(">H", self.d, self.i)[0]
        self.i += 2
        return v

    def string(self):
        n = self.u8()
        s = self.d[self.i:self.i + n].decode("utf-8", "replace")
        self.i += n
        return s


def describe(payload):
    r = Reader(payload)
    print("  packet type       : 0x%02x" % r.u8())
    print("  legacy protocol   : %d" % r.u8())

    n = r.u16()
    eagler = [r.u16() for _ in range(n)]
    print("  eagler protocols  : %s" % ", ".join(str(v) for v in eagler))

    n = r.u16()
    game = [r.u16() for _ in range(n)]
    named = ", ".join("%d (%s)" % (v, KNOWN.get(v, "unknown")) for v in game)
    print("  MINECRAFT protocol: %s" % named)

    print("  client brand      : %s" % r.string())
    print("  client version    : %s" % r.string())
    print("  password required : %s" % bool(r.u8()))
    print("  username          : %s" % r.string())

    print()
    if 758 in game:
        print("  OK - the client announces 1.18.2 (758).")
    elif 340 in game:
        print("  WRONG - still announcing 340 (1.12.2). The handshake fix is not in this build.")
    else:
        print("  UNEXPECTED - announced %s, expected 758." % game)


def main():
    port = int(sys.argv[1]) if len(sys.argv) > 1 else 8099
    srv = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
    srv.setsockopt(socket.SOL_SOCKET, socket.SO_REUSEADDR, 1)
    srv.bind(("127.0.0.1", port))
    srv.listen(4)
    print("listening on ws://localhost:%d - add that as a server in the client" % port)
    print("(this never replies, so the client will report a failed connection; that is fine)")
    print()

    while True:
        conn, addr = srv.accept()
        try:
            conn.settimeout(15)
            request = read_http_request(conn)
            if request is None:
                continue
            if b"upgrade: websocket" not in request.lower():
                # The client pings servers with a plain HTTP request too; ignore those.
                conn.sendall(b"HTTP/1.1 404 Not Found\r\n\r\n")
                continue
            if not websocket_accept(conn, request):
                continue
            payload = read_frame(conn)
            if not payload:
                print("connection from %s: upgraded, but sent no data" % (addr,))
                continue
            print("handshake from %s, %d bytes:" % (addr, len(payload)))
            try:
                describe(payload)
            except Exception as ex:
                print("  could not decode: %r" % (ex,))
                print("  raw: %s" % payload[:64].hex())
            print()
        except socket.timeout:
            pass
        except Exception as ex:
            print("connection error: %r" % (ex,))
        finally:
            try:
                conn.close()
            except Exception:
                pass


if __name__ == "__main__":
    main()
