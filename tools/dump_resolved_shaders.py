"""Resolve every vanilla shader's #moj_import and dump the results as JSON.

    python tools/dump_resolved_shaders.py [out.json]

Written so the GLSL-ES translation can be tested against a real WebGL 2 context without a
20-minute TeaVM build in the loop. The output is {name: source} for every .vsh and .fsh under
assets/minecraft/shaders, with #moj_import expanded the way GlslPreprocessor expands it, so a
browser can be handed the whole set and asked to compile it.

The import handling here is deliberately the simple case rather than a reimplementation of
GlslPreprocessor: vanilla's shaders only ever import from shaders/include, never conditionally
and never from inside a comment, so a direct textual substitution produces the same text. It
is a test fixture, not part of the build.

It writes into javascript/ because that is the directory tools/serve.py serves, so the page
can fetch it - which means the output is sitting next to classes.js and assets.epk. **Delete
it when the test is done**; it takes a second to regenerate and has no business shipping.

How it gets used, for the next time a shader question comes up: serve javascript/, open any
page from it, and in the browser console fetch this JSON, run each source through the
candidate translation, and hand it to a real WebGL 2 context with gl.compileShader. That
answers "will these shaders compile" in about a minute instead of a 20-minute TeaVM build, and
it is how the GLSL ES translation in GlStateManager.glShaderSource was settled: 0 of 147
compiled untranslated, 147 of 147 after, and all 62 vertex/fragment pairs linked.
"""
import json
import os
import re
import sys

SHADERS = os.path.join('desktopRuntime', 'resources', 'assets', 'minecraft', 'shaders')
IMPORT_RE = re.compile(r'^[ \t]*#[ \t]*moj_import[ \t]*[<"]([^>"]+)[>"][ \t]*$', re.M)


def resolve(source, seen, depth=0):
    if depth > 10:
        raise RuntimeError('import cycle')

    def sub(match):
        name = match.group(1)
        # "minecraft:include/fog.glsl" and "fog.glsl" both appear; take the last segment.
        leaf = name.split(':')[-1]
        if not leaf.startswith('include/'):
            leaf = 'include/' + os.path.basename(leaf)
        path = os.path.join(SHADERS, *leaf.split('/'))
        if not os.path.isfile(path):
            raise RuntimeError('missing import %s (looked in %s)' % (name, path))
        seen.add(leaf)
        with open(path, encoding='utf8') as fh:
            text = fh.read()
        # An imported file carries its own #version, which must not survive the splice.
        text = re.sub(r'^[ \t]*#[ \t]*version[^\n]*\n', '', text, count=1)
        return resolve(text, seen, depth + 1)

    return IMPORT_RE.sub(sub, source)


def main(out_path):
    shaders = {}
    imports_used = set()
    for dirpath, _, filenames in os.walk(SHADERS):
        for name in sorted(filenames):
            if not (name.endswith('.vsh') or name.endswith('.fsh')):
                continue
            path = os.path.join(dirpath, name)
            rel = os.path.relpath(path, SHADERS).replace(os.sep, '/')
            with open(path, encoding='utf8') as fh:
                source = fh.read()
            shaders[rel] = resolve(source, imports_used)
    with open(out_path, 'w', encoding='utf8') as fh:
        json.dump(shaders, fh)
    print('wrote %d shaders to %s' % (len(shaders), out_path))
    print('imports resolved: %s' % ', '.join(sorted(imports_used)))
    remaining = sum(1 for s in shaders.values() if 'moj_import' in s)
    print('sources still containing moj_import: %d' % remaining)
    return 1 if remaining else 0


if __name__ == '__main__':
    sys.exit(main(sys.argv[1] if len(sys.argv) > 1 else 'javascript/_resolved_shaders.json'))
