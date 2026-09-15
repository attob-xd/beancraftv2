#!/bin/sh
# Compiles the real TFileChannel against a test-only in-memory VFile2 and runs the
# differential test against the JDK's own FileChannel. See tools/VerifyFileChannel.java for
# why this exists - short version: TFileChannel is the save layer, and a wrong offset there
# reads back plausible bytes rather than throwing, which looks like world corruption and
# points nowhere near the bug.
#
#     sh tools/verify_filechannel.sh
#
# The stub is generated here rather than kept in the source tree, so there is no second
# VFile2 lying around for someone to mistake for a real one. It answers only the three
# questions TFileChannel asks: does this path exist, give me all its bytes, replace them.
set -e
cd "$(dirname "$0")/.."

OUT=$(mktemp -d 2>/dev/null || echo "${TMPDIR:-/tmp}/verify-fc-$$")
mkdir -p "$OUT/src/net/lax1dude/eaglercraft/internal/vfs2" "$OUT/classes"

cat > "$OUT/src/net/lax1dude/eaglercraft/internal/vfs2/VFile2.java" <<'JAVA'
package net.lax1dude.eaglercraft.internal.vfs2;

import java.util.HashMap;
import java.util.Map;

/** Test-only stand-in; see tools/VerifyFileChannel.java. */
public class VFile2 {

	private static final Map<String, byte[]> STORE = new HashMap<>();

	private final String path;

	public VFile2(Object... path) {
		StringBuilder sb = new StringBuilder();
		for (Object p : path) {
			if (sb.length() > 0) {
				sb.append('/');
			}
			sb.append(String.valueOf(p));
		}
		this.path = sb.toString();
	}

	public boolean exists() {
		return STORE.containsKey(path);
	}

	public byte[] getAllBytes() {
		byte[] b = STORE.get(path);
		return b == null ? null : b.clone();
	}

	public void setAllBytes(byte[] bytes) {
		STORE.put(path, bytes.clone());
	}

	public static void reset() {
		STORE.clear();
	}

	public static byte[] peek(String path) {
		byte[] b = STORE.get(path);
		return b == null ? null : b.clone();
	}

	public static boolean present(String path) {
		return STORE.containsKey(path);
	}
}
JAVA

SHIM=src/nio-shim/java/org/teavm/classlib/java/nio/channels
javac -nowarn -d "$OUT/classes" \
	"$OUT/src/net/lax1dude/eaglercraft/internal/vfs2/VFile2.java" \
	"$SHIM/TFileChannel.java" \
	"$SHIM/TFileLock.java"

java -cp "$OUT/classes" tools/VerifyFileChannel.java
STATUS=$?
rm -rf "$OUT"
exit $STATUS
