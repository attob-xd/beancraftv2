"""Makes TeaVM's $rt_exception survive a throw that is not a Java Throwable.

TeaVM's generated runtime assumes everything reaching $rt_throw is a Throwable, so it reads
a cause field off it:

    function $rt_exception(ex) {
        var err = ex.$jsException;
        if (!err) {
            var javaCause = $rt_throwableCause(ex);
            var jsCause = javaCause !== null ? javaCause.$jsException : undefined;
            ...

A raw JavaScript error can reach that path - a @JSBody script that throws synchronously
inside a green-thread continuation is the way it happens here - and then
$rt_throwableCause returns undefined rather than null. The `!== null` test passes,
undefined.$jsException throws a TypeError, and the runtime dies *while reporting the
original error*. In the integrated server worker that is fatal and silent: the worker exits
with "Cannot read properties of undefined (reading '$jsException')" and the actual
exception - the one worth reading - is never printed.

Normalising undefined to null costs one comparison, loses nothing, and turns those into an
ordinary crash report. This runs after every build because the runtime is emitted from
teavm-core's own runtime.js inside the jar, so there is no source file here to fix.
"""
import io
import sys

MARKER = '/*EAGLER_RT_PATCHED*/'
OLD = ('var err=ex.$jsException;if(!err){var javaCause=$rt_throwableCause(ex);'
       'var jsCause=javaCause!==null?javaCause.$jsException:$rt_globals.undefined;')
NEW = ('var err=ex.$jsException;if(!err){' + MARKER + 'var javaCause=$rt_throwableCause(ex);'
       'if(javaCause===undefined){javaCause=null;}'
       'var jsCause=javaCause!==null?javaCause.$jsException:$rt_globals.undefined;')


def main(path):
    s = io.open(path, encoding='utf-8', newline='').read()
    if MARKER in s:
        print('patch_runtime: already patched')
        return 0
    n = s.count(OLD)
    if n != 1:
        # Loud, not fatal: the build output is still usable, but TeaVM's runtime has changed
        # shape and this needs looking at rather than silently doing nothing.
        print('patch_runtime: FAILED - found %d matches, expected 1' % n)
        print('patch_runtime: $rt_exception was NOT patched, worker crashes may be swallowed')
        return 0
    io.open(path, 'w', encoding='utf-8', newline='').write(s.replace(OLD, NEW, 1))
    print('patch_runtime: $rt_exception now tolerates a non-Throwable throw')
    return 0


if __name__ == '__main__':
    sys.exit(main(sys.argv[1]))
