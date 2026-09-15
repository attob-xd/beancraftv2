"""
glTF-binary (.glb) -> EaglercraftX .mdl converter.

Handles what obj2mdl cannot: the node hierarchy (glTF nests transforms, and
Sketchfab exports in particular bury a 0.01 scale under a 100 scale with axis
swaps), and multiple primitives that each use a different texture. Those are
merged into one mesh against a single atlas, because a HighPolySkin binds
exactly one texture.
"""
import json, struct

COMPONENT = {5120: ("b", 1), 5121: ("B", 1), 5122: ("h", 2),
             5123: ("H", 2), 5125: ("I", 4), 5126: ("f", 4)}
NCOMP = {"SCALAR": 1, "VEC2": 2, "VEC3": 3, "VEC4": 4, "MAT4": 16}


def load_glb(path):
    d = open(path, "rb").read()
    off, chunks = 12, {}
    while off < len(d):
        clen = struct.unpack("<I", d[off:off + 4])[0]
        name = d[off + 4:off + 8].decode("ascii", "replace").strip("\x00")
        chunks[name] = d[off + 8:off + 8 + clen]
        off += 8 + clen
        off += (-off) % 4
    return json.loads(chunks["JSON"].decode("utf-8")), chunks.get("BIN", b"")


def read_accessor(g, bin_, index):
    a = g["accessors"][index]
    fmt, size = COMPONENT[a["componentType"]]
    n = NCOMP[a["type"]]
    bv = g["bufferViews"][a["bufferView"]]
    base = bv.get("byteOffset", 0) + a.get("byteOffset", 0)
    stride = bv.get("byteStride") or (size * n)
    out = []
    for i in range(a["count"]):
        o = base + i * stride
        out.append(struct.unpack_from("<" + fmt * n, bin_, o))
    return out


def mat_mul(a, b):
    """Column-major 4x4 multiply, as glTF stores them."""
    r = [0.0] * 16
    for c in range(4):
        for row in range(4):
            r[c * 4 + row] = sum(a[k * 4 + row] * b[c * 4 + k] for k in range(4))
    return r


def node_matrix(n):
    if "matrix" in n:
        return list(n["matrix"])
    m = [1,0,0,0, 0,1,0,0, 0,0,1,0, 0,0,0,1]
    if "scale" in n:
        s = n["scale"]
        m = mat_mul(m, [s[0],0,0,0, 0,s[1],0,0, 0,0,s[2],0, 0,0,0,1])
    if "translation" in n:
        t = n["translation"]
        m[12], m[13], m[14] = t
    return m


def apply(m, v):
    x, y, z = v
    return (m[0]*x + m[4]*y + m[8]*z + m[12],
            m[1]*x + m[5]*y + m[9]*z + m[13],
            m[2]*x + m[6]*y + m[10]*z + m[14])


def apply_dir(m, v):
    x, y, z = v
    return (m[0]*x + m[4]*y + m[8]*z,
            m[1]*x + m[5]*y + m[9]*z,
            m[2]*x + m[6]*y + m[10]*z)


def collect(path):
    """Walk the scene graph and return [(primitive, world_matrix), ...]."""
    g, bin_ = load_glb(path)
    prims = []

    def walk(idx, parent):
        n = g["nodes"][idx]
        m = mat_mul(parent, node_matrix(n))
        if "mesh" in n:
            for prim in g["meshes"][n["mesh"]]["primitives"]:
                prims.append((prim, m))
        for c in n.get("children", []):
            walk(c, m)

    ident = [1,0,0,0, 0,1,0,0, 0,0,1,0, 0,0,0,1]
    for root in g["scenes"][g.get("scene", 0)]["nodes"]:
        walk(root, ident)
    return g, bin_, prims
