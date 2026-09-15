"""Carry EaglercraftX's translation keys from the 1.12.2 fork into 1.18.2's en_us.json.

    python tools/merge_eagler_lang.py [path/to/1.12.2 en_US.lang]

Then rebuild the EPK (sh CompileEPK.sh) - the client reads en_us.json out of assets.epk, not
off disk, so an edit that skips that step changes nothing in the browser.

Why this exists: the profile editor, the cape editor, the default-username note and the main
menu all ask I18n for keys such as editProfile.title and menu.editProfile. Those were never
Mojang keys; lax1dude's fork carried them in its own en_US.lang, which the 1.12.2 build
shipped in place of vanilla's. 1.18.2's en_us.json is vanilla's, so every one of those
screens would render its raw key.

Two things about how the 1.12.2 fork stored them matter here:

  - Its keys are written with an "eaglercraft." prefix (eaglercraft.editProfile.title), and
    its Locale loader synthesised the unprefixed alias as it read the file - the screens ask
    for the short form. 1.18.2's Language does no such thing, so both spellings are written.
  - The 1.12.2 file is the *whole* language, ~3,000 lines of which most are 1.12-era vanilla
    keys that 1.18.2 renamed or dropped. Copying it wholesale would quietly reintroduce old
    vanilla wording, so it does not: only the eaglercraft.* keys, plus any other key this
    source tree actually names in an I18n / TranslatableComponent literal and 1.18.2 lacks.

Vanilla keys already present are never overwritten. The script is idempotent - a second run
changes nothing - and it lists every key the source references that is still unresolved
afterwards, so a missing translation shows up here rather than as raw text on a screen.
"""
import io
import json
import os
import re
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.abspath(os.path.join(HERE, os.pardir))
DEFAULT_LANG = os.path.join(ROOT, os.pardir, 'eagle 1.12.2', 'desktopRuntime', 'resources',
                            'assets', 'minecraft', 'lang', 'en_US.lang')
JSON_PATH = os.path.join(ROOT, 'desktopRuntime', 'resources', 'assets', 'minecraft', 'lang',
                         'en_us.json')
SOURCE_DIRS = [os.path.join(ROOT, 'src', 'main', 'java'),
               os.path.join(ROOT, 'src', 'game', 'java')]
PREFIX = 'eaglercraft.'

# A translation key as it appears at a call site. Only matches a plain string literal, which
# is the point: a key built at runtime cannot be checked from here anyway.
KEY_LITERAL = re.compile(
    r'(?:I18n\.(?:get|format|hasKey|exists)|TranslatableComponent|translatable)'
    r'\s*\(\s*"([A-Za-z0-9_.-]+)"')


def read_lang(path):
    """The 1.12.2 .lang format: key=value per line, # for comments."""
    out = {}
    with io.open(path, encoding='utf-8') as f:
        for line in f:
            line = line.rstrip('\r\n')
            if not line or line.startswith('#') or '=' not in line:
                continue
            k, v = line.split('=', 1)
            out[k.strip()] = v
    return out


def referenced_keys():
    keys = set()
    for d in SOURCE_DIRS:
        for dirpath, _, files in os.walk(d):
            for name in files:
                if not name.endswith('.java'):
                    continue
                path = os.path.join(dirpath, name)
                with io.open(path, encoding='utf-8', errors='replace') as f:
                    for m in KEY_LITERAL.finditer(f.read()):
                        keys.add(m.group(1))
    return keys


def main():
    lang_path = sys.argv[1] if len(sys.argv) > 1 else DEFAULT_LANG
    if not os.path.isfile(lang_path):
        print('no such lang file: ' + lang_path)
        return 1
    lang = read_lang(lang_path)
    with io.open(JSON_PATH, encoding='utf-8') as f:
        vanilla = json.load(f)
    wanted = referenced_keys()

    added = {}

    def add(k, v):
        if k not in vanilla and k not in added:
            added[k] = v

    for k, v in lang.items():
        if k.startswith(PREFIX):
            add(k, v)
            add(k[len(PREFIX):], v)
        elif k in wanted:
            add(k, v)

    merged = dict(vanilla)
    merged.update(added)
    with io.open(JSON_PATH, 'w', encoding='utf-8', newline='\n') as f:
        json.dump(merged, f, ensure_ascii=False, indent=2, sort_keys=True)
        f.write('\n')

    print('lang file:  %s (%d keys)' % (lang_path, len(lang)))
    print('en_us.json: %d keys before, %d added, %d after'
          % (len(vanilla), len(added), len(merged)))
    unresolved = sorted(k for k in wanted if k not in merged)
    if unresolved:
        print('%d referenced key(s) still have no translation:' % len(unresolved))
        for k in unresolved:
            print('   ' + k)
    else:
        print('every key the source references resolves')
    print()
    print('Rebuild the EPK now: sh CompileEPK.sh')
    return 0


if __name__ == '__main__':
    sys.exit(main())
