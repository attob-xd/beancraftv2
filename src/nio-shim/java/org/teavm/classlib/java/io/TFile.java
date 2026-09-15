package org.teavm.classlib.java.io;

import java.io.FileFilter;
import java.io.FilenameFilter;
import java.io.IOException;
import java.net.URI;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

import net.lax1dude.eaglercraft.internal.vfs2.VFile2;

/**
 * Replaces TeaVM's File, for two reasons.
 *
 * The immediate one is toPath(). TeaVM's File does not have it, and Minecraft's constructor
 * builds its LevelStorageSource from gameDirectory.toPath().resolve("saves") - so the client
 * could not be constructed at all. A class cannot be partially replaced, so adding one method
 * means supplying the whole thing.
 *
 * The second is where the bytes live. TeaVM backs File with its own in-memory virtual
 * filesystem, which starts empty on every page load and is discarded when the tab closes -
 * so options.txt, servers.dat, hotbar.nbt and every world would be written and then lost.
 * EaglercraftX already has a real filesystem, VFile2, backed by IndexedDB and shared with
 * the rest of the client, so this is backed by that instead and vanilla's files persist.
 * TFileInputStream and TFileOutputStream are replaced alongside for the same reason: a File
 * that reports what VFile2 holds while streams read TeaVM's VFS would agree about nothing.
 *
 * Two honest differences from a real filesystem, both inherent to VFile2:
 *
 *   - Directories are implicit. VFile2 is a key-value store keyed by path, so a directory
 *     "exists" exactly when some file sits under it. mkdir() and mkdirs() therefore have
 *     nothing to create in the store - but they do remember the path for the life of the
 *     page, and isDirectory()/exists() honour that memory. Without it an empty directory
 *     could never be seen to exist, and Minecraft asks exactly that question:
 *     LevelStorageSource creates "saves" in its constructor and then, when the Singleplayer
 *     button is pressed, refuses to list worlds unless Files.isDirectory("saves") is true.
 *     On a profile with no worlds yet that is an empty directory, so the world screen died
 *     with "Unable to read or access folder where game worlds are saved!" before a single
 *     world could be created. The memory is per page load on purpose: an empty directory
 *     has no representation in the store, so there is nothing to persist, and the code that
 *     needs it re-creates it every start anyway.
 *   - Timestamps are not kept, so lastModified() is 0 and setLastModified() reports failure
 *     rather than pretending.
 *
 * One inconsistency is left deliberately: RandomAccessFile is still TeaVM's, and still reads
 * TeaVM's in-memory VFS, so it does not see files written through here. VFile2 stores a file
 * as a single record and offers no seekable accessor, so backing RandomAccessFile onto it
 * would mean reading and rewriting the whole file per seek. Nothing in this build uses it on
 * a real file - vanilla's RegionFile goes through FileChannel, which is a separate gap - so
 * the cost of leaving it is a divergence nothing currently crosses.
 */
public class TFile implements Comparable<TFile> {

	public static final char separatorChar = '/';
	public static final String separator = "/";
	public static final char pathSeparatorChar = ':';
	public static final String pathSeparator = ":";

	private final String path;

	public TFile(String path) {
		this.path = normalize(path);
	}

	public TFile(String parent, String child) {
		this.path = normalize(join(parent, child));
	}

	public TFile(TFile parent, String child) {
		this.path = normalize(join(parent == null ? null : parent.path, child));
	}

	public TFile(URI uri) {
		this(uri.getPath());
	}

	private static String join(String parent, String child) {
		if (parent == null || parent.isEmpty()) {
			return child;
		}
		if (child == null || child.isEmpty()) {
			return parent;
		}
		return parent + separator + child;
	}

	/**
	 * Backslashes become slashes, and empty and "." segments are dropped.
	 *
	 * The "." part matters and VFile2.normalizePath does not do it - it only swaps
	 * separators and trims a leading and trailing slash. The client's game directory is
	 * new File("."), so without this every vanilla path would be stored under a literal "."
	 * directory ("./options.txt", "./saves/..."), in a corner of the filesystem separate
	 * from everything EaglercraftX writes. It would be self-consistent, and wrong.
	 *
	 * ".." is deliberately left in place rather than resolved: VFile2 treats a path
	 * containing it as relative and refuses to act on it, and that refusal is worth
	 * preserving rather than quietly resolving away.
	 */
	private static String normalize(String p) {
		if (p == null) {
			throw new NullPointerException("path");
		}
		String flat = VFile2.normalizePath(p);
		StringBuilder sb = new StringBuilder(flat.length());
		for (String part : flat.split("/")) {
			if (part.isEmpty() || part.equals(".")) {
				continue;
			}
			if (sb.length() > 0) {
				sb.append(separatorChar);
			}
			sb.append(part);
		}
		return sb.toString();
	}

	private VFile2 vfile() {
		return new VFile2(path);
	}

	/** Directories created through mkdir()/mkdirs() this page load; see the class comment. */
	private static final java.util.Set<String> CREATED_DIRECTORIES = new java.util.HashSet<>();

	private boolean isRememberedDirectory() {
		return path.isEmpty() || CREATED_DIRECTORIES.contains(path);
	}

	/**
	 * Whether anything is stored <i>under</i> this path, which is what makes an implicit
	 * directory exist.
	 *
	 * <p>Deliberately not {@code VFile2.dirExists()}. The filesystem's iterate matches keys by
	 * raw string prefix with no separator required, so listing "&hellip;/level.dat" also
	 * returns "&hellip;/level.dat_old", and a file reports itself as a directory the moment
	 * its own backup is written next to it.
	 *
	 * <p>That is not cosmetic - it is what broke world saving. vanilla's
	 * {@code Util.safeReplaceFile} rotates level.dat to level.dat_old, then confirms the old
	 * one is gone with {@code Files.exists("level.dat")}. Under the prefix match that answer
	 * was always yes, so every autosave ran its "remove old level.dat" step ten times and gave
	 * up with "aborting, progress might be lost"; the following save then saw exists()==true
	 * for a level.dat that was not there, deleted level.dat_old out from under itself, and
	 * failed the rename with NoSuchFileException - ten more times, every save, for ever.
	 *
	 * <p>Requiring the separator is the whole fix. A directory's children all begin with the
	 * directory's path plus one; a sibling whose name merely starts the same way does not.
	 */
	private boolean hasChildren() {
		VFile2 f = vfile();
		String prefix = f.toString() + separatorChar;
		List<VFile2> children = f.listFiles(true);
		for (int i = 0, l = children.size(); i < l; ++i) {
			if (children.get(i).toString().startsWith(prefix)) {
				return true;
			}
		}
		return false;
	}

	public String getPath() {
		return path;
	}

	public String getName() {
		int i = path.lastIndexOf(separatorChar);
		return i < 0 ? path : path.substring(i + 1);
	}

	public String getParent() {
		int i = path.lastIndexOf(separatorChar);
		return i <= 0 ? null : path.substring(0, i);
	}

	public TFile getParentFile() {
		String parent = getParent();
		return parent == null ? null : new TFile(parent);
	}

	/**
	 * Every path is relative here: VFile2 has one root and no working directory, so there is
	 * no "/" to be absolute against.
	 */
	public boolean isAbsolute() {
		return false;
	}

	public String getAbsolutePath() {
		return path;
	}

	public TFile getAbsoluteFile() {
		return this;
	}

	public String getCanonicalPath() throws IOException {
		return path;
	}

	public TFile getCanonicalFile() throws IOException {
		return this;
	}

	public Path toPath() {
		return Paths.get(path);
	}

	public URI toURI() {
		return URI.create("file:///" + path);
	}

	public boolean exists() {
		VFile2 f = vfile();
		return f.exists() || isRememberedDirectory() || hasChildren();
	}

	public boolean isFile() {
		return vfile().exists();
	}

	public boolean isDirectory() {
		VFile2 f = vfile();
		return !f.exists() && (isRememberedDirectory() || hasChildren());
	}

	public boolean isHidden() {
		return getName().startsWith(".");
	}

	public boolean canRead() {
		return vfile().exists();
	}

	public boolean canWrite() {
		return true;
	}

	public long length() {
		int len = vfile().length();
		return len < 0 ? 0L : len;
	}

	/** VFile2 keeps no timestamps; see the class comment. */
	public long lastModified() {
		return 0L;
	}

	public boolean setLastModified(long time) {
		return false;
	}

	public boolean setReadOnly() {
		return false;
	}

	public boolean setWritable(boolean writable) {
		return writable;
	}

	public boolean setReadable(boolean readable) {
		return readable;
	}

	public boolean createNewFile() throws IOException {
		VFile2 f = vfile();
		if (f.exists()) {
			return false;
		}
		f.setAllBytes(new byte[0]);
		return true;
	}

	/**
	 * Where the two-argument createTempFile puts things. There is no TMPDIR here and VFile2
	 * has a single root, so a directory is simply named. It is an ordinary directory in the
	 * browser filesystem, which means it persists - that is the honest description of it.
	 */
	private static final String TEMP_DIR = "tmp";

	private static int tempCounter;

	/**
	 * Minecraft saves through this, which is why it is here rather than left missing.
	 * LevelStorageSource, PlayerDataStorage and ServerList each write a temp file and then move
	 * it over the real one, so a crash mid-write cannot leave a half-written level.dat or
	 * servers.dat. Without it, saving anything at all is a NoSuchMethodError - and ServerList
	 * means that starts at the multiplayer screen, not at a world.
	 *
	 * Two honest differences from the JDK, both from the platform rather than from choice:
	 *
	 *   - The name is unique but not unpredictable. The JDK draws it from SecureRandom so
	 *     another process cannot guess it; a page has no other process to hide from. A counter
	 *     plus the clock gives uniqueness, which is all the atomic-save pattern needs.
	 *   - Nothing is cleaned up on exit - deleteOnExit has nothing to hook here, as the method
	 *     below says. Callers that move the file into place, which is all of Minecraft's, leave
	 *     nothing behind anyway.
	 *
	 * The JDK's argument checks are kept, including the three-character minimum on the prefix,
	 * so a call that would fail on the desktop fails here too instead of quietly working.
	 */
	public static TFile createTempFile(String prefix, String suffix, TFile directory)
			throws IOException {
		if (prefix == null) {
			throw new NullPointerException("prefix");
		}
		if (prefix.length() < 3) {
			throw new IllegalArgumentException("Prefix string \"" + prefix
					+ "\" too short: length must be at least 3");
		}
		String tail = suffix == null ? ".tmp" : suffix;
		TFile dir = directory != null ? directory : new TFile(TEMP_DIR);
		dir.mkdirs();
		for (int attempt = 0; attempt < 10000; ++attempt) {
			String name = prefix + Long.toString(System.currentTimeMillis(), 36)
					+ Integer.toString(++tempCounter, 36) + tail;
			TFile candidate = new TFile(dir, name);
			if (candidate.createNewFile()) {
				return candidate;
			}
		}
		throw new IOException("Could not create a temp file in " + dir.getPath()
				+ " after 10000 attempts");
	}

	public static TFile createTempFile(String prefix, String suffix) throws IOException {
		return createTempFile(prefix, suffix, null);
	}

	public boolean delete() {
		return vfile().delete();
	}

	public void deleteOnExit() {
		// A page is not told when it closes, and there is nothing to run if it were.
	}

	public boolean renameTo(TFile dest) {
		return dest != null && vfile().renameTo(dest.path);
	}

	/** Nothing to create; see the class comment on implicit directories. */
	public boolean mkdir() {
		if (vfile().exists()) {
			return false; // a file is in the way, which is what a real filesystem reports
		}
		CREATED_DIRECTORIES.add(path);
		return true;
	}

	public boolean mkdirs() {
		if (vfile().exists()) {
			return false;
		}
		// Every ancestor becomes a directory too, as on a real filesystem.
		String p = path;
		while (!p.isEmpty()) {
			CREATED_DIRECTORIES.add(p);
			int slash = p.lastIndexOf(separatorChar);
			p = slash < 0 ? "" : p.substring(0, slash);
		}
		return true;
	}

	/**
	 * The immediate children of this path. VFile2 lists whole paths recursively, so the
	 * results are cut back to one level and de-duplicated - two files under the same
	 * subdirectory must yield that subdirectory once, not twice.
	 */
	public String[] list() {
		VFile2 dir = vfile();
		List<String> names = new ArrayList<>();
		String prefix = path.isEmpty() ? "" : path + separator;
		for (String full : dir.listFilenames(true)) {
			if (!full.startsWith(prefix)) {
				continue;
			}
			String rest = full.substring(prefix.length());
			int slash = rest.indexOf(separatorChar);
			String child = slash < 0 ? rest : rest.substring(0, slash);
			if (!child.isEmpty() && !names.contains(child)) {
				names.add(child);
			}
		}
		return names.toArray(new String[0]);
	}

	public String[] list(FilenameFilter filter) {
		String[] all = list();
		if (filter == null) {
			return all;
		}
		List<String> out = new ArrayList<>();
		for (String name : all) {
			if (filter.accept(asJavaFile(), name)) {
				out.add(name);
			}
		}
		return out.toArray(new String[0]);
	}

	public TFile[] listFiles() {
		String[] names = list();
		TFile[] out = new TFile[names.length];
		for (int i = 0; i < names.length; ++i) {
			out[i] = new TFile(this, names[i]);
		}
		return out;
	}

	public TFile[] listFiles(FilenameFilter filter) {
		List<TFile> out = new ArrayList<>();
		for (TFile f : listFiles()) {
			if (filter == null || filter.accept(asJavaFile(), f.getName())) {
				out.add(f);
			}
		}
		return out.toArray(new TFile[0]);
	}

	public TFile[] listFiles(FileFilter filter) {
		List<TFile> out = new ArrayList<>();
		for (TFile f : listFiles()) {
			if (filter == null || filter.accept(f.asJavaFile())) {
				out.add(f);
			}
		}
		return out.toArray(new TFile[0]);
	}

	/**
	 * The filter interfaces are declared against java.io.File. This class *is* java.io.File
	 * once TeaVM has mapped it, so the cast is an identity at run time; it exists only
	 * because javac, compiling against the real JDK, cannot see that.
	 */
	@SuppressWarnings("unchecked")
	private java.io.File asJavaFile() {
		return (java.io.File) (Object) this;
	}

	/** There is one root and it is the empty path. */
	public static TFile[] listRoots() {
		return new TFile[] { new TFile("") };
	}

	public long getTotalSpace() {
		return 0L;
	}

	public long getFreeSpace() {
		return 0L;
	}

	public long getUsableSpace() {
		return 0L;
	}

	@Override
	public int compareTo(TFile other) {
		return path.compareTo(other.path);
	}

	@Override
	public boolean equals(Object o) {
		return o instanceof TFile && path.equals(((TFile) o).path);
	}

	@Override
	public int hashCode() {
		return path.hashCode();
	}

	@Override
	public String toString() {
		return path;
	}
}
