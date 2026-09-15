package net.minecraft;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Supplier;

import net.lax1dude.eaglercraft.EagRuntime;
import net.lax1dude.eaglercraft.EaglercraftVersion;
import net.lax1dude.eaglercraft.opengl.EaglercraftGPU;

/**
 * Replaces the system section of a crash report.
 *
 * Vanilla fills it from things a browser tab does not have: OSHI for CPU model and core
 * count, ManagementFactory for JVM flags and heap pools, java.awt.Toolkit for the display,
 * and the JFR profiler for the recording state. Reaching for those is what put oshi, JNA,
 * java.lang.management and - through Toolkit - Swing and java2d into the dependency graph,
 * 709 diagnostics' worth.
 *
 * What replaces them is the information a crash report from the browser can actually use:
 * the EaglercraftX and Minecraft versions, the user agent, the GL vendor and renderer, and
 * TeaVM's view of the heap. The report format is unchanged, so an uploaded crash log still
 * reads the way lax1dude's tooling expects.
 */
public class SystemReport {

	public static final long BYTES_PER_MEBIBYTE = 1048576L;

	private final Map<String, String> entries = new LinkedHashMap<>();

	public SystemReport() {
		setDetail("Minecraft Version", SharedConstants.getCurrentVersion().getName());
		setDetail("Minecraft Version ID", SharedConstants.getCurrentVersion().getId());
		setDetail("EaglercraftX Version", EaglercraftVersion.projectForkVersion);
		setDetail("Operating System", "Browser");
		setDetail("User Agent", EagRuntime::getUserAgentString);
		setDetail("Runtime", EagRuntime::getVersion);
		setDetail("Memory", () -> {
			long max = EagRuntime.maxMemory();
			long total = EagRuntime.totalMemory();
			long free = EagRuntime.freeMemory();
			return String.format("%d bytes (%d MiB) / %d bytes (%d MiB) up to %d bytes (%d MiB)",
					free, free / BYTES_PER_MEBIBYTE, total, total / BYTES_PER_MEBIBYTE, max,
					max / BYTES_PER_MEBIBYTE);
		});
		// GL_VENDOR / GL_RENDERER / GL_VERSION - EaglercraftGPU takes the raw enum, since
		// it forwards to WebGL rather than to LWJGL's named wrappers.
		setDetail("Graphics Vendor", () -> EaglercraftGPU.glGetString(7936));
		setDetail("Graphics Renderer", () -> EaglercraftGPU.glGetString(7937));
		setDetail("Graphics Version", () -> EaglercraftGPU.glGetString(7938));
	}

	public void setDetail(String name, String value) {
		entries.put(name, value);
	}

	/**
	 * Vanilla resolves the supplier immediately and records the exception text if it
	 * throws, so that one broken probe cannot take the whole crash report with it. The
	 * same holds here, where a GL call can fail if the context is already lost.
	 */
	public void setDetail(String name, Supplier<String> value) {
		try {
			entries.put(name, value.get());
		} catch (Throwable t) {
			entries.put(name, "ERR " + t.getClass().getSimpleName() + ": " + t.getMessage());
		}
	}

	public void appendToCrashReportString(StringBuilder sb) {
		for (Map.Entry<String, String> e : entries.entrySet()) {
			sb.append("\t").append(e.getKey()).append(": ").append(e.getValue()).append("\n");
		}
	}

	public String toLineSeparatedString() {
		StringBuilder sb = new StringBuilder();
		for (Map.Entry<String, String> e : entries.entrySet()) {
			if (sb.length() > 0) {
				sb.append("\n");
			}
			sb.append(e.getKey()).append(": ").append(e.getValue());
		}
		return sb.toString();
	}
}
