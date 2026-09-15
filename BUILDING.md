# Building beancraftv2

This repository holds the **source** of an EaglercraftX 1.18.2 fork. It deliberately does
not contain Minecraft itself.

You must supply these yourself before the build will run:

| Path | What to put there |
|---|---|
| `libs-1.18.2/` | the Minecraft 1.18.2 client jar and its libraries |
| `desktopRuntime/resources/` | the Minecraft assets tree |
| `decompiled-1.18.2-reference/` | optional: a decompile, used only as a reading reference |

Then:

    sh CompileEPK.sh     # packs desktopRuntime/resources into javascript/assets.epk
    sh CompileJS.sh      # TeaVM build -> javascript/classes.js  (~25 minutes)
    python tools/serve.py

and open http://localhost:8088.

## Why Minecraft is not in this repository

Minecraft's code and assets are Mojang's and are not redistributable - the game's own title
screen says "Copyright Mojang AB. Do not distribute!". Only the fork's own source is here.

## Build notes

- `CompileJS.sh` exports `JAVA_TOOL_OPTIONS=-Xss512m`. TeaVM's compiler is forked with no
  `-Xss` of its own and overflows the default 1 MB stack on a project this size.
- The build emits `classes.build.js` and renames it onto `classes.js` only after the
  post-build checks pass, so a reload mid-build never serves a half-written script.
- `BUILD FAILED` with "Errors occurred during TeaVM build" is the normal diagnostics-only
  state. Check that `classes.js` has a new timestamp before concluding anything.
