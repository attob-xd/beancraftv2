import java.lang.reflect.Method;
import java.nio.ByteBuffer;

/**
 * Checks {@code org.lwjgl.system.MemoryUtil.memRealloc} against the contract its callers rely
 * on, and reproduces the failure it caused.
 *
 * <p>The bug was not in the copying - the bytes were always right - but in the returned
 * buffer's limit, which was carried over from the smaller buffer. Nothing notices until
 * something writes past the old end, because {@code BufferBuilder.ensureCapacity} asks for
 * {@code capacity()} and {@code putShort} bounds-checks {@code limit()}. So this asserts the
 * three things a caller needs (bytes preserved, capacity as requested, limit at capacity) and
 * then replays the BufferBuilder sequence that actually threw.
 */
public class VerifyMemRealloc {

	private static int checks;
	private static int failures;

	public static void main(String[] args) throws Exception {
		Class<?> cls = Class.forName("org.lwjgl.system.MemoryUtil");
		Method memAlloc = cls.getMethod("memAlloc", int.class);
		Method memRealloc = cls.getMethod("memRealloc", ByteBuffer.class, int.class);

		int[][] sizes = { {1024, 1536}, {1536, 2098688}, {16, 32}, {1, 4096}, {1024, 1024} };

		for (int[] pair : sizes) {
			int from = pair[0];
			int to = pair[1];

			ByteBuffer old = (ByteBuffer) memAlloc.invoke(null, from);
			for (int i = 0; i < from; i++) {
				old.put(i, (byte) ((i * 31 + 7) & 0xFF));
			}
			// The state BufferBuilder leaves a full buffer in: written to the end.
			old.position(from);
			old.limit(from);

			ByteBuffer next = (ByteBuffer) memRealloc.invoke(null, old, to);

			check("capacity " + from + "->" + to, next.capacity() == to,
					"capacity is " + next.capacity());
			check("limit " + from + "->" + to, next.limit() == to,
					"limit is " + next.limit() + ", expected " + to);
			check("position " + from + "->" + to, next.position() == 0,
					"position is " + next.position());

			boolean bytesOk = true;
			int keep = Math.min(from, to);
			for (int i = 0; i < keep; i++) {
				if (next.get(i) != (byte) ((i * 31 + 7) & 0xFF)) {
					bytesOk = false;
					break;
				}
			}
			check("bytes preserved " + from + "->" + to, bytesOk, "content differs");
		}

		// The exact sequence that threw: a buffer full at 1536, grown the way
		// BufferBuilder.ensureCapacity grows it, then written at the old end.
		ByteBuffer buf = (ByteBuffer) memAlloc.invoke(null, 1536);
		buf.position(1536);
		buf.limit(1536);
		ByteBuffer grown = (ByteBuffer) memRealloc.invoke(null, buf, 2098688);
		grown.rewind();                       // what ensureCapacity does, and all it does
		String outcome;
		try {
			grown.putShort(1536, (short) 0x1234);
			outcome = grown.getShort(1536) == 0x1234 ? "wrote and read back" : "wrote the wrong value";
		} catch (RuntimeException ex) {
			outcome = "THREW " + ex.getClass().getSimpleName() + ": " + ex.getMessage();
		}
		check("write at the old end after growing", outcome.equals("wrote and read back"), outcome);

		// Negative control: the old behaviour must fail this same write, or the replay above
		// is not actually exercising the bug and proves nothing.
		ByteBuffer manual = ByteBuffer.allocateDirect(2098688);
		manual.position(0);
		manual.limit(1536);                   // what the buggy version produced
		String control;
		try {
			manual.putShort(1536, (short) 0x1234);
			control = "accepted the write - CONTROL FAILED, the replay proves nothing";
		} catch (RuntimeException ex) {
			control = "rejected with " + ex.getClass().getSimpleName() + " - control ok";
		}
		System.out.println("negative control (old limit kept): " + control);

		System.out.println(checks + " checks, " + failures + " failures");
		if (failures > 0) {
			System.exit(1);
		}
	}

	private static void check(String what, boolean ok, String detail) {
		++checks;
		if (!ok) {
			++failures;
			System.out.println("  FAIL " + what + ": " + detail);
		}
	}
}
