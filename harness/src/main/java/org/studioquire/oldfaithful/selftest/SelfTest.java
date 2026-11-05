package org.studioquire.oldfaithful.selftest;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.zip.Deflater;

import net.fabricmc.api.DedicatedServerModInitializer;
import net.fabricmc.loader.api.entrypoint.PreLaunchEntrypoint;
import net.minecraft.network.packet.s2c.play.ChunkDataS2CPacket;
import net.minecraft.network.packet.s2c.play.ExplosionS2CPacket;
import org.spongepowered.asm.mixin.MixinEnvironment;
import org.studioquire.oldfaithful.hardening.log.GameLog;

/**
 * Test-only mod, never shipped: checks from inside the real game runtime that the hardening mixins applied and
 * behave. On the dedicated server it runs at startup; for the client it runs before the game starts (no window) when
 * {@code -Doldfaithful.selftest.client=true} is set, then exits. Results are printed as {@code [oldfaithful-selftest]} lines that
 * the harness reads.
 */
public final class SelfTest implements DedicatedServerModInitializer, PreLaunchEntrypoint {
	private int pass;
	private int fail;
	private String owner;

	@Override
	public void onInitializeServer() {
		auditMixins();
		packetChecks();
		new NetChecks(this).run(false);
		done();
	}

	@Override
	public void onPreLaunch() {
		if (!Boolean.getBoolean("oldfaithful.selftest.client")) return;
		// Our log and console redaction first (entrypoint order isn't fixed), then print the session the harness
		// launched with: the task checks it never appears, in the console or in logs/purefaithful/latest.log.
		GameLog.install();
		String[] args = net.fabricmc.loader.api.FabricLoader.getInstance().getLaunchArguments(false);
		for (int i = 0; i + 1 < args.length; i++) {
			if (args[i].equals("--session")) System.out.println("[oldfaithful-selftest] session as launched: " + args[i + 1]);
		}
		auditMixins();
		packetChecks();
		new NetChecks(this).run(true);
		new ContentChecks(this).run();
		new ClientChecks(this).run();
		done();
		System.exit(fail == 0 ? 0 : 1);
	}

	/** Force-loads every class our mixins target, applying them; a broken injection point fails here. */
	private void auditMixins() {
		try {
			MixinEnvironment.getCurrentEnvironment().audit();
			pass("mixin audit");
		} catch (Throwable t) {
			fail("mixin audit", t);
		}
	}

	private void packetChecks() {
		// A whole chunk, as the game sends it, must still read.
		try {
			byte[] raw = new byte[16 * 128 * 16 * 5 / 2];
			byte[] compressed = deflate(raw);
			ChunkDataS2CPacket packet = new ChunkDataS2CPacket();
			packet.read(chunkPacket(16, 128, 16, compressed.length, compressed));
			check("chunk packet: genuine chunk reads", packet.chunkData.length == raw.length);
		} catch (Throwable t) {
			fail("chunk packet: genuine chunk reads", t);
		}

		// A 2 GB length must be refused before anything is allocated (unpatched, this is an OutOfMemoryError).
		expectRefused("chunk packet: huge length refused", "impossible", () ->
				new ChunkDataS2CPacket().read(chunkPacket(16, 128, 16, Integer.MAX_VALUE, new byte[0])));
		expectRefused("chunk packet: negative length refused", "impossible", () ->
				new ChunkDataS2CPacket().read(chunkPacket(16, 128, 16, -1, new byte[0])));

		try {
			ExplosionS2CPacket packet = new ExplosionS2CPacket();
			packet.read(explosionPacket(3));
			check("explosion packet: genuine explosion reads", packet.affectedBlocks.size() == 3);
		} catch (Throwable t) {
			fail("explosion packet: genuine explosion reads", t);
		}
		expectRefused("explosion packet: huge count refused", "Explosion lists", () -> new ExplosionS2CPacket().read(explosionPacket(Integer.MAX_VALUE)));
	}

	private interface Read {
		void run() throws Exception;
	}

	/** The read must fail with our own message: any other failure (EOF, OutOfMemoryError) means the guard didn't run. */
	private void expectRefused(String name, String message, Read read) {
		try {
			read.run();
			fail(name, new AssertionError("was accepted"));
		} catch (IOException e) {
			if (String.valueOf(e.getMessage()).contains(message)) pass(name + " (" + e.getMessage() + ")");
			else fail(name, e);
		} catch (Throwable t) {
			fail(name, t);
		}
	}

	private static DataInputStream chunkPacket(int sx, int sy, int sz, int length, byte[] data) throws IOException {
		ByteArrayOutputStream bytes = new ByteArrayOutputStream();
		DataOutputStream out = new DataOutputStream(bytes);
		out.writeInt(0);
		out.writeShort(0);
		out.writeInt(0);
		out.write(sx - 1);
		out.write(sy - 1);
		out.write(sz - 1);
		out.writeInt(length);
		out.write(data);
		return new DataInputStream(new ByteArrayInputStream(bytes.toByteArray()));
	}

	private static DataInputStream explosionPacket(int count) throws IOException {
		ByteArrayOutputStream bytes = new ByteArrayOutputStream();
		DataOutputStream out = new DataOutputStream(bytes);
		out.writeDouble(0);
		out.writeDouble(64);
		out.writeDouble(0);
		out.writeFloat(4);
		out.writeInt(count);
		for (int i = 0; i < Math.min(count, 3); i++) {
			out.writeByte(i);
			out.writeByte(0);
			out.writeByte(0);
		}
		return new DataInputStream(new ByteArrayInputStream(bytes.toByteArray()));
	}

	private static byte[] deflate(byte[] raw) {
		Deflater deflater = new Deflater();
		deflater.setInput(raw);
		deflater.finish();
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		byte[] buf = new byte[4096];
		while (!deflater.finished()) out.write(buf, 0, deflater.deflate(buf));
		deflater.end();
		return out.toByteArray();
	}

	public void check(String name, boolean ok) {
		if (ok) pass(name);
		else fail(name, new AssertionError("check failed"));
	}

	/** While a library mod's test runs, whose it is: the harness then reads its results under that mod's name. Null when it's over. */
	public void owner(String mod) {
		owner = mod;
	}

	public String owner() {
		return owner;
	}

	public void pass(String name) {
		pass++;
		System.out.println(owner == null ? "[oldfaithful-selftest] PASS " + name : "[faithful-test] PASS " + owner + " | " + name);
	}

	public void fail(String name, Throwable t) {
		if (owner != null) {
			System.out.println("[faithful-test] FAIL " + owner + " | " + name + (t instanceof AssertionError ? "" : ": " + t));
			for (StackTraceElement frame : t.getStackTrace()) {
				if (!(t instanceof AssertionError)) System.out.println("[faithful-test]   " + frame);
			}
			return;
		}
		fail++;
		System.out.println("[oldfaithful-selftest] FAIL " + name + ": " + t);
		t.printStackTrace(System.out);
	}

	void done() {
		System.out.println("[oldfaithful-selftest] DONE pass=" + pass + " fail=" + fail);
	}
}
