package org.studioquire.oldfaithful.selftest;

import java.util.Locale;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screen.DisconnectedScreen;
import net.minecraft.client.input.Input;
import net.minecraft.entity.player.ClientPlayerEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.vehicle.BoatEntity;

/**
 * The hidden client riding a boat on a test server ({@link MultiplayerScenes}' "!oftest boat", in a roofed pool, the
 * boat facing west), for two checks:
 * <ul>
 * <li>{@link Kind#LOOK}, Old Faithful's #83 (`fixesMultiplayerTest`): the view turned 3 degrees a tick while riding,
 * and partway through the turn the server asked where it has the player looking. With the fix, within a tick's turn of
 * the client's look; beta's client sends no look while it changes, so the server still has the look from before the
 * turn.</li>
 * <li>{@link Kind#STEERING}, Still Faithful's Boat Steering (`fencesMultiplayerTest`): the look set and nudged a degree
 * and back (a client with #83 has then sent two riding moves with different looks, which is how a server tells it can
 * steer), the player looking south; W for 40 ticks: steered, the boat goes west, the way it faces; paddled as beta's, it
 * goes south, the way the player looks, curving as beta's boat turns the view. Then, steered only, A for 30 ticks: the
 * boat turns left by well over 90 degrees and the view with it. Positions and yaws are the boat's in this client's
 * world, as the server sent them.</li>
 * </ul>
 * With {@link #measuringFloat}, before the keys: the boat's height in this client's world over {@link #FLOAT} ticks once
 * it has settled. Where the server has Still Faithful's Boat Handling, it holds still on every client (a client draws a
 * boat where the server sends it, and never floats it itself); otherwise the client sees beta's bob, reported.
 * Then out of the boat and back where it started.
 */
public final class BoatRide {
	public static final int POOL_LENGTH = 40, POOL_WIDTH = 24;

	/** The last "oftest ..." line the server sent this client (ClientNetworkHandlerReplyMixin); null until one comes. */
	public static volatile String reply;

	public enum Kind { LOOK, STEERING }

	private static final int SETTLE = 60, FLOAT = 40;

	private final SelfTest results;
	private final String label;
	private final Kind kind;
	/** LOOK: whether the client has #83. STEERING: whether the boat should steer (both sides have what it needs). */
	private final boolean expected;
	private final int poolX, poolZ;
	private final double backX, backFeet, backZ;
	private final ScriptInput input = new ScriptInput();
	private Input savedInput;
	private int stage, ticks;
	private BoatEntity boat;
	private float yaw0, yawAsked;
	private double markX, markZ, boatTurned, viewTurned;
	private float lastBoatYaw, lastView;
	private boolean measureFloat, serverFloats;
	private double floatLow = Double.MAX_VALUE, floatHigh = -Double.MAX_VALUE;

	/** The pool's north-east inside corner at poolX poolZ; afterwards the player goes back to back*, standing. */
	public BoatRide(SelfTest results, String label, Kind kind, boolean expected, int poolX, int poolZ, double backX, double backFeet, double backZ) {
		this.results = results;
		this.label = label;
		this.kind = kind;
		this.expected = expected;
		this.poolX = poolX;
		this.poolZ = poolZ;
		this.backX = backX;
		this.backFeet = backFeet;
		this.backZ = backZ;
	}

	/** Measures the boat's height before the keys; {@code serverFloats}: the server has Boat Handling. */
	public BoatRide measuringFloat(boolean serverFloats) {
		this.measureFloat = true;
		this.serverFloats = serverFloats;
		return this;
	}

	/** One game tick; true when done. */
	public boolean tick(Minecraft mc) {
		if (mc.currentScreen instanceof DisconnectedScreen || mc.world == null || mc.player == null) {
			results.fail(name() + ": disconnected or left the world in stage " + stage, new AssertionError());
			return true;
		}
		ticks++;
		PlayerEntity p = mc.player;
		switch (stage) {
			case 0:
				// To the start first: a server sends an entity only within its view distance (32 blocks on the test's).
				if (ticks == 1) mc.player.sendChatMessage("!oftest tp " + backX + " " + backFeet + " " + backZ + " 0");
				if (Math.abs(p.x - backX) > 0.5 || Math.abs(p.z - backZ) > 0.5 || !p.onGround) return timeout(400, "being put at the start");
				mc.player.sendChatMessage("!oftest boat " + poolX + " " + poolZ);
				next();
				return false;
			case 1:
				boat = poolBoat(mc);
				if (boat == null) return timeout(400, "the boat arriving");
				mc.player.sendChatMessage("!oftest ride");
				next();
				return false;
			case 2:
				if (p.vehicle != boat) return timeout(400, "being seated in the boat");
				savedInput = ((ClientPlayerEntity) p).input;
				((ClientPlayerEntity) p).input = input;
				next();
				return false;
			case 3:
				if (measureFloat && ticks > 20 + SETTLE) {
					floatLow = Math.min(floatLow, boat.y);
					floatHigh = Math.max(floatHigh, boat.y);
				}
				if (ticks < (measureFloat ? 20 + SETTLE + FLOAT : 20)) return false;
				if (measureFloat) floated();
				next();
				return false;
			default:
				return kind == Kind.LOOK ? look(mc) : steering(mc);
		}
	}

	private boolean look(Minecraft mc) {
		PlayerEntity p = mc.player;
		switch (stage) {
			case 4:
				// Turning the view, as the mouse does between ticks; the question goes partway through.
				if (ticks == 1) {
					yaw0 = p.yaw;
					reply = null;
				}
				if (ticks == 20) {
					yawAsked = p.yaw;
					mc.player.sendChatMessage("!oftest look");
				}
				p.yaw += 3.0F;
				if (ticks == 30) next();
				return false;
			case 5: {
				if (reply == null) return timeout(400, "the server's answer");
				float server = Float.parseFloat(reply.split(" ")[2]);
				String seen = String.format(Locale.ROOT, "the view from %.1f to %.1f by the question; the server had %.1f", yaw0, yawAsked, server);
				if (expected) {
					results.check(name() + ": the server has the look the client had while it turned (" + seen + ")", Math.abs(server - yawAsked) <= 3.5F);
				} else {
					results.check(name() + " control: beta's client sent no look while it turned, so the server kept the one from before (" + seen + ")",
							Math.abs(server - yaw0) < 0.01F);
				}
				stage = 20;
				ticks = 0;
				return false;
			}
			default:
				return out(mc);
		}
	}

	private boolean steering(Minecraft mc) {
		PlayerEntity p = mc.player;
		switch (stage) {
			case 4:
				// Looking south, nudged a degree and back: three riding moves with looks that differ, if the client sends them.
				p.yaw = p.prevYaw = ticks == 2 ? 1.0F : 0.0F;
				p.pitch = 0;
				if (ticks < 4) return false;
				markX = boat.x;
				markZ = boat.z;
				input.forward = 1;
				next();
				return false;
			case 5: {
				if (ticks < 40) return false;
				input.forward = 0;
				double dx = boat.x - markX, dz = boat.z - markZ;
				String seen = String.format(Locale.ROOT, "heading west, looking south: went %.2f west and %.2f south in 40 ticks", -dx, dz);
				if (expected) {
					// The direction is the point: well west, where beta's paddling goes south (8 was set at the first
					// build's push of 0.05; at 0.0325 it goes about 7).
					results.check(name() + ": W takes the boat west, the way it faces (" + seen + ")", dx < -4 && Math.abs(dz) < 1);
				} else {
					results.check(name() + ": W paddles as beta's, south, the way the player looks (" + seen + ")", dz > 0.2 && dx > -3);
				}
				if (!expected) {
					stage = 20;
					ticks = 0;
					return false;
				}
				lastBoatYaw = boat.yaw;
				lastView = p.yaw;
				input.sideways = 1;
				next();
				return false;
			}
			case 6: {
				boatTurned += wrap(boat.yaw - lastBoatYaw);
				viewTurned += wrap(p.yaw - lastView);
				lastBoatYaw = boat.yaw;
				lastView = p.yaw;
				if (ticks < 40) {
					if (ticks == 30) input.sideways = 0;
					return false;
				}
				String seen = String.format(Locale.ROOT, "A for 30 ticks: the boat turned %.1f, the view %.1f", boatTurned, viewTurned);
				results.check(name() + ": A turns the boat left, and the view with it (" + seen + ")", boatTurned < -120 && viewTurned < -100);
				stage = 20;
				ticks = 0;
				return false;
			}
			default:
				return out(mc);
		}
	}

	private void floated() {
		double surface = MultiplayerScenes.FLOOR + 1;
		String seen = String.format(Locale.ROOT, "moved %.5f over %d ticks, %.4f to %.4f over the surface", floatHigh - floatLow, FLOAT,
				floatLow - surface, floatHigh - surface);
		if (serverFloats) {
			results.check(name() + ": settled, the boat holds its height in this client's world (" + seen + ")", floatHigh - floatLow < 0.001);
		} else {
			System.out.println("[oldfaithful-selftest] " + name() + ", beta's server: the boat in this client's world " + seen);
		}
	}

	/** Out of the boat and back to the start. */
	private boolean out(Minecraft mc) {
		PlayerEntity p = mc.player;
		input.forward = input.sideways = 0;
		if (ticks == 1) {
			mc.player.sendChatMessage("!oftest unride " + backX + " " + backFeet + " " + backZ);
			return false;
		}
		if (p.vehicle != null || Math.abs(p.x - backX) > 0.5 || Math.abs(p.z - backZ) > 0.5) return timeout(400, "getting out and back");
		if (ticks < 20) return false;
		if (savedInput != null) ((ClientPlayerEntity) p).input = savedInput;
		return true;
	}

	/** The pool's boat, once this client has it. */
	private BoatEntity poolBoat(Minecraft mc) {
		for (Object o : mc.world.entities) {
			if (!(o instanceof BoatEntity)) continue;
			BoatEntity b = (BoatEntity) o;
			if (!b.dead && Math.abs(b.x - (poolX - 2.5)) < 1 && Math.abs(b.z - (poolZ + POOL_WIDTH / 2.0)) < 1) return b;
		}
		return null;
	}

	private void next() {
		stage++;
		ticks = 0;
	}

	private boolean timeout(int limit, String what) {
		if (ticks < limit) return false;
		results.fail(name() + ": timed out waiting for " + what, new AssertionError());
		return true;
	}

	private String name() {
		return (kind == Kind.LOOK ? "#83" : "boat steering") + " (" + label + ")";
	}

	private static double wrap(double degrees) {
		double d = degrees % 360.0;
		if (d < -180) d += 360;
		if (d >= 180) d -= 360;
		return d;
	}

	/** The script's keys, as KeyboardInput would set them. */
	private static final class ScriptInput extends Input {
		float forward, sideways;

		@Override
		public void update(PlayerEntity player) {
			movementForward = forward;
			movementSideways = sideways;
			jumping = false;
			sneaking = false;
		}
	}
}
