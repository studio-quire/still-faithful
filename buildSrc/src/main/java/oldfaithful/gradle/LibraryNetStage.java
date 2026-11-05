package oldfaithful.gradle;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.function.UnaryOperator;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import oldfaithful.gradle.LibraryTestTask.Stage;

/**
 * Stage 6: who's let in and who isn't, on a real client with the screen it ends on; the hello and every channel fuzzed against the server; and a hostile server against
 * a real client. A fuzzed peer may be disconnected, as a handler's throw does: what counts is that the other end stays up, answers, and says so plainly.
 */
final class LibraryNetStage implements Stage {
	private static final String MATCH = "harness-match", EXTRA = "harness-extra";
	private static final java.util.regex.Pattern OVERRAN = java.util.regex.Pattern.compile("\\[faithful-core] WARN The handler on ([^:\\s]+):(\\S+) overran on a message: (.*)");
	private final LibraryTestTask t;
	private int connections;

	LibraryNetStage(LibraryTestTask t) {
		this.t = t;
	}

	private static Path matchJar(Path dir, String id, int network) throws IOException {
		Path jar = Files.createDirectories(dir).resolve(id + "-" + network + ".jar");
		try (OutputStream file = Files.newOutputStream(jar); ZipOutputStream zip = new ZipOutputStream(file)) {
			zip.putNextEntry(new ZipEntry("fabric.mod.json"));
			zip.write(("{\"schemaVersion\":1,\"id\":\"" + id + "\",\"version\":\"" + network + "\",\"custom\":{\"faithful\":{\"match\":true,\"network\":" + network + "}}}").getBytes("UTF-8"));
			zip.closeEntry();
		}
		return jar;
	}

	private static List<Path> plus(List<Path> jars, Path... more) {
		List<Path> all = new ArrayList<>(jars);
		all.addAll(Arrays.asList(more));
		return all;
	}

	@Override
	public void run() throws Exception {
		Path dir = t.run.resolve(t.stage);
		Path jars = dir.resolve("jars");
		Path match = matchJar(jars, MATCH, 1), other = matchJar(jars, MATCH, 2), extra = matchJar(jars, EXTRA, 1);
		byte[] hello;
		List<Map.Entry<Integer, String>> channels;
		boolean content;
		try (HarnessServer server = new HarnessServer(t, dir.resolve("server"))) {
			server.mods(plus(t.serverMods(t.subjects, true), match));
			if (!server.start(t.probeFlags(dir.resolve("out")))) {
				t.fail("harness", "failed-to-start", "the multiplayer server");
				t.scan("server: multiplayer", server.stop(null));
				return;
			}
			refusals(server, match, other, extra);
			try (RawClient first = source(server.port, "Hello").core(true)) {
				first.login(30);
				hello = first.helloBytes();
				channels = new ArrayList<>(first.helloChannels().entrySet());
				content = first.serverHasContent();
			}
			fuzzHello(server);
			fuzzChannels(server, channels);
			scanHostile("server: fuzzed", server.stop(null));
		}
		hostileServer(dir, match, hello, channels, content);
	}

	/** For planted mistakes: only the channels. */
	void fuzzOnly() throws Exception {
		Path dir = t.run.resolve(t.stage);
		try (HarnessServer server = new HarnessServer(t, dir.resolve("server"))) {
			server.mods(t.serverMods(t.subjects, true));
			if (server.start(t.probeFlags(dir.resolve("out")))) {
				List<Map.Entry<Integer, String>> channels;
				try (RawClient first = source(server.port, "Hello").core(true)) {
					first.login(30);
					channels = new ArrayList<>(first.helloChannels().entrySet());
				}
				fuzzChannels(server, channels);
			}
			scanHostile("server: fuzzed", server.stop(null));
		}
	}

	/** Stage 7: the bot on a real client and server, then each mod's own scenario on the server (single player's ran in the earlier stages' clients). */
	static final class MonkeyStage implements Stage {
		private final LibraryTestTask t;

		MonkeyStage(LibraryTestTask t) {
			this.t = t;
		}

		@Override
		public void run() throws Exception {
			Path dir = t.run.resolve(t.stage);
			try (HarnessServer server = new HarnessServer(t, dir.resolve("server"))) {
				server.mods(t.serverMods(t.subjects, true));
				List<String> jvm = new ArrayList<>(t.probeFlags(dir.resolve("out")));
				jvm.add("-Dfaithful.harness.monkey=true");
				if (!server.start(jvm)) {
					t.fail("harness", "failed-to-start", "the monkey's server");
				} else {
					try (RawClient keeper = new RawClient(server.port, "Keeper").core(true)) {
						if (keeper.login(30) == null) {
							keeper.stand(3, 100);
							server.scene("hprobe scenarios Keeper");
						}
					}
					Thread.sleep(5500);
					Path game = dir.resolve("client");
					Files.createDirectories(game);
					List<String> client = new ArrayList<>(t.probeFlags(game.resolve("out")));
					client.addAll(List.of("-Doldfaithful.selftest.joinServer=127.0.0.1:" + server.port, "-Doldfaithful.selftest.joinWorldChecks=true", "-Dfaithful.harness.monkey=true",
							"-Doldfaithful.selftest.stallMs=" + (t.getTimeoutSeconds().get() - 20) * 1000L));
					t.client("the monkey", game, t.clientMods(t.subjects, true), client);
				}
				t.scan("server: the monkey", server.stop(null));
			}
		}
	}

	/** Each connection from an address of its own, past the server's five seconds between logins from one. */
	private RawClient source(int port, String name) throws IOException {
		int n = ++connections;
		return new RawClient(port, name, "127.0." + (n / 250 + 1) + "." + (n % 250 + 1));
	}

	private String tryServer(String label, int port, List<Path> jars) throws Exception {
		Path game = t.run.resolve(t.stage).resolve(label);
		Files.createDirectories(game);
		List<String> jvm = List.of("-Doldfaithful.selftest.tryServer=127.0.0.1:" + port, "-Doldfaithful.selftest.recordText=true");
		List<String> lines = t.quietClient(label, game, jars, jvm);
		for (String line : lines) {
			if (line.startsWith("[library-harness] SCREEN ")) return line.substring("[library-harness] SCREEN ".length());
		}
		return "no screen";
	}

	private void expect(String label, String shown, String... words) {
		t.getLogger().lifecycle("{}: {}", label, shown);
		for (String word : words) {
			if (!shown.contains(word)) t.fail("faithful-core", "refusal", label + ": expected \"" + word + "\", the screen said: " + shown);
		}
	}

	private void refusals(HarnessServer server, Path match, Path other, Path extra) throws Exception {
		int port = server.port;
		List<Path> full = plus(t.clientMods(t.subjects, true), match);
		expect("the same mods", tryServer("same", port, full), "admitted");
		Thread.sleep(5500);
		expect("a client missing a mod", tryServer("missing", port, t.clientMods(t.subjects, true)), "MismatchScreen", "Missing: " + MATCH);
		Thread.sleep(5500);
		expect("a client with an extra", tryServer("extra", port, plus(full, extra)), "MismatchScreen", "Extra: " + EXTRA);
		Thread.sleep(5500);
		expect("a different network version", tryServer("network", port, plus(t.clientMods(t.subjects, true), other)), "MismatchScreen", "Different: " + MATCH);
		Thread.sleep(5500);
		List<Path> vanilla = new ArrayList<>();
		t.getMods().getFiles().forEach(f -> {
			String name = f.getName();
			if (!name.startsWith("faithful-core") && !name.startsWith("oldfaithful-settings")) vanilla.add(f.toPath());
		});
		expect("a client with no core", tryServer("vanilla", port, vanilla), "Faithful Library");
		Thread.sleep(5500);
	}

	private static byte[] section(int id, byte[] body, int claimed) {
		return RawServer.bytes(out -> {
			try {
				out.writeByte(id);
				out.writeInt(claimed);
				out.write(body);
			} catch (IOException e) {
				throw new IllegalStateException(e);
			}
		});
	}

	/** What can be wrong with a hello or its reply, shape by shape. */
	private List<UnaryOperator<byte[]>> helloShapes(int kind) {
		Random random = new Random(t.getSeed().get());
		byte[] noise = new byte[64];
		random.nextBytes(noise);
		byte[] head = {(byte) kind, 0, 1, 1};
		List<UnaryOperator<byte[]>> shapes = new ArrayList<>();
		shapes.add(good -> new byte[0]);
		shapes.add(good -> new byte[1]);
		shapes.add(good -> Arrays.copyOf(good, good.length / 2));
		shapes.add(good -> Arrays.copyOf(good, Math.max(0, good.length - 1)));
		shapes.add(good -> noise);
		shapes.add(good -> concat(head, section(1, new byte[0], 1 << 30)));
		shapes.add(good -> concat(head, section(1, new byte[0], -1)));
		shapes.add(good -> concat(head, section(1, new byte[] {(byte) 0xFF, (byte) 0xFF}, 2)));
		shapes.add(good -> concat(head, section(2, new byte[] {(byte) 0xFF, (byte) 0xFF}, 2)));
		shapes.add(good -> concat(head, section(3, new byte[] {1, 1, 0x7F, (byte) 0xFF}, 4)));
		shapes.add(good -> concat(head, section(4, new byte[] {(byte) 0xFF, (byte) 0xFF}, 2)));
		shapes.add(good -> concat(head, section(99, new byte[8], 8)));
		return shapes;
	}

	private static byte[] concat(byte[] a, byte[] b) {
		byte[] out = Arrays.copyOf(a, a.length + b.length);
		System.arraycopy(b, 0, out, a.length, b.length);
		return out;
	}

	private void fuzzHello(HarnessServer server) throws Exception {
		try (RawClient honest = source(server.port, "Honest").core(true)) {
			if (honest.login(30) != null) t.fail("harness", "fuzz", "the honest client was refused");
			int n = 0;
			for (UnaryOperator<byte[]> shape : helloShapes(2)) {
				n++;
				try (RawClient bad = source(server.port, "BadReply" + n).core(true)) {
					bad.replyShape(shape);
					try {
						bad.login(3);
					} catch (IOException e) {
						// Held at the door, or dropped: either will do.
					}
				}
			}
			try (RawClient before = source(server.port, "Early")) {
				before.sendChannel(RawClient.CORE_PREFIX, 1, 3, new byte[3]);
				before.sendChannel(RawClient.CORE_PREFIX, 0, 3, new byte[3]);
				before.stand0(300);
			}
			if (!server.answers(15)) t.fail("faithful-core", "fuzz-hello", "the server stopped answering after a hostile hello");
			if (honest.isClosed()) t.fail("faithful-core", "fuzz-hello", "a bystander was dropped by a hostile hello");
		}
	}

	private static List<byte[]> messageShapes(Random random) {
		List<byte[]> shapes = new ArrayList<>();
		shapes.add(new byte[0]);
		shapes.add(new byte[1]);
		shapes.add(new byte[64]);
		byte[] ones = new byte[8];
		Arrays.fill(ones, (byte) 0xFF);
		shapes.add(ones);
		// A length of two thousand million, as a first int, short or UTF length.
		byte[] large = new byte[64];
		Arrays.fill(large, (byte) 0x7F);
		shapes.add(large);
		for (int size : new int[] {4, 16, 256, 32 * 1024}) {
			byte[] noise = new byte[size];
			random.nextBytes(noise);
			shapes.add(noise);
		}
		return shapes;
	}

	private void fuzzChannels(HarnessServer server, List<Map.Entry<Integer, String>> channels) throws Exception {
		Random random = new Random(t.getSeed().get());
		try (RawClient honest = source(server.port, "Bystander").core(true)) {
			if (honest.login(30) != null) t.fail("harness", "fuzz", "the bystander was refused");
			RawClient bad = null;
			int n = 0;
			try {
				for (Map.Entry<Integer, String> channel : channels) {
					String mod = channel.getValue().substring(0, channel.getValue().indexOf(':'));
					for (byte[] message : messageShapes(random)) {
						if (bad == null || bad.isClosed()) {
							if (bad != null) bad.close();
							bad = source(server.port, "Fuzz" + ++n).core(true);
							if (bad.login(15) != null) throw new IOException("the fuzzing client was refused");
						}
						bad.sendChannel(RawClient.CORE_PREFIX, channel.getKey(), message.length, message);
						Thread.sleep(30);
					}
					if (!server.answers(15)) {
						t.fail(mod, "channel-hangs-server", "after hostile messages on " + channel.getValue() + " the server stopped answering");
						return;
					}
					if (honest.isClosed()) {
						t.fail(mod, "channel-drops-bystander", "hostile messages on " + channel.getValue() + " dropped another player");
						return;
					}
				}
			} finally {
				if (bad != null) bad.close();
			}
			t.getLogger().lifecycle("{} channels fuzzed, {} connections", channels.size(), n);
		}
	}

	/** A run under attack may log what it refused; what it may not do is fall over. */
	private void scanHostile(String where, List<String> lines) {
		for (String line : lines) {
			java.util.regex.Matcher overran = OVERRAN.matcher(line);
			if (overran.find()) {
				t.fail(overran.group(1), "channel-handler-overran", overran.group(1) + ":" + overran.group(2) + ": " + overran.group(3) + ": check a length before allocating by it");
			} else if (line.contains("Unexpected exception") || line.contains("Exception in thread") || line.contains("A fatal error") || line.contains("FatalErrorScreen")) {
				t.fail("unknown", "crash", line.trim() + " [" + where + "]");
			}
		}
	}

	private static RawServer.Attack garbage(int channel, byte[] data) {
		return out -> RawServer.channel(out, channel, data);
	}

	private List<RawServer.Case> cases(byte[] hello, List<Map.Entry<Integer, String>> channels, boolean content) {
		List<RawServer.Case> cases = new ArrayList<>();
		for (UnaryOperator<byte[]> shape : helloShapes(1)) {
			byte[] bad = shape.apply(hello);
			cases.add(new RawServer.Case("hello " + cases.size(), false, bad, null));
		}
		cases.add(new RawServer.Case("a 2 GB channel message", true, null, out -> {
			out.write(250);
			out.writeInt(RawClient.CORE_PREFIX);
			out.writeShort(1);
			out.writeInt(Integer.MAX_VALUE);
		}));
		cases.add(new RawServer.Case("a negative channel message", true, null, out -> {
			out.write(250);
			out.writeInt(RawClient.CORE_PREFIX);
			out.writeShort(1);
			out.writeInt(-1);
		}));
		cases.add(new RawServer.Case("a stranger's 250", true, null, out -> {
			out.write(250);
			out.writeInt(0x12345678);
			out.writeShort(1);
			out.writeInt(0);
		}));
		cases.add(new RawServer.Case("an unknown channel", false, null, garbage(60000, new byte[4])));
		Random random = new Random(t.getSeed().get());
		for (Map.Entry<Integer, String> channel : channels) {
			if (channel.getValue().startsWith("harness-probe:")) continue;
			List<byte[]> shapes = messageShapes(random);
			// A case that's survived takes seconds, so fewer than to a server: nothing, the longest of lengths, noise.
			for (byte[] message : List.of(shapes.get(0), shapes.get(4), shapes.get(7))) {
				cases.add(new RawServer.Case(channel.getValue() + " with " + message.length + " bytes", false, null, garbage(channel.getKey(), message)));
			}
		}
		if (content) {
			byte[] unknownId = new byte[RawServer.Wire.WHOLE];
			unknownId[1000 * 2] = 0x0F;
			unknownId[1000 * 2 + 1] = (byte) 0xFF;
			cases.add(new RawServer.Case("an overlay id nothing here has", true, null, out -> RawServer.chunk(out, 0, 0, unknownId)));
			byte[] vanillaId = new byte[RawServer.Wire.WHOLE];
			vanillaId[1000 * 2 + 1] = 1;
			cases.add(new RawServer.Case("an overlay id of vanilla's", true, null, out -> RawServer.chunk(out, 0, 0, vanillaId)));
			cases.add(new RawServer.Case("a short tail", false, null, out -> RawServer.chunk(out, 0, 0, new byte[1001])));
			cases.add(new RawServer.Case("a long tail", false, null, out -> RawServer.chunk(out, 0, 0, new byte[RawServer.Wire.WHOLE + 4096])));
			cases.add(new RawServer.Case("a block update of nothing", true, null, out -> {
				out.write(53);
				out.writeInt(1);
				out.writeByte(90);
				out.writeInt(1);
				out.writeByte(0xFF);
				out.writeByte(0xF0);
			}));
			cases.add(new RawServer.Case("a multi-block update of nothing", true, null, out -> {
				out.write(52);
				out.writeInt(0);
				out.writeInt(0);
				out.writeShort(1);
				out.writeShort(0);
				out.writeByte(0xFF);
				out.writeByte(0xF0);
			}));
			cases.add(new RawServer.Case("a stack with a 64 KB claim", true, null, out -> {
				out.write(103);
				out.writeByte(0);
				out.writeShort(36);
				out.writeShort(1);
				out.writeByte(1);
				out.writeShort(0);
				out.writeShort(0xFFFE); // 0xFFFF is the mark of data withheld
			}));
			cases.add(new RawServer.Case("a stack with noise for data", true, null, out -> {
				out.write(103);
				out.writeByte(0);
				out.writeShort(36);
				out.writeShort(1);
				out.writeByte(1);
				out.writeShort(0);
				out.writeShort(16);
				out.write(new byte[16]);
			}));
		}
		return cases;
	}

	private void hostileServer(Path dir, Path match, byte[] hello, List<Map.Entry<Integer, String>> channels, boolean content) throws Exception {
		List<RawServer.Case> cases = cases(hello, channels, content);
		Path game = dir.resolve("hostile");
		Files.createDirectories(game);
		try (RawServer raw = new RawServer(hello)) {
			Thread clientThread;
			List<String> jvm = List.of("-Doldfaithful.selftest.hostile=127.0.0.1:" + raw.port(), "-Doldfaithful.selftest.recordText=true", "-Doldfaithful.selftest.stallMs=400000");
			Exception[] thrown = {null};
			List<List<String>> lines = new ArrayList<>();
			clientThread = new Thread(() -> {
				try {
					lines.add(t.quietClient("hostile server", game, plus(t.clientMods(t.subjects, true), match), jvm));
				} catch (Exception e) {
					thrown[0] = e;
				}
			}, "hostile-client");
			clientThread.start();
			raw.play(cases, (name, left, must) -> {
				t.getLogger().lifecycle("hostile: {}: the client {}", name, left ? "left" : "stayed");
				if (must && !left) t.fail("faithful-core", "hostile-server", name + ": the client should have disconnected");
			});
			clientThread.join(120_000);
			if (thrown[0] != null) throw thrown[0];
			if (clientThread.isAlive()) t.fail("faithful-core", "hostile-server", "the client didn't stop after the last case");
			else scanHostile("client: hostile server", lines.get(0));
		}
	}
}
