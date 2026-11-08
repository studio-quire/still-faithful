package org.studioquire.oldfaithful.selftest;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screen.DisconnectedScreen;
import net.minecraft.client.input.Input;
import net.minecraft.entity.player.PlayerEntity;

/**
 * Still Faithful's #30 by real movement: the player walked by a scripted {@link Input} (the game's own physics, nothing
 * reimplemented) into the fences of a {@link FenceScene}, each walk from a place it's put at first. What each walk must
 * end at depends on whether this player's collision should be thin (a post with arms) or beta's whole block, which the
 * caller says: in single player, the polish switch; in multiplayer, the switch on both sides. In multiplayer every walk
 * must also end without the server sending the player anywhere ({@link #serverMoves}, counted from the client's handler
 * of the server's position packet), and standing on the edge of a post's top 6 seconds must not get the player kicked
 * for floating, which beta's server does after 4 (the test server has flying off).
 */
public final class FenceWalk {
	/** Puts the player at a place, standing: directly in single player, by asking the server in multiplayer. */
	public interface Mover {
		void to(Minecraft mc, double x, double feet, double z, float yaw);
	}

	/** Position packets from the server since the client joined (ClientNetworkHandlerFenceMixin). */
	public static volatile int serverMoves;

	private static final double CLOSE = 0.01;

	private final SelfTest results;
	private final String label;
	private final boolean thin;
	private final Mover mover;
	private final int ox, oz;
	private final double feet;
	private final List<Walk> walks = new ArrayList<>();
	private final ScriptInput input = new ScriptInput();
	private Input saved;
	private int walk = -1, stage, ticks, movesBefore;
	private double maxFeet, maxZ, maxDeviation;
	private boolean wasRunning;

	public FenceWalk(SelfTest results, String label, boolean thin, Mover mover, int ox, int floor, int oz) {
		this.results = results;
		this.label = label;
		this.thin = thin;
		this.mover = mover;
		this.ox = ox;
		this.oz = oz;
		this.feet = floor + 1;
		int line = ox + FenceScene.LINE, corner = ox + FenceScene.CORNER;
		// Up to a lone post from the north, its middle: stops a player's half-width (0.3) short of the post or the block.
		walks.add(new Walk("walking up to a lone post", ox + 0.5, feet, oz - 1.5, 0, 30, false) {
			void check(PlayerEntity p) {
				stop("z", p.z - oz, thin ? 0.075 : -0.3);
			}
		});
		// Past its side, 0.6 off its middle: through the block's edge, clear of the post.
		walks.add(new Walk("walking past a lone post's side", ox + 1.1, feet, oz - 1.5, 0, 30, false) {
			void check(PlayerEntity p) {
				if (thin) result("went past it (z " + f(p.z - oz) + ", past 1.5)", p.z - oz > 1.5);
				else stop("z", p.z - oz, -0.3);
			}
		});
		// Diagonally past its north-east corner, on a line 1.2 off its middle (x - z): clips the block, not the post.
		walks.add(new Walk("walking diagonally past a lone post's corner", ox - 0.3, feet, oz - 1.5, -45, 25, false) {
			void track(PlayerEntity p) {
				maxDeviation = Math.max(maxDeviation, Math.abs((p.x - ox) - (p.z - oz) - 1.2));
			}

			void check(PlayerEntity p) {
				String seen = "pushed off its line by up to " + f(maxDeviation) + ", ended at x " + f(p.x - ox);
				if (thin) result(seen + " (thin: never, and past it)", maxDeviation < 0.02 && p.x - ox > 1.5);
				else result(seen + " (whole: slid along the block)", maxDeviation > 0.1);
			}
		});
		// Up to the middle of a line of three from the south.
		walks.add(new Walk("walking up to a line of fences", line + 1.5, feet, oz + 2.5, 180, 30, false) {
			void check(PlayerEntity p) {
				stop("z", p.z - oz, thin ? 0.925 : 1.3);
			}
		});
		// Diagonally into the inside of a corner (fences 12 and 13 east, and 12 east 1 south), from the south-east.
		walks.add(new Walk("walking into a corner's inside", corner + 2.5, feet, oz + 2.5, 135, 30, false) {
			void check(PlayerEntity p) {
				double ex = thin ? 0.925 : 1.3, ez = thin ? 0.925 : 1.3;
				result("ended at x " + f(p.x - corner) + ", z " + f(p.z - oz) + " (" + (thin ? "thin" : "whole") + ": " + f(ex) + ", " + f(ez) + ")",
						Math.abs(p.x - corner - ex) < CLOSE && Math.abs(p.z - oz - ez) < CLOSE);
			}
		});
		// Jumping all the way: still 1.5 high, so never over.
		walks.add(new Walk("jumping at a lone post", ox + 0.5, feet, oz - 1.5, 0, 40, true) {
			void track(PlayerEntity p) {
				maxFeet = Math.max(maxFeet, p.boundingBox.minY - feet);
				maxZ = Math.max(maxZ, p.z - oz);
			}

			void check(PlayerEntity p) {
				double stop = thin ? 0.075 : -0.3;
				result("jumped " + f(maxFeet) + " high, got no further than z " + f(maxZ) + " (" + f(stop) + ")",
						maxFeet > 0.5 && maxZ < stop + CLOSE && Math.abs(p.z - oz - stop) < CLOSE);
			}
		});
		// On the edge of a post's top, 0.65 off its middle: on the block's top, clear of the post's. Stands there with a
		// whole fence, falls with a thin one; 6 s, longer than beta's server lets a player float.
		walks.add(new Walk("standing on the edge of a post's top for 6 s", ox + 1.15, feet + 1.5, oz + 0.5, 0, 0, false) {
			{
				stand = 120;
			}

			void check(PlayerEntity p) {
				double expected = thin ? 0 : 1.5;
				result("feet at " + f(p.boundingBox.minY - feet) + " above the floor (" + (thin ? "thin: fell" : "whole: stood") + ", " + f(expected) + ")",
						Math.abs(p.boundingBox.minY - feet - expected) < CLOSE);
			}
		});
	}

	/** One game tick; true when every walk is done. */
	public boolean tick(Minecraft mc) {
		if (mc.currentScreen instanceof DisconnectedScreen || mc.world == null || mc.player == null) {
			results.fail("fences (" + label + "): disconnected or left the world in walk " + walk, new AssertionError());
			return true;
		}
		PlayerEntity p = mc.player;
		if (walk < 0) {
			saved = ((net.minecraft.entity.player.ClientPlayerEntity) p).input;
			((net.minecraft.entity.player.ClientPlayerEntity) p).input = input;
			next(mc);
		}
		Walk w = walks.get(walk);
		ticks++;
		switch (stage) {
			case 0: // put there; wait until there and settled
				input.forward = 0;
				input.jump = false;
				boolean there = Math.abs(p.x - w.x) < 0.05 && Math.abs(p.z - w.z) < 0.05;
				if (!there && ticks < 100) return false;
				if (!there) {
					results.check("fences (" + label + "): put in place for " + w.name + " (at " + f(p.x) + " " + f(p.z) + ", asked " + f(w.x) + " " + f(w.z) + ")", false);
					return done(mc);
				}
				if (ticks < 10) return false;
				p.yaw = p.prevYaw = w.yaw;
				p.pitch = 0;
				movesBefore = serverMoves;
				stage = 1;
				ticks = 0;
				return false;
			case 1: // walk
				p.yaw = w.yaw;
				input.forward = ticks <= w.walk ? 1 : 0;
				input.jump = w.jump && ticks <= w.walk;
				w.track(p);
				if (ticks < w.walk + w.stand) return false;
				w.check(p);
				if (serverMoves != movesBefore) {
					results.check("fences (" + label + "): " + w.name + ": the server sent the player back " + (serverMoves - movesBefore) + " times", false);
				}
				if (walk + 1 == walks.size()) {
					if (mc.world.isRemote) results.check("fences (" + label + "): no position from the server through all the walks, still connected", true);
					return done(mc);
				}
				next(mc);
				return false;
			default:
				return true;
		}
	}

	private void next(Minecraft mc) {
		walk++;
		stage = 0;
		ticks = 0;
		maxFeet = maxDeviation = 0;
		maxZ = -99;
		Walk w = walks.get(walk);
		mover.to(mc, w.x, w.startFeet, w.z, w.yaw);
	}

	private boolean done(Minecraft mc) {
		input.forward = 0;
		input.jump = false;
		if (saved != null) ((net.minecraft.entity.player.ClientPlayerEntity) mc.player).input = saved;
		return true;
	}

	private static String f(double v) {
		return String.format(java.util.Locale.ROOT, "%+.3f", v);
	}

	private abstract class Walk {
		final String name;
		final double x, startFeet, z;
		final float yaw;
		final int walk;
		final boolean jump;
		int stand = 20;

		Walk(String name, double x, double feet, double z, float yaw, int walk, boolean jump) {
			this.name = name;
			this.x = x;
			this.startFeet = feet;
			this.z = z;
			this.yaw = yaw;
			this.walk = walk;
			this.jump = jump;
		}

		void track(PlayerEntity p) {
		}

		abstract void check(PlayerEntity p);

		void stop(String axis, double at, double expected) {
			result("stopped at " + axis + " " + f(at) + " (" + (thin ? "thin" : "whole") + ": " + f(expected) + ")", Math.abs(at - expected) < CLOSE);
		}

		void result(String what, boolean ok) {
			results.check("fences (" + label + "): " + name + ": " + what, ok);
		}
	}

	/** The script's keys, as KeyboardInput would set them. */
	private static final class ScriptInput extends Input {
		float forward;
		boolean jump;

		@Override
		public void update(PlayerEntity player) {
			movementSideways = 0.0F;
			movementForward = forward;
			jumping = jump;
			sneaking = false;
		}
	}
}
