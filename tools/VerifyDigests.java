/**
 * Checks EaglercraftX's pure-Java digests against the JDK's own, on the desktop JVM.
 *
 *     java -cp "build/classes/java/main" tools/VerifyDigests.java
 *
 * TMessageDigest hands these out in place of the JCA, which TeaVM cannot provide. That makes
 * them load-bearing in a way they were not before: 1.18.2 seeds positional random sources -
 * and therefore terrain features - from an MD5 of a string, so a digest that merely *looked*
 * random would generate a different world from the same seed without anything ever failing.
 * "It returns 16 bytes" is not the property that matters; "it returns the same 16 bytes the
 * JDK does" is.
 *
 * So this compares against java.security.MessageDigest over the shapes that actually occur:
 * empty input, single bytes, strings, and lengths either side of the 64-byte block boundary
 * and the length-encoding edge at 56 bytes, where a block-padding bug would hide. It also
 * checks the property Guava depends on - that digest() resets, so one instance can hash
 * repeatedly - because a missing reset would make every hash after the first wrong while
 * leaving the first one right.
 *
 * The negative control at the end must FAIL: it feeds one implementation a byte the other
 * never sees. If that pass reports a match, the comparison is not comparing anything.
 */
import net.lax1dude.eaglercraft.crypto.MD5Digest;
import net.lax1dude.eaglercraft.crypto.SHA1Digest;
import net.lax1dude.eaglercraft.crypto.SHA256Digest;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class VerifyDigests {

	static int checks = 0;
	static int failures = 0;

	public static void main(String[] args) throws Exception {
		List<byte[]> inputs = inputs();
		System.out.println("inputs: " + inputs.size());

		for (byte[] in : inputs) {
			compare("MD5", in, eaglerMd5(in));
			compare("SHA-1", in, eaglerSha1(in));
			compare("SHA-256", in, eaglerSha256(in));
		}

		reuse(inputs);
		negativeControl();

		System.out.println();
		System.out.println(checks + " comparison(s), " + failures + " failure(s)");
		System.out.println(failures == 0
				? "OK - EaglercraftX's digests agree with the JDK"
				: "MISMATCH - do not ship this");
		System.exit(failures == 0 ? 0 : 1);
	}

	static List<byte[]> inputs() {
		List<byte[]> out = new ArrayList<>();
		out.add(new byte[0]);
		out.add(new byte[] { 0 });
		out.add("a".getBytes(StandardCharsets.UTF_8));
		out.add("abc".getBytes(StandardCharsets.UTF_8));
		out.add("minecraft:overworld".getBytes(StandardCharsets.UTF_8));
		// Real callers: the strings 1.18.2 forks positional random sources from.
		for (String s : new String[] { "minecraft:ore_coal_upper", "minecraft:village_plains",
				"minecraft:bastion_remnant", "minecraft:stronghold", "octave_-9", "aquifer_barrier" }) {
			out.add(s.getBytes(StandardCharsets.UTF_8));
		}
		// Lengths around the 64-byte block and the 56-byte length-encoding edge.
		Random rand = new Random(1234L);
		for (int len : new int[] { 1, 55, 56, 57, 63, 64, 65, 119, 120, 127, 128, 129, 1000, 4096 }) {
			byte[] b = new byte[len];
			rand.nextBytes(b);
			out.add(b);
		}
		return out;
	}

	static void compare(String algorithm, byte[] input, byte[] theirs) throws Exception {
		++checks;
		byte[] jdk = MessageDigest.getInstance(algorithm).digest(input);
		if (!java.util.Arrays.equals(jdk, theirs)) {
			++failures;
			System.out.println("MISMATCH " + algorithm + " on " + input.length + " byte(s)");
			System.out.println("   jdk:     " + hex(jdk));
			System.out.println("   eagler:  " + hex(theirs));
		}
	}

	/** digest() must reset, or the second hash through one instance includes the first. */
	static void reuse(List<byte[]> inputs) throws Exception {
		MD5Digest shared = new MD5Digest();
		for (byte[] in : inputs) {
			++checks;
			shared.update(in, 0, in.length);
			byte[] out = new byte[shared.getDigestSize()];
			shared.doFinal(out, 0);
			byte[] jdk = MessageDigest.getInstance("MD5").digest(in);
			if (!java.util.Arrays.equals(jdk, out)) {
				++failures;
				System.out.println("MISMATCH MD5 on reuse after " + in.length + " byte(s)"
						+ " - doFinal is not resetting");
				break;
			}
		}
	}

	/**
	 * Must fail. If feeding the two implementations different bytes still reports a match,
	 * the comparison above is vacuous and proves nothing.
	 */
	static void negativeControl() throws Exception {
		byte[] a = "control".getBytes(StandardCharsets.UTF_8);
		byte[] b = "controI".getBytes(StandardCharsets.UTF_8);
		byte[] jdk = MessageDigest.getInstance("MD5").digest(a);
		byte[] eagler = eaglerMd5(b);
		System.out.println();
		if (java.util.Arrays.equals(jdk, eagler)) {
			++failures;
			System.out.println("NEGATIVE CONTROL PASSED - the comparison is vacuous");
		} else {
			System.out.println("negative control fails as it should (different input, different hash)");
		}
	}

	static byte[] eaglerMd5(byte[] in) {
		MD5Digest d = new MD5Digest();
		d.update(in, 0, in.length);
		byte[] out = new byte[d.getDigestSize()];
		d.doFinal(out, 0);
		return out;
	}

	static byte[] eaglerSha1(byte[] in) {
		SHA1Digest d = new SHA1Digest();
		d.update(in, 0, in.length);
		byte[] out = new byte[d.getDigestSize()];
		d.doFinal(out, 0);
		return out;
	}

	static byte[] eaglerSha256(byte[] in) {
		SHA256Digest d = new SHA256Digest();
		d.update(in, 0, in.length);
		byte[] out = new byte[32];
		d.doFinal(out, 0);
		return out;
	}

	static String hex(byte[] b) {
		StringBuilder sb = new StringBuilder(b.length * 2);
		for (byte x : b) {
			sb.append(Character.forDigit((x >> 4) & 0xF, 16)).append(Character.forDigit(x & 0xF, 16));
		}
		return sb.toString();
	}
}
