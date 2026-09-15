package org.teavm.classlib.java.nio.file;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import org.teavm.classlib.java.nio.file.attribute.TBasicFileAttributes;
import org.teavm.classlib.java.nio.file.attribute.TFileAttribute;
import org.teavm.classlib.java.nio.file.attribute.TFileTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.function.BiPredicate;
import java.util.stream.Stream;

/**
 * The java.io.File-backed half of the shim; see TPath for the whole story.
 *
 * Every TLinkOption is ignored (there are no symbolic links), and the file attributes
 * passed to the create* methods are ignored (there are no POSIX permissions). Where the
 * real TFiles throws on a missing file, so does this one, because vanilla's save code
 * relies on it: LevelStorageSource reads TNoSuchFileException as "no such world" rather
 * than as a failure.
 */
public final class TFiles {

	private TFiles() {
	}

	public static boolean exists(TPath path, TLinkOption... options) {
		return path.toFile().exists();
	}

	public static boolean notExists(TPath path, TLinkOption... options) {
		return !exists(path, options);
	}

	public static boolean isDirectory(TPath path, TLinkOption... options) {
		return path.toFile().isDirectory();
	}

	public static boolean isRegularFile(TPath path, TLinkOption... options) {
		return path.toFile().isFile();
	}

	public static boolean isReadable(TPath path) {
		return path.toFile().canRead();
	}

	public static boolean isWritable(TPath path) {
		return path.toFile().canWrite();
	}

	public static boolean isHidden(TPath path) {
		TPath name = path.getFileName();
		return name != null && name.toString().startsWith(".");
	}

	public static long size(TPath path) throws IOException {
		File f = path.toFile();
		if (!f.exists()) {
			throw new TNoSuchFileException(path.toString());
		}
		return f.length();
	}

	public static TFileTime getLastModifiedTime(TPath path, TLinkOption... options)
			throws IOException {
		File f = path.toFile();
		if (!f.exists()) {
			throw new TNoSuchFileException(path.toString());
		}
		return TFileTime.fromMillis(f.lastModified());
	}

	public static TPath createDirectory(TPath dir, TFileAttribute<?>... attrs) throws IOException {
		File f = dir.toFile();
		if (f.isDirectory()) {
			throw new TFileAlreadyExistsException(dir.toString());
		}
		if (!f.mkdir()) {
			throw new IOException("Could not create directory: " + dir);
		}
		return dir;
	}

	public static TPath createDirectories(TPath dir, TFileAttribute<?>... attrs) throws IOException {
		File f = dir.toFile();
		if (!f.isDirectory() && !f.mkdirs() && !f.isDirectory()) {
			throw new IOException("Could not create directory: " + dir);
		}
		return dir;
	}

	public static TPath createFile(TPath path, TFileAttribute<?>... attrs) throws IOException {
		File f = path.toFile();
		if (f.exists()) {
			throw new TFileAlreadyExistsException(path.toString());
		}
		if (!f.createNewFile()) {
			throw new IOException("Could not create file: " + path);
		}
		return path;
	}

	private static int tempCounter;

	/**
	 * There is no OS temp directory behind a browser tab, so temporaries live under "tmp"
	 * in the working directory and are named from the clock plus a counter rather than
	 * from a secure random source. Nothing here is multi-tenant.
	 */
	private static TPath tempName(TPath dir, String prefix, String suffix) {
		return dir.resolve((prefix == null ? "" : prefix) + System.currentTimeMillis() + "-"
				+ (tempCounter++) + suffix);
	}

	public static TPath createTempDirectory(String prefix, TFileAttribute<?>... attrs)
			throws IOException {
		return createTempDirectory(TPaths.get("tmp"), prefix, attrs);
	}

	public static TPath createTempDirectory(TPath dir, String prefix, TFileAttribute<?>... attrs)
			throws IOException {
		TPath out = tempName(dir, prefix, "");
		createDirectories(out);
		return out;
	}

	public static TPath createTempFile(TPath dir, String prefix, String suffix,
			TFileAttribute<?>... attrs) throws IOException {
		createDirectories(dir);
		TPath out = tempName(dir, prefix, suffix == null ? ".tmp" : suffix);
		out.toFile().createNewFile();
		return out;
	}

	public static TPath createTempFile(String prefix, String suffix, TFileAttribute<?>... attrs)
			throws IOException {
		return createTempFile(TPaths.get("tmp"), prefix, suffix, attrs);
	}

	public static void delete(TPath path) throws IOException {
		File f = path.toFile();
		if (!f.exists()) {
			throw new TNoSuchFileException(path.toString());
		}
		if (!f.delete()) {
			throw new IOException("Could not delete: " + path);
		}
	}

	public static boolean deleteIfExists(TPath path) throws IOException {
		File f = path.toFile();
		return f.exists() && f.delete();
	}

	public static TPath move(TPath source, TPath target, TCopyOption... options) throws IOException {
		File from = source.toFile();
		File to = target.toFile();
		if (!from.exists()) {
			throw new TNoSuchFileException(source.toString());
		}
		if (to.exists()) {
			if (!Arrays.asList(options).contains(TStandardCopyOption.REPLACE_EXISTING)) {
				throw new TFileAlreadyExistsException(target.toString());
			}
			to.delete();
		}
		if (!from.renameTo(to)) {
			// A rename across directories is not guaranteed by every backing filesystem;
			// copy-then-delete leaves the same observable result.
			copy(source, target, TStandardCopyOption.REPLACE_EXISTING);
			delete(source);
		}
		return target;
	}

	public static TPath copy(TPath source, TPath target, TCopyOption... options) throws IOException {
		if (exists(target)
				&& !Arrays.asList(options).contains(TStandardCopyOption.REPLACE_EXISTING)) {
			throw new TFileAlreadyExistsException(target.toString());
		}
		try (InputStream in = newInputStream(source); OutputStream out = newOutputStream(target)) {
			transfer(in, out);
		}
		return target;
	}

	public static long copy(InputStream in, TPath target, TCopyOption... options) throws IOException {
		try (OutputStream out = newOutputStream(target)) {
			return transfer(in, out);
		}
	}

	public static long copy(TPath source, OutputStream out) throws IOException {
		try (InputStream in = newInputStream(source)) {
			return transfer(in, out);
		}
	}

	private static long transfer(InputStream in, OutputStream out) throws IOException {
		byte[] buf = new byte[8192];
		long total = 0;
		int r;
		while ((r = in.read(buf)) > 0) {
			out.write(buf, 0, r);
			total += r;
		}
		return total;
	}

	public static InputStream newInputStream(TPath path, TOpenOption... options) throws IOException {
		File f = path.toFile();
		if (!f.exists()) {
			throw new TNoSuchFileException(path.toString());
		}
		return new FileInputStream(f);
	}

	public static OutputStream newOutputStream(TPath path, TOpenOption... options)
			throws IOException {
		TPath parent = path.getParent();
		if (parent != null) {
			createDirectories(parent);
		}
		boolean append = Arrays.asList(options).contains(TStandardOpenOption.APPEND);
		return new FileOutputStream(path.toFile(), append);
	}

	public static BufferedReader newBufferedReader(TPath path) throws IOException {
		return newBufferedReader(path, StandardCharsets.UTF_8);
	}

	public static BufferedReader newBufferedReader(TPath path, Charset cs) throws IOException {
		return new BufferedReader(new InputStreamReader(newInputStream(path), cs));
	}

	public static BufferedWriter newBufferedWriter(TPath path, TOpenOption... options)
			throws IOException {
		return newBufferedWriter(path, StandardCharsets.UTF_8, options);
	}

	public static BufferedWriter newBufferedWriter(TPath path, Charset cs, TOpenOption... options)
			throws IOException {
		return new BufferedWriter(new OutputStreamWriter(newOutputStream(path, options), cs));
	}

	public static byte[] readAllBytes(TPath path) throws IOException {
		try (InputStream in = newInputStream(path)) {
			ByteArrayOutputStream out = new ByteArrayOutputStream();
			transfer(in, out);
			return out.toByteArray();
		}
	}

	public static List<String> readAllLines(TPath path) throws IOException {
		return readAllLines(path, StandardCharsets.UTF_8);
	}

	public static List<String> readAllLines(TPath path, Charset cs) throws IOException {
		List<String> lines = new ArrayList<>();
		try (BufferedReader r = newBufferedReader(path, cs)) {
			String line;
			while ((line = r.readLine()) != null) {
				lines.add(line);
			}
		}
		return lines;
	}

	public static String readString(TPath path) throws IOException {
		return new String(readAllBytes(path), StandardCharsets.UTF_8);
	}

	public static TPath write(TPath path, byte[] bytes, TOpenOption... options) throws IOException {
		try (OutputStream out = newOutputStream(path, options)) {
			out.write(bytes);
		}
		return path;
	}

	public static TPath writeString(TPath path, CharSequence text, TOpenOption... options)
			throws IOException {
		return write(path, text.toString().getBytes(StandardCharsets.UTF_8), options);
	}

	private static List<TPath> listDirectory(TPath dir) throws IOException {
		File f = dir.toFile();
		if (!f.isDirectory()) {
			throw new TNotDirectoryException(dir.toString());
		}
		String[] names = f.list();
		List<TPath> out = new ArrayList<>();
		if (names != null) {
			Arrays.sort(names);
			for (String name : names) {
				out.add(dir.resolve(name));
			}
		}
		return out;
	}

	private static TDirectoryStream<TPath> streamOf(List<TPath> entries) {
		return new TDirectoryStream<TPath>() {
			@Override
			public Iterator<TPath> iterator() {
				return entries.iterator();
			}

			@Override
			public void close() {
			}
		};
	}

	public static TDirectoryStream<TPath> newDirectoryStream(TPath dir) throws IOException {
		return streamOf(listDirectory(dir));
	}

	public static TDirectoryStream<TPath> newDirectoryStream(TPath dir,
			TDirectoryStream.Filter<? super TPath> filter) throws IOException {
		List<TPath> kept = new ArrayList<>();
		for (TPath p : listDirectory(dir)) {
			if (filter.accept(p)) {
				kept.add(p);
			}
		}
		return streamOf(kept);
	}

	public static Stream<TPath> list(TPath dir) throws IOException {
		return listDirectory(dir).stream();
	}

	public static Stream<TPath> walk(TPath start, TFileVisitOption... options) throws IOException {
		return walk(start, Integer.MAX_VALUE, options);
	}

	public static Stream<TPath> walk(TPath start, int maxDepth, TFileVisitOption... options)
			throws IOException {
		List<TPath> found = new ArrayList<>();
		collect(start, maxDepth, found);
		return found.stream();
	}

	public static Stream<TPath> find(TPath start, int maxDepth,
			BiPredicate<TPath, TBasicFileAttributes> matcher, TFileVisitOption... options)
			throws IOException {
		List<TPath> found = new ArrayList<>();
		collect(start, maxDepth, found);
		return found.stream().filter(p -> matcher.test(p, attributesOf(p)));
	}

	private static void collect(TPath path, int depth, List<TPath> out) {
		out.add(path);
		if (depth <= 0 || !isDirectory(path)) {
			return;
		}
		String[] names = path.toFile().list();
		if (names == null) {
			return;
		}
		Arrays.sort(names);
		for (String name : names) {
			collect(path.resolve(name), depth - 1, out);
		}
	}

	public static TPath walkFileTree(TPath start, TFileVisitor<? super TPath> visitor)
			throws IOException {
		visit(start, visitor);
		return start;
	}

	public static TPath walkFileTree(TPath start, Set<TFileVisitOption> options, int maxDepth,
			TFileVisitor<? super TPath> visitor) throws IOException {
		visit(start, visitor);
		return start;
	}

	private static TFileVisitResult visit(TPath path, TFileVisitor<? super TPath> visitor)
			throws IOException {
		if (!isDirectory(path)) {
			return visitor.visitFile(path, attributesOf(path));
		}
		TFileVisitResult result = visitor.preVisitDirectory(path, attributesOf(path));
		if (result == TFileVisitResult.SKIP_SUBTREE || result == TFileVisitResult.TERMINATE) {
			return result;
		}
		for (TPath child : listDirectory(path)) {
			if (visit(child, visitor) == TFileVisitResult.TERMINATE) {
				return TFileVisitResult.TERMINATE;
			}
		}
		return visitor.postVisitDirectory(path, null);
	}

	public static TBasicFileAttributes readAttributes(TPath path, Class<?> type,
			TLinkOption... options) throws IOException {
		if (!exists(path)) {
			throw new TNoSuchFileException(path.toString());
		}
		return attributesOf(path);
	}

	private static TBasicFileAttributes attributesOf(TPath path) {
		File f = path.toFile();
		return new TBasicFileAttributes() {
			@Override
			public TFileTime lastModifiedTime() {
				return TFileTime.fromMillis(f.lastModified());
			}

			@Override
			public TFileTime lastAccessTime() {
				return lastModifiedTime();
			}

			@Override
			public TFileTime creationTime() {
				return lastModifiedTime();
			}

			@Override
			public boolean isRegularFile() {
				return f.isFile();
			}

			@Override
			public boolean isDirectory() {
				return f.isDirectory();
			}

			@Override
			public boolean isSymbolicLink() {
				return false;
			}

			@Override
			public boolean isOther() {
				return false;
			}

			@Override
			public long size() {
				return f.length();
			}

			@Override
			public Object fileKey() {
				return null;
			}
		};
	}
}
