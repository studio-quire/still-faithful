package oldfaithful.gradle;

import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import javax.imageio.ImageIO;

import oldfaithful.gradle.LibraryTestTask.Stage;

/**
 * Stage 8, {@code coreInertTest}: the core with nothing using it changes nothing. A fixed seed's chunks byte for byte, and a picture of the world pixel for pixel, against the
 * same without the core. Each is run twice without it first, because a game has its own chance, and what differs between those two is the game's and not the core's.
 * Then a client without the core on a server with it, and one with it on the real, unmodified server.
 */
final class LibraryInertStage implements Stage {
	private static final long SEED = 7373L;
	private final LibraryTestTask t;

	LibraryInertStage(LibraryTestTask t) {
		this.t = t;
	}

	private List<Path> plainServer() {
		List<Path> jars = new ArrayList<>();
		for (File f : t.getServerBase().getFiles()) {
			if (!isCore(f)) jars.add(f.toPath());
		}
		return jars;
	}

	private List<Path> plainClient() {
		List<Path> jars = new ArrayList<>();
		for (File f : t.getMods().getFiles()) {
			if (!isCore(f)) jars.add(f.toPath());
		}
		return jars;
	}

	private static boolean isCore(File f) {
		return f.getName().startsWith("faithful-core") || f.getName().startsWith("oldfaithful-settings");
	}

	private List<Path> withSelfTest(List<Path> jars) {
		List<Path> all = new ArrayList<>(jars);
		for (File f : t.getMods().getFiles()) {
			if (f.getName().startsWith("harness-")) all.add(f.toPath());
		}
		return all;
	}

	@Override
	public void run() throws Exception {
		chunks();
		pictures();
		peers();
	}

	private Map<String, byte[]> world(String label, List<Path> mods) throws Exception {
		Path dir = t.run.resolve(t.stage).resolve(label);
		try (HarnessServer server = new HarnessServer(t, dir)) {
			Files.writeString(dir.resolve("server.properties"), Files.readString(dir.resolve("server.properties")) + "level-seed=" + SEED + "\n");
			server.mods(withSelfTest(mods));
			// It saves and stops before its first tick: everything after that is chance.
			server.startRefused(List.of("-Doldfaithful.selftest.fixedSpawn=" + SEED, "-Doldfaithful.selftest.stopAtStart=true"), l -> l.contains("Stopping server"));
			Integer[] exit = {null};
			List<String> lines = server.waitStopped(exit);
			if (exit[0] == null) t.fail("faithful-core", "inert", label + ": the server didn't stop by itself");
			for (String p : LibraryTestTask.problemsOf(lines)) t.fail("faithful-core", "inert", label + ": " + p);
		}
		return RegionFiles.read(dir.resolve("world"));
	}

	private void chunks() throws Exception {
		Map<String, byte[]> first = world("plain-1", plainServer());
		Map<String, byte[]> second = world("plain-2", plainServer());
		Map<String, byte[]> core = world("core", t.serverMods(List.of(), false));
		if (first.isEmpty()) {
			t.fail("harness", "inert", "no chunks were saved");
			return;
		}
		int stable = 0, differ = 0;
		List<String> names = new ArrayList<>();
		for (Map.Entry<String, byte[]> e : first.entrySet()) {
			if (!Arrays.equals(e.getValue(), second.get(e.getKey()))) continue;
			stable++;
			if (!Arrays.equals(e.getValue(), core.get(e.getKey()))) {
				differ++;
				names.add(e.getKey());
			}
		}
		t.getLogger().lifecycle("{} chunks, {} the same on two plain runs, {} differ with the core", first.size(), stable, differ);
		if (stable < first.size() * 9 / 10) t.fail("harness", "inert", "only " + stable + " of " + first.size() + " chunks repeat without the core: nothing can be concluded");
		if (differ > 0) t.fail("faithful-core", "inert-chunks", differ + " chunks differ with the core, first " + names.subList(0, Math.min(5, names.size())));
		if (first.size() != core.size()) t.fail("faithful-core", "inert-chunks", first.size() + " chunks without the core, " + core.size() + " with it");
	}

	private static final int NOISE_REACH = 3, MORE_PLAIN_RUNS = 3;

	private Path scene(String label, List<Path> mods) throws Exception {
		Path game = t.run.resolve(t.stage).resolve(label);
		Files.createDirectories(game);
		t.quietClient("scene " + label, game, mods, List.of("-Doldfaithful.selftest.scene=" + game.resolve("shots"), "-Doldfaithful.selftest.sceneSeed=" + SEED, "-Doldfaithful.selftest.fixedSpawn=" + SEED,
				"-Doldfaithful.selftest.sceneShots=2", "-Doldfaithful.selftest.sceneRadius=48", "-Doldfaithful.selftest.sceneStill=true"));
		return game.resolve("shots");
	}

	private static List<Path> pictures(Path dir) throws IOException {
		if (!Files.isDirectory(dir)) return List.of();
		try (Stream<Path> list = Files.list(dir)) {
			return list.filter(p -> p.toString().endsWith(".png")).sorted().toList();
		}
	}

	private void pictures() throws Exception {
		Path first = scene("scene-plain-1", plainClient()), second = scene("scene-plain-2", plainClient()), core = scene("scene-core", t.clientMods(List.of(), false));
		List<List<Path>> plain = new ArrayList<>(List.of(pictures(first), pictures(second)));
		List<Path> c = pictures(core);
		if (plain.get(0).isEmpty() || plain.get(0).size() != plain.get(1).size()) {
			t.fail("harness", "inert", "the plain runs gave " + plain.get(0).size() + " and " + plain.get(1).size() + " pictures: nothing can be concluded");
			return;
		}
		if (plain.get(0).size() != c.size()) {
			t.fail("faithful-core", "inert-pictures", "without the core " + plain.get(0).size() + " pictures, with it " + c.size());
			return;
		}
		// Two plain runs can agree by luck where a flow of lava stands differently from run to run (2026-11-05): a difference has to outlast more of them.
		for (int more = 0; !compare(plain, c, more == MORE_PLAIN_RUNS); more++) plain.add(pictures(scene("scene-plain-" + (3 + more), plainClient())));
	}

	/** True if no steady pixel differs with the core; with {@code last}, says so as a failure if one does. */
	private boolean compare(List<List<Path>> plain, List<Path> core, boolean last) throws IOException {
		boolean same = true;
		for (int i = 0; i < core.size(); i++) {
			List<BufferedImage> runs = new ArrayList<>();
			for (List<Path> run : plain) runs.add(ImageIO.read(run.get(i).toFile()));
			BufferedImage one = runs.get(0), three = ImageIO.read(core.get(i).toFile());
			int noise = 0, masked = 0, differ = 0, w = one.getWidth(), h = one.getHeight();
			boolean[][] varies = new boolean[h][w];
			for (int y = 0; y < h; y++) {
				for (int x = 0; x < w; x++) {
					boolean steady = true;
					for (BufferedImage run : runs) steady &= run.getRGB(x, y) == one.getRGB(x, y);
					if (steady) continue;
					noise++;
					// A pixel beside ones that vary is varying too.
					for (int dy = Math.max(0, y - NOISE_REACH); dy <= Math.min(h - 1, y + NOISE_REACH); dy++) {
						for (int dx = Math.max(0, x - NOISE_REACH); dx <= Math.min(w - 1, x + NOISE_REACH); dx++) varies[dy][dx] = true;
					}
				}
			}
			for (int y = 0; y < h; y++) {
				for (int x = 0; x < w; x++) {
					if (varies[y][x]) masked++;
					else if (one.getRGB(x, y) != three.getRGB(x, y)) differ++;
				}
			}
			String name = core.get(i).getFileName().toString();
			t.getLogger().lifecycle("{}: {} pixels vary by themselves over {} plain runs, {} set aside with their neighbours, {} more with the core", name, noise, runs.size(), masked, differ);
			if (masked > w * h / 4) t.fail("harness", "inert", name + ": plain runs leave " + masked + " pixels unusable: nothing can be concluded");
			if (differ > 0) {
				same = false;
				if (last) t.fail("faithful-core", "inert-pictures", name + " differs in " + differ + " steady pixels with the core");
			}
		}
		return same || last;
	}

	private void peers() throws Exception {
		Path dir = t.run.resolve(t.stage).resolve("peers");
		try (HarnessServer server = new HarnessServer(t, dir.resolve("core-server"))) {
			server.mods(t.serverMods(List.of(), false));
			if (server.start(List.of())) {
				try (RawClient raw = new RawClient(server.port, "PlainRaw")) {
					if (raw.login(30) != null) t.fail("faithful-core", "inert-peers", "a vanilla client was refused by a server with only the core");
					raw.stand(5, 50);
					if (raw.await(r -> r.id() == 250, 0.5) != null) t.fail("faithful-core", "inert-peers", "a vanilla client was sent a 250");
				}
				Thread.sleep(5500);
				Path game = dir.resolve("plain-client");
				Files.createDirectories(game);
				t.client("a client without the core on a server with it", game, plainClient(), List.of("-Doldfaithful.selftest.joinServer=127.0.0.1:" + server.port, "-Doldfaithful.selftest.joinWorldChecks=true"));
			} else {
				t.fail("faithful-core", "inert-peers", "a server with only the core didn't start");
			}
			t.scan("server: core only", server.stop(null));
		}
		Path plain = dir.resolve("real-server");
		Files.createDirectories(plain);
		Files.copy(t.getServerJar().get().getAsFile().toPath(), plain.resolve("minecraft_server.jar"));
		int port;
		try (java.net.ServerSocket socket = new java.net.ServerSocket(0)) {
			port = socket.getLocalPort();
		}
		Files.writeString(plain.resolve("server.properties"), String.join("\n", "level-name=world", "online-mode=false", "server-ip=127.0.0.1", "server-port=" + port, "view-distance=3", "spawn-monsters=false", "spawn-animals=false", ""));
		try (GameProcess vanilla = new GameProcess(List.of(t.getJavaExecutable().get(), "-Xmx512m", "-jar", "minecraft_server.jar", "nogui"), plain, plain.resolve("harness-output.log"), Map.of())) {
			if (vanilla.await(l -> l.contains("Done ("), 120) == null) {
				t.fail("harness", "inert-peers", "the real server didn't start");
				return;
			}
			Path game = dir.resolve("core-client");
			Files.createDirectories(game);
			t.client("a client with the core on the real server", game, t.clientMods(List.of(), false), List.of("-Doldfaithful.selftest.joinServer=127.0.0.1:" + port, "-Doldfaithful.selftest.joinWorldChecks=true"));
			vanilla.send("stop");
			vanilla.waitFor(60);
		}
	}
}
