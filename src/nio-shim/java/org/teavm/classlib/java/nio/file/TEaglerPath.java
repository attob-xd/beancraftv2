package org.teavm.classlib.java.nio.file;

import java.io.File;
import java.net.URI;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/** The only TPath implementation; see TPath. */
final class TEaglerPath implements TPath {

	private final String path;

	TEaglerPath(String path) {
		this.path = path;
	}

	static String normalizeString(String p) {
		p = p.replace('\\', '/');
		boolean absolute = p.startsWith("/");
		List<String> parts = new ArrayList<>();
		for (String part : p.split("/")) {
			if (part.isEmpty() || part.equals(".")) {
				continue;
			}
			if (part.equals("..") && !parts.isEmpty()
					&& !parts.get(parts.size() - 1).equals("..")) {
				parts.remove(parts.size() - 1);
				continue;
			}
			parts.add(part);
		}
		return (absolute ? "/" : "") + String.join("/", parts);
	}

	@Override
	public TPath resolve(String other) {
		if (other.isEmpty()) {
			return this;
		}
		if (other.startsWith("/")) {
			return new TEaglerPath(normalizeString(other));
		}
		return new TEaglerPath(normalizeString(path.isEmpty() ? other : path + "/" + other));
	}

	@Override
	public TPath resolve(TPath other) {
		return resolve(other.toString());
	}

	@Override
	public TPath resolveSibling(String other) {
		TPath parent = getParent();
		return parent == null ? new TEaglerPath(normalizeString(other)) : parent.resolve(other);
	}

	@Override
	public TPath resolveSibling(TPath other) {
		return resolveSibling(other.toString());
	}

	@Override
	public TPath getParent() {
		int i = path.lastIndexOf('/');
		if (i < 0) {
			return null;
		}
		// "/a" has parent "/", not ""
		return new TEaglerPath(i == 0 ? "/" : path.substring(0, i));
	}

	@Override
	public TPath getFileName() {
		int i = path.lastIndexOf('/');
		String name = i < 0 ? path : path.substring(i + 1);
		return name.isEmpty() ? null : new TEaglerPath(name);
	}

	@Override
	public TPath normalize() {
		return new TEaglerPath(normalizeString(path));
	}

	@Override
	public TPath toAbsolutePath() {
		if (isAbsolute()) {
			return this;
		}
		return new TEaglerPath(normalizeString(new File(path).getAbsolutePath()));
	}

	/** There are no symbolic links here, so the real path is just the absolute one. */
	@Override
	public TPath toRealPath(TLinkOption... options) {
		return toAbsolutePath();
	}

	@Override
	public boolean isAbsolute() {
		return path.startsWith("/");
	}

	@Override
	public TPath relativize(TPath other) {
		String[] a = split();
		String[] b = ((TEaglerPath) other).split();
		int common = 0;
		while (common < a.length && common < b.length && a[common].equals(b[common])) {
			++common;
		}
		StringBuilder sb = new StringBuilder();
		for (int i = common; i < a.length; ++i) {
			sb.append(sb.length() == 0 ? "" : "/").append("..");
		}
		for (int i = common; i < b.length; ++i) {
			sb.append(sb.length() == 0 ? "" : "/").append(b[i]);
		}
		return new TEaglerPath(sb.toString());
	}

	@Override
	public boolean startsWith(TPath other) {
		return startsWith(other.toString());
	}

	@Override
	public boolean startsWith(String other) {
		String o = normalizeString(other);
		return path.equals(o) || path.startsWith(o.endsWith("/") ? o : o + "/");
	}

	@Override
	public boolean endsWith(TPath other) {
		return endsWith(other.toString());
	}

	@Override
	public boolean endsWith(String other) {
		String o = normalizeString(other);
		return path.equals(o) || path.endsWith("/" + o);
	}

	@Override
	public int getNameCount() {
		return split().length;
	}

	@Override
	public TPath getName(int index) {
		return new TEaglerPath(split()[index]);
	}

	@Override
	public TPath subpath(int begin, int end) {
		String[] parts = split();
		StringBuilder sb = new StringBuilder();
		for (int i = begin; i < end; ++i) {
			sb.append(sb.length() == 0 ? "" : "/").append(parts[i]);
		}
		return new TEaglerPath(sb.toString());
	}

	@Override
	public TPath getRoot() {
		return isAbsolute() ? new TEaglerPath("/") : null;
	}

	@Override
	public TFileSystem getFileSystem() {
		return TFileSystems.getDefault();
	}

	@Override
	public File toFile() {
		return new File(path);
	}

	@Override
	public URI toUri() {
		return URI.create("file://" + toAbsolutePath());
	}

	@Override
	public Iterator<TPath> iterator() {
		List<TPath> names = new ArrayList<>();
		for (String part : split()) {
			names.add(new TEaglerPath(part));
		}
		return names.iterator();
	}

	@Override
	public TWatchKey register(TWatchService watcher, TWatchEvent.Kind<?>... events) {
		return watcher.register(this);
	}

	private String[] split() {
		String p = isAbsolute() ? path.substring(1) : path;
		return p.isEmpty() ? new String[0] : p.split("/");
	}

	@Override
	public int compareTo(TPath other) {
		return path.compareTo(other.toString());
	}

	@Override
	public boolean equals(Object o) {
		return o instanceof TEaglerPath && ((TEaglerPath) o).path.equals(path);
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
