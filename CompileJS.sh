#!/bin/sh
# Every trim derives its list from the source tree, so they have to run before the build or
# they go stale the moment a class is added to src/game/java or src/main/java/org/lwjgl - and
# a stale trim fails silently, by leaving the jar's copy of a class to win over this
# project's. That has cost real time twice (net.minecraft.client.main.Main first, then the
# whole of LWJGL), so it is no longer something to remember to run.
set -e
sh tools/trim_vanilla.sh
sh tools/trim_netty.sh
sh tools/trim_teavm_jso.sh
sh tools/trim_lwjgl.sh
sh tools/trim_lib.sh com/mojang/authlib/yggdrasil authlib
sh tools/trim_lib.sh com/google/gson gson
# Charsets: commons-io builds a map with Collections.unmodifiableSortedMap, and TeaVM has no
# sorted or navigable unmodifiable views at all - so merely touching the class threw, and the
# crash landed in GlslPreprocessor because ShaderInstance reads shaders through
# IOUtils.toString. See src/main/java/org/apache/commons/io/Charsets.java.
# This also drops the jar's IOSupplier, which this project had already replaced with a strict
# superset of it while relying on classpath order to win.
sh tools/trim_lib.sh org/apache/commons/io commons-io
chmod +x gradlew

# TeaVM's compiler overflows its stack on this project, and nothing was giving it one.
#
# outOfProcess=true means the compiler does not run in the Gradle daemon: the plugin calls
# BuildDaemon.start(), which forks
#     <java.home>/bin/java -cp <daemonClasspath> -XX:+HeapDumpOnOutOfMemoryError -Xmx<processMemory>m
# and that is the whole command. There is no -Xss in it, so the compiler has run on the JVM
# default stack (1 MB here) for every build in this project's history. The -Xss1g in
# gradle.properties only ever applied to Gradle itself; the note there claiming the forked
# process inherits it was wrong, which is why raising it kept appearing to help and then not
# helping.
#
# The failure is a bare "BuildException: java.lang.StackOverflowError" from
# generateJavaScript, AFTER 25 minutes, with classes.js left untouched - so it looks exactly
# like a successful diagnostics-only build unless you check the timestamp. It has cost five
# builds (142, 152, 156, 160, 166). It fires when a change makes more code reachable, because
# the recursive passes walk the reachable set: build 166 only deleted a shadowed field, but
# that field was dead, so deleting it pulled the entire server-side packet path in.
#
# JAVA_TOOL_OPTIONS is the only lever - the fork inherits the environment but takes no
# configurable JVM arguments. It is a reservation per thread, not an allocation.
export JAVA_TOOL_OPTIONS="-Xss512m"

# The Gradle daemon passes ITS OWN environment to the fork, so a daemon started before the
# line above existed would go on spawning a 1 MB-stack compiler and the setting would appear
# to do nothing. Stopping it costs a few seconds against a 25-minute build.
./gradlew --stop >/dev/null 2>&1 || true
# --info because TeaVM runs out of process: without it the error list never reaches the
# gradle log and a failed build says only "Errors occurred during TeaVM build".
#
# The grep keeps the log readable - there are ~150 "was not found" diagnostics and they
# matter - but it used to drop the reason a build FAILED along with everything else.
# javac's own errors are lowercase "error:" and were not matched either, so a build that
# failed compilation in 12 seconds printed nothing but "Compilation failed; see the
# compiler error output for details" - with the output it is telling you to see filtered
# out. That cost a cycle. Matched now as javac spells it exactly - "File.java:123: error:" -
# rather than a bare "error:" or ".java:", which also caught the ordinary stack-trace lines
# inside TeaVM's own diagnostics and buried the signal again.
# Two builds died on "BuildException: java.lang.StackOverflowError" showing only
# "> Task :generateJavaScript FAILED", and a third died before the JVM even started,
# showing nothing at all. Gradle's "What went wrong" block is kept now.
./gradlew generateJavascript --info 2>&1 \
    | grep -E "was not found|^> Task |BUILD |FAILED|What went wrong|^> |Caused by:|Invalid|Error:|\.java:[0-9]+: error:" || true
# The build can report zero errors and still emit a page that will not load - see the header
# of tools/check_dangling.py. This is cheap and decisive, so it runs before anyone opens a
# browser.
python tools/check_dangling.py javascript/classes.build.js
# TeaVM emits its runtime from inside its own jar, so this one-line correction has no
# source file to live in. See the header of tools/patch_runtime.py - without it, an
# integrated-server crash can kill the worker while printing nothing at all.
python tools/patch_runtime.py javascript/classes.build.js
# Only now does the browser get to see it. Both checks above have passed and the file is
# complete, so this rename is the single instant at which the served build changes - a reload
# during a compile keeps serving the previous working build instead of half of the next one.
mv -f javascript/classes.build.js javascript/classes.js
