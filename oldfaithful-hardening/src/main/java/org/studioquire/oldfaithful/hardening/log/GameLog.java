package org.studioquire.oldfaithful.hardening.log;

import java.io.File;
import java.io.PrintStream;
import java.io.UnsupportedEncodingException;
import java.nio.charset.Charset;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;

import net.fabricmc.api.EnvType;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.ModContainer;
import net.fabricmc.loader.api.entrypoint.PreLaunchEntrypoint;

import org.studioquire.oldfaithful.hardening.Config;

/**
 * The client's own log: from the pre-launch entrypoint on, everything printed to stdout and stderr also goes to
 * {@code <game dir>/logs/purefaithful/latest.log} (rotated, {@link LogFile}), so what happened can be read without
 * the launcher's log window. Both the console and the file pass through a {@link Redactor} first, so the session
 * token never reaches either, whatever prints it.
 */
public final class GameLog implements PreLaunchEntrypoint {
	private static boolean installed;

	@Override
	public void onPreLaunch() {
		if (FabricLoader.getInstance().getEnvironmentType() == EnvType.CLIENT) install();
	}

	/** Idempotent; the test harness calls it before its own pre-launch checks. */
	public static synchronized void install() {
		if (installed) return;
		installed = true;
		FabricLoader loader = FabricLoader.getInstance();
		Redactor redactor = Redactor.fromLaunchArguments(loader.getLaunchArguments(false));
		LogFile file = null;
		String problem = null;
		if (Config.INSTANCE.logFile) {
			File dir = loader.getGameDir().resolve("logs").resolve("purefaithful").toFile();
			try {
				file = new LogFile(dir, LogFile.MAX_BYTES);
				file.write(header(loader), null);
			} catch (Exception e) {
				problem = "Could not open the log file in " + dir + ": " + e;
			}
		}
		PrintStream out = System.out;
		PrintStream err = System.err;
		try {
			Charset outCharset = consoleCharset("stdout");
			Charset errCharset = consoleCharset("stderr");
			RedactingStream outStream = new RedactingStream(out, outCharset, file, null, redactor);
			RedactingStream errStream = new RedactingStream(err, errCharset, file, "[stderr]", redactor);
			System.setOut(new PrintStream(outStream, true, outCharset.name()));
			System.setErr(new PrintStream(errStream, true, errCharset.name()));
			Runtime.getRuntime().addShutdownHook(new Thread(() -> {
				outStream.drain();
				errStream.drain();
			}, "purefaithful log drain"));
		} catch (UnsupportedEncodingException e) {
			problem = "Console redaction not installed: " + e;
		}
		if (problem != null) System.out.println("[purefaithful-hardening] WARN " + problem);
	}

	/** The charset the JVM encodes this console stream with (Java 18+ names it; older ones use sun.* or the default). */
	private static Charset consoleCharset(String stream) {
		for (String key : new String[] {stream + ".encoding", "sun." + stream + ".encoding"}) {
			String name = System.getProperty(key);
			if (name != null && Charset.isSupported(name)) return Charset.forName(name);
		}
		return Charset.defaultCharset();
	}

	private static String header(FabricLoader loader) {
		StringBuilder b = new StringBuilder();
		b.append("Pure Faithful client log, started ").append(new SimpleDateFormat("yyyy-MM-dd HH:mm:ss Z").format(new Date())).append('\n');
		b.append("Java ").append(System.getProperty("java.version")).append(" (").append(System.getProperty("java.vendor"))
				.append(", ").append(System.getProperty("java.vm.name")).append("), ").append(System.getProperty("os.name"))
				.append(' ').append(System.getProperty("os.version")).append(' ').append(System.getProperty("os.arch")).append('\n');
		List<String> mods = new ArrayList<String>();
		for (ModContainer mod : loader.getAllMods()) {
			mods.add(mod.getMetadata().getId() + " " + mod.getMetadata().getVersion().getFriendlyString());
		}
		Collections.sort(mods);
		b.append("Mods: ").append(String.join(", ", mods)).append('\n');
		return b.toString();
	}
}
