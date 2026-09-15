"""List every class this project supplies as source that something else on TeaVM's classpath
also carries.

    python tools/find_shadowed.py            # asks gradle for the real classpath
    python tools/find_shadowed.py --offline  # falls back to scanning the jar directories

The whole port rests on one rule: where a class exists both as source here and as a class on
the classpath, the source must win. javac is made to obey it with -Xprefer:source. TeaVM is
not - it reads the classpath, a jar can win regardless of order, and when it does the failure
is silent. The jar's version is compiled into the page and this project's is simply absent.
That has now cost real time four times: net.minecraft.client.main.Main, the whole of LWJGL, and
commons-io's Charsets twice over.

**Ask the build, do not guess.** The Charsets case is why this runs gradle rather than
listing directories. commons-io reached TeaVM by *two* routes - the browserLibs fileTree over
libs-1.18.2, and a hand-written `teavm 'commons-io:commons-io:2.11.0'` added because
teavm-classlib's transitive dependencies had to be re-declared when the module was excluded.
Fixing only the obvious one left the client dying in exactly the same place, from a copy
nothing in libs-1.18.2 was responsible for. An earlier version of this script scanned
libs-1.18.2 and libs-1.18.2-derived and reported the tree as clean, because the offending jar
was in neither. So it now enumerates `generateJavaScript.classpath` itself, which is by
definition the set TeaVM compiles from, and cannot be wrong about a route nobody remembered.

Read the output with judgement rather than trimming everything it prints. A collision is not
automatically a bug:

  - Many are deliberate and harmless because both copies agree. EaglercraftX vendors large
    parts of guava and commons-lang3 as source with the jars still present; that is how the
    fork has always been laid out, and the client boots fine with it.
  - Some MUST NOT be trimmed. tools/trim_lib.sh's header has the example: lax1dude's
    GameProfile shadows authlib's but returns a Guava Multimap where authlib returns
    PropertyMap, so dropping the jar's copy turns every vanilla call into a NoSuchMethodError.

So this answers "what is shadowed, and by which file". A human answers "which of those matter".
The one question it settles outright is the one that keeps costing builds: after replacing a
class, is there still another copy anywhere on the path?
"""
import os
import subprocess
import sys
import tempfile
import zipfile

SOURCE_ROOTS = [
    os.path.join('src', 'main', 'java'),
    os.path.join('src', 'game', 'java'),
    os.path.join('src', 'protocol-game', 'java'),
    os.path.join('src', 'teavm', 'java'),
    os.path.join('src', 'nio-shim', 'java'),
]

# Only used by --offline, and only as a worse approximation; see the module docstring.
OFFLINE_DIRS = ['libs-1.18.2-derived', 'libs-1.18.2']

INIT_SCRIPT = """
allprojects {
    tasks.register('printTeavmClasspath') {
        doLast {
            def t = tasks.findByName('generateJavaScript')
            if (t == null) { println 'NO_TASK'; return }
            t.classpath.files.each { println "CP ${it}" }
        }
    }
}
"""


def classpath_from_gradle():
    """Every file on generateJavaScript.classpath, straight from the build."""
    handle, path = tempfile.mkstemp(suffix='.gradle')
    try:
        with os.fdopen(handle, 'w', encoding='utf8') as fh:
            fh.write(INIT_SCRIPT)
        gradlew = os.path.join('.', 'gradlew.bat' if os.name == 'nt' else 'gradlew')
        if not os.path.exists(gradlew):
            gradlew = './gradlew'
        result = subprocess.run([gradlew, '-q', '-I', path, 'printTeavmClasspath'],
                                capture_output=True, text=True)
        if result.returncode != 0:
            print('gradle failed (%d); rerun with --offline for a rough answer'
                  % result.returncode)
            sys.stderr.write(result.stderr[-2000:])
            return None
        entries = [l[3:].strip() for l in result.stdout.split('\n') if l.startswith('CP ')]
        return entries or None
    finally:
        try:
            os.remove(path)
        except OSError:
            pass


def classpath_offline():
    entries = []
    for directory in OFFLINE_DIRS:
        if not os.path.isdir(directory):
            continue
        for name in sorted(os.listdir(directory)):
            if name.endswith('.jar'):
                entries.append(os.path.join(directory, name))
    return entries


def source_classes():
    """Class paths this project supplies, as classpath-style paths without the extension."""
    out = {}
    for root in SOURCE_ROOTS:
        if not os.path.isdir(root):
            continue
        for dirpath, _, filenames in os.walk(root):
            for name in filenames:
                if not name.endswith('.java') or name == 'package-info.java':
                    continue
                rel = os.path.relpath(os.path.join(dirpath, name), root)
                out.setdefault(rel[:-len('.java')].replace(os.sep, '/'), root)
    return out


def providers(entries, wanted):
    """For each wanted class path, the classpath entries that carry it - excluding our own."""
    found = {}
    ours = os.path.abspath(os.path.join('build', 'classes'))
    for entry in entries:
        if os.path.abspath(entry).startswith(ours):
            continue
        if entry.lower().endswith('.jar') and os.path.isfile(entry):
            try:
                with zipfile.ZipFile(entry) as zf:
                    names = set(zf.namelist())
            except (zipfile.BadZipFile, OSError):
                continue
            label = os.path.basename(entry)
            for cls in wanted:
                if cls + '.class' in names:
                    found.setdefault(cls, []).append(label)
        elif os.path.isdir(entry):
            label = entry
            for cls in wanted:
                if os.path.isfile(os.path.join(entry, *(cls.split('/')))+'.class'):
                    found.setdefault(cls, []).append(label)
    return found


def main(argv):
    offline = '--offline' in argv
    entries = classpath_offline() if offline else classpath_from_gradle()
    if entries is None:
        return 2
    print('classpath entries examined: %d%s'
          % (len(entries), ' (offline approximation)' if offline else ' (from gradle)'))

    sources = source_classes()
    print('source classes: %d' % len(sources))
    shadowed = providers(entries, set(sources))
    print('\nSHADOWED: %d of them are also on the classpath\n' % len(shadowed))

    by_package = {}
    for cls, where in shadowed.items():
        by_package.setdefault(cls.rsplit('/', 1)[0], []).append((cls, where))
    for pkg in sorted(by_package, key=lambda p: (-len(by_package[p]), p)):
        entriez = sorted(by_package[pkg])
        carriers = sorted({w for _, ws in entriez for w in ws})
        print('  %-46s %4d class(es)  <- %s' % (pkg, len(entriez), ', '.join(carriers)))
    print('\nA collision is not automatically a bug - read the header before trimming '
          'anything.')
    return 0


if __name__ == '__main__':
    sys.exit(main(sys.argv[1:]))
