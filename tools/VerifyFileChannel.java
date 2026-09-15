/**
 * Differential test: the VFile2-backed TFileChannel in src/nio-shim, against the JDK's real
 * java.nio.channels.FileChannel on a real temp file.
 *
 *     java -cp "<dir with TFileChannel + stub VFile2>" tools/VerifyFileChannel.java
 *
 * (tools/verify_filechannel.sh compiles both and runs this.)
 *
 * Why it exists. TFileChannel is the save layer: 1.18.2 writes every chunk through RegionFile,
 * which is positional reads and writes into a .mca file, and takes DirectoryLock on
 * session.lock before touching a world at all. Nothing has exercised it yet - nothing has
 * reached a world load - and the failure mode if it is wrong is not an exception. It is a
 * region file that reads back plausible bytes from the wrong offset, which looks exactly like
 * world corruption and points nowhere near here. That is the same reason SimpleBitStorage got
 * a differential test rather than a careful read.
 *
 * So every operation runs twice, once against a real FileChannel on a real file and once
 * against TFileChannel on an in-memory VFile2, and both the return values and the resulting
 * file contents are compared after each step. The sequence is deliberately shaped like what
 * RegionFile does: a header written at offset 0, sectors written at 4096-byte boundaries,
 * positional reads of a few bytes, a hole left past the end, reopen, and force().
 *
 * Locking is tested on its own rather than against the JDK. The JDK's FileLock is a real
 * OS-level lock across processes; TFileChannel's is a set in one page, which is what the class
 * comment says it is. Comparing them would only assert that two different things are
 * different, so what is checked here is the contract DirectoryLock actually relies on: a
 * second tryLock on a locked path returns null, and releasing makes it available again.
 */
import java.io.IOException;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.Arrays;
import java.util.Random;

public class VerifyFileChannel {

	static int checks;
	static int failures;

	static Class<?> TFC;
	static Class<?> STUB;

	public static void main(String[] args) throws Exception {
		TFC = Class.forName("org.teavm.classlib.java.nio.channels.TFileChannel");
		STUB = Class.forName("net.lax1dude.eaglercraft.internal.vfs2.VFile2");

		Path tmp = Files.createTempDirectory("verify-fc");
		try {
			regionFileShapedSequence(tmp);
			holesAndTruncation(tmp);
			openModes(tmp);
			persistenceAcrossReopen(tmp);
			locking();
		} finally {
			deleteTree(tmp);
		}

		System.out.println(checks + " checks, " + failures + " failures");
		if (failures > 0) {
			System.exit(1);
		}
		System.out.println("OK - TFileChannel agrees with the JDK on every operation");
	}

	// ------------------------------------------------------------------ the sequences

	/** Shaped like RegionFile: 8 KB header, sector writes at 4096 boundaries, short reads. */
	static void regionFileShapedSequence(Path dir) throws Exception {
		Path real = dir.resolve("region.mca");
		String virt = "region.mca";
		reset(virt);

		FileChannel a = FileChannel.open(real, StandardOpenOption.CREATE,
				StandardOpenOption.READ, StandardOpenOption.WRITE);
		Object b = open(virt, "CREATE", "READ", "WRITE", "DSYNC");

		Random rnd = new Random(9090);

		byte[] header = new byte[8192];
		rnd.nextBytes(header);
		eq("header write", a.write(ByteBuffer.wrap(header), 0L), write(b, header, 0L));
		sameBytes("after header", a, b, real, virt);

		for (int sector = 2; sector < 10; ++sector) {
			byte[] chunk = new byte[rnd.nextInt(3000) + 100];
			rnd.nextBytes(chunk);
			long at = (long) sector * 4096L;
			eq("sector " + sector + " write", a.write(ByteBuffer.wrap(chunk), at),
					write(b, chunk, at));
			sameBytes("after sector " + sector, a, b, real, virt);
		}

		// positional reads of the 5-byte chunk headers RegionFile reads
		for (int sector = 0; sector < 12; ++sector) {
			long at = (long) sector * 4096L;
			ByteBuffer ba = ByteBuffer.allocate(5);
			byte[] bb = new byte[5];
			int ra = a.read(ba, at);
			int rb = read(b, bb, 0, 5, at);
			eq("read5 @" + at, ra, rb);
			if (ra > 0) {
				byte[] want = Arrays.copyOf(ba.array(), ra);
				byte[] got = Arrays.copyOf(bb, rb);
				sameArray("read5 bytes @" + at, want, got);
			}
		}

		// a read that spans past the end
		ByteBuffer big = ByteBuffer.allocate(9000);
		byte[] bigB = new byte[9000];
		long near = size(b) - 100;
		eq("short read at eof", a.read(big, near), read(b, bigB, 0, 9000, near));
		eq("read past eof", a.read(ByteBuffer.allocate(16), size(b) + 4096),
				read(b, new byte[16], 0, 16, size(b) + 4096));

		eq("size", a.size(), size(b));
		force(b);
		a.force(true);
		sameBytes("after force", a, b, real, virt);
		a.close();
		close(b);
		sameFileBytes("after close", real, virt);
	}

	/** A write past the end must leave zeroes, and truncate must shorten. */
	static void holesAndTruncation(Path dir) throws Exception {
		Path real = dir.resolve("holes.bin");
		String virt = "holes.bin";
		reset(virt);
		FileChannel a = FileChannel.open(real, StandardOpenOption.CREATE,
				StandardOpenOption.READ, StandardOpenOption.WRITE);
		Object b = open(virt, "CREATE", "READ", "WRITE");

		byte[] head = "hello".getBytes("UTF-8");
		eq("hole: first write", a.write(ByteBuffer.wrap(head), 0L), write(b, head, 0L));

		byte[] far = "far".getBytes("UTF-8");
		eq("hole: far write", a.write(ByteBuffer.wrap(far), 4096L), write(b, far, 4096L));
		sameBytes("hole filled with zeroes", a, b, real, virt);
		eq("hole: size", a.size(), size(b));

		// Fill the region that is about to be truncated away with NON-ZERO bytes. Without
		// this the test passes whether or not the implementation zeroes its holes, because
		// the bytes left behind by the earlier hole fill are already zero - which is exactly
		// how the first version of this test passed a deliberately broken build.
		byte[] noise = new byte[5000];
		Arrays.fill(noise, (byte) 0xA5);
		eq("noise write", a.write(ByteBuffer.wrap(noise), 0L), write(b, noise, 0L));
		sameBytes("after noise", a, b, real, virt);

		a.truncate(2048L);
		truncate(b, 2048L);
		sameBytes("after truncate", a, b, real, virt);
		eq("size after truncate", a.size(), size(b));

		// Writing past the end after a truncate: the gap must read back as zeroes, not as the
		// 0xA5 bytes still sitting in the backing array.
		byte[] more = "more".getBytes("UTF-8");
		eq("post-truncate write", a.write(ByteBuffer.wrap(more), 3000L), write(b, more, 3000L));
		sameBytes("post-truncate hole is zeroed", a, b, real, virt);

		// sequential write and position tracking
		position(b, 0L);
		a.position(0L);
		byte[] seq = "sequential".getBytes("UTF-8");
		eq("sequential write", a.write(ByteBuffer.wrap(seq)), writeSeq(b, seq));
		eq("position after sequential write", a.position(), position(b));
		sameBytes("after sequential write", a, b, real, virt);

		a.close();
		close(b);
		sameFileBytes("holes after close", real, virt);
	}

	/** Open-mode behaviour DirectoryLock.isLocked depends on. */
	static void openModes(Path dir) throws Exception {
		Path missingReal = dir.resolve("nope.lock");
		String missingVirt = "nope.lock";
		reset(missingVirt);

		boolean jdkThrew = false;
		try {
			FileChannel.open(missingReal, StandardOpenOption.WRITE).close();
		} catch (NoSuchFileException e) {
			jdkThrew = true;
		}
		boolean oursThrew = false;
		try {
			close(open(missingVirt, "WRITE"));
		} catch (Exception e) {
			oursThrew = causeIs(e, NoSuchFileException.class);
		}
		eqBool("WRITE on a missing file throws NoSuchFileException", jdkThrew, oursThrew);

		// CREATE_NEW on an existing file
		Path existsReal = dir.resolve("exists.bin");
		String existsVirt = "exists.bin";
		reset(existsVirt);
		FileChannel.open(existsReal, StandardOpenOption.CREATE, StandardOpenOption.WRITE).close();
		close(open(existsVirt, "CREATE", "WRITE"));

		boolean jdkNew = false;
		try {
			FileChannel.open(existsReal, StandardOpenOption.CREATE_NEW,
					StandardOpenOption.WRITE).close();
		} catch (FileAlreadyExistsException e) {
			jdkNew = true;
		}
		boolean ourNew = false;
		try {
			close(open(existsVirt, "CREATE_NEW", "WRITE"));
		} catch (Exception e) {
			ourNew = causeIs(e, FileAlreadyExistsException.class);
		}
		eqBool("CREATE_NEW on an existing file throws", jdkNew, ourNew);

		// an empty CREATE must materialise the file even with nothing written
		Path emptyReal = dir.resolve("empty.lock");
		String emptyVirt = "empty.lock";
		reset(emptyVirt);
		FileChannel.open(emptyReal, StandardOpenOption.CREATE, StandardOpenOption.WRITE).close();
		close(open(emptyVirt, "CREATE", "WRITE"));
		eqBool("empty CREATE materialises the file", Files.exists(emptyReal), present(emptyVirt));
		eq("empty CREATE size", Files.size(emptyReal), (long) peekLength(emptyVirt));
	}

	/** What is written must still be there on the next open - this is a save file. */
	static void persistenceAcrossReopen(Path dir) throws Exception {
		Path real = dir.resolve("persist.bin");
		String virt = "persist.bin";
		reset(virt);

		byte[] payload = new byte[5000];
		new Random(4242).nextBytes(payload);

		FileChannel a = FileChannel.open(real, StandardOpenOption.CREATE,
				StandardOpenOption.WRITE);
		Object b = open(virt, "CREATE", "WRITE");
		a.write(ByteBuffer.wrap(payload), 0L);
		write(b, payload, 0L);
		a.close();
		close(b);

		FileChannel a2 = FileChannel.open(real, StandardOpenOption.READ);
		Object b2 = open(virt, "READ");
		eq("reopened size", a2.size(), size(b2));
		ByteBuffer ba = ByteBuffer.allocate(5000);
		byte[] bb = new byte[5000];
		eq("reopened read", a2.read(ba, 0L), read(b2, bb, 0, 5000, 0L));
		sameArray("reopened contents", ba.array(), bb);
		a2.close();
		close(b2);
	}

	/** The contract DirectoryLock relies on, tested on its own terms. */
	static void locking() throws Exception {
		reset("session.lock");
		Object one = open("session.lock", "CREATE", "WRITE");
		Object lock = tryLock(one);
		check("first tryLock succeeds", lock != null);

		Object two = open("session.lock", "CREATE", "WRITE");
		check("second tryLock on a locked path returns null", tryLock(two) == null);

		Method release = lock.getClass().getMethod("release");
		release.setAccessible(true);
		release.invoke(lock);
		Method isValid = lock.getClass().getMethod("isValid");
		isValid.setAccessible(true);
		check("released lock reports invalid", !((Boolean) isValid.invoke(lock)));

		Object three = tryLock(two);
		check("tryLock succeeds again after release", three != null);
		close(two);
		close(one);

		// closing a channel must release the lock it holds
		Object four = open("session.lock", "CREATE", "WRITE");
		check("lock after close of the holder", tryLock(four) != null);
		close(four);
		Object five = open("session.lock", "CREATE", "WRITE");
		check("close released the lock", tryLock(five) != null);
		close(five);
	}

	// ------------------------------------------------------------------ reflection glue

	static Object open(String path, String... options) throws Exception {
		// The array handed to a varargs method through reflection must have the declared
		// component type, not Object[].
		java.nio.file.OpenOption[] opts = new java.nio.file.OpenOption[options.length];
		for (int i = 0; i < options.length; ++i) {
			opts[i] = StandardOpenOption.valueOf(options[i]);
		}
		Method m = TFC.getMethod("open", Path.class, java.nio.file.OpenOption[].class);
		m.setAccessible(true);
		return m.invoke(null, Paths.get(path), (Object) opts);
	}

	static int write(Object ch, byte[] data, long at) throws Exception {
		Method m = TFC.getMethod("write", ByteBuffer.class, long.class);
		m.setAccessible(true);
		return (Integer) m.invoke(ch, ByteBuffer.wrap(data), at);
	}

	static int writeSeq(Object ch, byte[] data) throws Exception {
		Method m = TFC.getMethod("write", ByteBuffer.class);
		m.setAccessible(true);
		return (Integer) m.invoke(ch, ByteBuffer.wrap(data));
	}

	static int read(Object ch, byte[] dst, int off, int len, long at) throws Exception {
		Method m = TFC.getMethod("read", ByteBuffer.class, long.class);
		m.setAccessible(true);
		ByteBuffer buf = ByteBuffer.wrap(dst, off, len);
		int n = (Integer) m.invoke(ch, buf, at);
		return n;
	}

	static long size(Object ch) throws Exception {
		Method m = TFC.getMethod("size");
		m.setAccessible(true);
		return (Long) m.invoke(ch);
	}

	static long position(Object ch) throws Exception {
		Method m = TFC.getMethod("position");
		m.setAccessible(true);
		return (Long) m.invoke(ch);
	}

	static void position(Object ch, long at) throws Exception {
		Method m = TFC.getMethod("position", long.class);
		m.setAccessible(true);
		m.invoke(ch, at);
	}

	static void truncate(Object ch, long at) throws Exception {
		Method m = TFC.getMethod("truncate", long.class);
		m.setAccessible(true);
		m.invoke(ch, at);
	}

	static void force(Object ch) throws Exception {
		Method m = TFC.getMethod("force", boolean.class);
		m.setAccessible(true);
		m.invoke(ch, true);
	}

	static void close(Object ch) throws Exception {
		Method m = TFC.getMethod("close");
		m.setAccessible(true);
		m.invoke(ch);
	}

	static Object tryLock(Object ch) throws Exception {
		Method m = TFC.getMethod("tryLock");
		m.setAccessible(true);
		return m.invoke(ch);
	}

	static void reset(String path) throws Exception {
		STUB.getMethod("reset").invoke(null);
	}

	static boolean present(String path) throws Exception {
		return (Boolean) STUB.getMethod("present", String.class).invoke(null, path);
	}

	static int peekLength(String path) throws Exception {
		byte[] b = (byte[]) STUB.getMethod("peek", String.class).invoke(null, path);
		return b == null ? -1 : b.length;
	}

	static boolean causeIs(Throwable t, Class<?> type) {
		while (t != null) {
			if (type.isInstance(t)) {
				return true;
			}
			t = t.getCause();
		}
		return false;
	}

	// ------------------------------------------------------------------ assertions

	static void check(String what, boolean ok) {
		++checks;
		if (!ok) {
			System.out.println("FAIL " + what);
			++failures;
		}
	}

	static void eq(String what, long expected, long actual) {
		++checks;
		if (expected != actual) {
			System.out.println("FAIL " + what + ": jdk=" + expected + " ours=" + actual);
			++failures;
		}
	}

	static void eqBool(String what, boolean expected, boolean actual) {
		++checks;
		if (expected != actual) {
			System.out.println("FAIL " + what + ": jdk=" + expected + " ours=" + actual);
			++failures;
		}
	}

	static void sameArray(String what, byte[] expected, byte[] actual) {
		++checks;
		if (!Arrays.equals(expected, actual)) {
			int at = -1;
			for (int i = 0; i < Math.min(expected.length, actual.length); ++i) {
				if (expected[i] != actual[i]) {
					at = i;
					break;
				}
			}
			System.out.println("FAIL " + what + ": differ at " + at + " (lengths "
					+ expected.length + " vs " + actual.length + ")");
			++failures;
		}
	}

	/** Compares the live contents of both channels, forcing ours so the store is current. */
	static void sameBytes(String what, FileChannel a, Object b, Path real, String virt)
			throws Exception {
		a.force(true);
		force(b);
		sameFileBytes(what, real, virt);
	}

	static void sameFileBytes(String what, Path real, String virt) throws Exception {
		byte[] expected = Files.readAllBytes(real);
		byte[] actual = (byte[]) STUB.getMethod("peek", String.class).invoke(null, virt);
		if (actual == null) {
			actual = new byte[0];
		}
		sameArray(what, expected, actual);
	}

	static void deleteTree(Path dir) {
		try {
			Files.walk(dir).sorted(java.util.Comparator.reverseOrder()).forEach(p -> {
				try {
					Files.deleteIfExists(p);
				} catch (IOException ignored) {
					// best effort
				}
			});
		} catch (IOException ignored) {
			// best effort
		}
	}
}
