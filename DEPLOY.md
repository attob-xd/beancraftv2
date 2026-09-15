# Publishing a playable build to GitHub Pages

Pages serves the repository root, so the page needs these files **at the root** next to
`index.html`:

| File | Size | Where it comes from |
|---|---|---|
| `classes.js` | ~64 MB | `javascript/classes.js` after `sh CompileJS.sh` |
| `assets.epk` | ~21 MB | `javascript/assets.epk` after `sh CompileEPK.sh` |
| `favicon.png` | 4 KB | `javascript/favicon.png` |
| `lang/en_us.lang` | 192 KB | `javascript/lang/` |

`index.html` and `.nojekyll` are already here. Copy the four above in, then commit and push:

    git add -f classes.js assets.epk favicon.png lang
    git commit -m "publish build"
    git push

They are force-added because `.gitignore` excludes build output by default.

## Read this before you do it

`classes.js` is Minecraft compiled to JavaScript, `assets.epk` is Minecraft's asset tree and
`lang/en_us.lang` is Minecraft's translation strings. All three are Mojang's, and the game's
own title screen says "Copyright Mojang AB. Do not distribute!". Publishing them to a public
site redistributes Mojang's code and assets under your account. That is your decision to make.

GitHub Pages also has a **100 MB per-file limit** and warns above 50 MB; `classes.js` at ~64 MB
is over the warning threshold and a repository carrying it will be large and slow to clone.
