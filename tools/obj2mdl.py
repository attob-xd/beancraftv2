"""
OBJ -> EaglercraftX .mdl converter.

Format (see EaglerMeshLoader.java):
  magic  : "!EAG$mdl" (8 bytes)
  type   : 'T' textured / 'C' colour-only (1 byte)
  meta   : uint16 big-endian length, then that many bytes (skipped by the loader)
  counts : int32 big-endian vertexCount, int32 big-endian indexCount
  payload: little-endian raw GL data, uploaded straight to the GPU
             vertex stride 24 = vec3 position (12) + 4 signed bytes normal (4) + vec2 uv (8)
             indices are uint16, padded to an even count

Note the mixed endianness: the header uses DataInputStream (big-endian) while the
payload is assembled byte-by-byte into little-endian ints.
"""
import struct, sys, os

MAGIC = bytes([33, 69, 65, 71, 36, 109, 100, 108])  # !EAG$mdl


def load_obj(path):
    pos, uv, norm, faces = [], [], [], []
    for line in open(path, "r", encoding="utf-8", errors="replace"):
        p = line.split()
        if not p:
            continue
        if p[0] == "v":
            pos.append((float(p[1]), float(p[2]), float(p[3])))
        elif p[0] == "vt":
            uv.append((float(p[1]), float(p[2])))
        elif p[0] == "vn":
            norm.append((float(p[1]), float(p[2]), float(p[3])))
        elif p[0] == "f":
            idx = []
            for tok in p[1:]:
                a = tok.split("/")
                vi = int(a[0]) - 1
                ti = int(a[1]) - 1 if len(a) > 1 and a[1] else vi
                ni = int(a[2]) - 1 if len(a) > 2 and a[2] else vi
                idx.append((vi, ti, ni))
            # fan-triangulate anything with more than 3 corners
            for k in range(1, len(idx) - 1):
                faces.append((idx[0], idx[k], idx[k + 1]))
    return pos, uv, norm, faces


def convert(src, dst, scale=1.0, offset=(0.0, 0.0, 0.0), flip_v=True):
    pos, uv, norm, faces = load_obj(src)
    verts, lookup, indices = [], {}, []
    for tri in faces:
        for key in tri:
            i = lookup.get(key)
            if i is None:
                i = len(verts)
                if i > 65535:
                    raise SystemExit("%s exceeds the 65535 vertex limit" % src)
                lookup[key] = i
                verts.append(key)
            indices.append(i)

    out = bytearray()
    out += MAGIC
    out += b"T"
    out += struct.pack(">H", 0)                 # no metadata
    out += struct.pack(">i", len(verts))
    out += struct.pack(">i", len(indices))

    for (vi, ti, ni) in verts:
        x, y, z = pos[vi]
        out += struct.pack("<fff", x * scale + offset[0], y * scale + offset[1], z * scale + offset[2])
        nx, ny, nz = norm[ni] if ni < len(norm) else (0.0, 1.0, 0.0)
        # normals as signed bytes, GL_BYTE normalised
        out += struct.pack("<bbbb", max(-127, min(127, int(nx * 127))),
                           max(-127, min(127, int(ny * 127))),
                           max(-127, min(127, int(nz * 127))), 0)
        u, v = uv[ti] if ti < len(uv) else (0.0, 0.0)
        out += struct.pack("<ff", u, (1.0 - v) if flip_v else v)

    for i in indices:
        out += struct.pack("<H", i)
    if len(indices) % 2 != 0:
        out += struct.pack("<H", 0)             # pad to a whole int

    open(dst, "wb").write(out)
    return len(verts), len(indices), len(out)


if __name__ == "__main__":
    src, dst = sys.argv[1], sys.argv[2]
    scale = float(sys.argv[3]) if len(sys.argv) > 3 else 1.0
    oy = float(sys.argv[4]) if len(sys.argv) > 4 else 0.0
    v, i, b = convert(src, dst, scale, (0.0, oy, 0.0))
    print("%-22s %5d verts %5d idx %7d bytes" % (os.path.basename(dst), v, i, b))
