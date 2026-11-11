package org.studioquire.oldfaithful.selftest;

import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.function.Function;

import javax.imageio.ImageIO;

import net.minecraft.block.Block;
import net.minecraft.client.Minecraft;
import net.minecraft.client.network.OtherPlayerEntity;
import net.minecraft.client.render.FrustumCuller;
import net.minecraft.entity.Entity;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.mob.CreeperEntity;
import net.minecraft.entity.mob.GhastEntity;
import net.minecraft.entity.mob.PigZombieEntity;
import net.minecraft.entity.mob.SkeletonEntity;
import net.minecraft.entity.mob.SlimeEntity;
import net.minecraft.entity.mob.SpiderEntity;
import net.minecraft.entity.mob.ZombieEntity;
import net.minecraft.entity.passive.ChickenEntity;
import net.minecraft.entity.passive.CowEntity;
import net.minecraft.entity.passive.PigEntity;
import net.minecraft.entity.passive.SheepEntity;
import net.minecraft.entity.passive.SquidEntity;
import net.minecraft.entity.passive.WolfEntity;
import net.minecraft.entity.projectile.ArrowEntity;
import net.minecraft.entity.vehicle.BoatEntity;
import net.minecraft.entity.vehicle.MinecartEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.math.Box;
import net.minecraft.world.World;
import org.studioquire.oldfaithful.selftest.mixin.CreeperFuseAccessor;
import org.studioquire.oldfaithful.selftest.mixin.WorldRendererAccessor;

/**
 * How far each kind of thing is drawn past its box at the screen's edges, measured in the real game, unseen
 * (`:harness:cullingMeasure`, docs/bugs.md #84). Beta draws an entity only if its box is in the view; a model that reaches
 * past its box can be on screen when its box isn't, and then it vanishes.
 * <p>
 * The camera is held 180 blocks up, above everything, looking along +z. For each kind, in each of its poses (walking
 * limbs, a creeper's swell, a squid's tilt...), facing each way, a probe is slid from the middle of the view towards an
 * edge: the side (a vertical plane), the top and bottom (planes 55 degrees from the vertical), and the top and bottom with
 * the camera pitched 35 degrees so those planes lie flat (parts below and above the box). Along the slide the probe is
 * drawn regardless of culling, and its pixels are counted from the depth buffer (everything nearer than 30 blocks is
 * it). Where beta's culler first drops the box, the pixels still on screen are counted; then the furthest point where
 * any pixel remains is found by halving, and there the box's distance outside the edge's plane is the reach, and the
 * growth on every side that would bring it back in is the padding needed. Last, a frame drawn the game's own way, culling
 * and all: with oldfaithful-fixes' render.entityCulling on, at the furthest point (it must be drawn); without it, just past
 * where beta drops the box (it mustn't be).
 * <p>
 * Results in {@code culling.txt} and {@code sweeps.tsv} in the run's folder, and pictures of a few kinds at the moment
 * beta drops them, beside what the fixed game draws.
 */
public final class CullingMeasure implements Culling.Scene {
	public static final boolean ENABLED = Boolean.getBoolean("oldfaithful.selftest.culling");
	private static final List<String> ONLY = list(System.getProperty("oldfaithful.selftest.cullingKinds", ""));
	private static final String LABEL = System.getProperty("oldfaithful.selftest.cullingLabel", "run");
	private static final boolean PICTURES = !"false".equals(System.getProperty("oldfaithful.selftest.cullingPictures"));
	private static final double EYE = 180, NEAR = 30;
	/** Just past where beta drops the box, and how finely the furthest drawn point is found. */
	private static final double JUST = 0.002, STEP = 0.004;
	private static final int FLOOR = 118;

	private interface Pose {
		void apply(Entity e, float tickDelta);
	}

	private static final class State {
		final String label;
		final Pose pose;

		State(String label, Pose pose) {
			this.label = label;
			this.pose = pose;
		}
	}

	private static final class Kind {
		final String name;
		final Function<World, Entity> make;
		final double distance, slide;
		final List<State> states;
		double reach, pad;
		Sweep widest, furthest;
		int sweeps, dropped, validated, validFailed;

		Kind(String name, Function<World, Entity> make, double distance, double slide, List<State> states) {
			this.name = name;
			this.make = make;
			this.distance = distance;
			this.slide = slide;
			this.states = states;
		}
	}

	/**
	 * A way of looking at an edge: which way the probe slides (left, up or down on screen), the camera's pitch (level, or
	 * tilted by half the view's height so the top or bottom edge's plane lies flat) and its yaw (along +z, or turned by
	 * half the view's width so the side edge's plane faces along x). The probe's yaws are relative to the camera's.
	 */
	private static final class View {
		final String label;
		final int flat;
		final char toward;
		final boolean square;
		final float[] yaws;

		View(String label, char toward, int flat, boolean square, int yaws) {
			this.label = label;
			this.flat = flat;
			this.toward = toward;
			this.square = square;
			this.yaws = new float[yaws];
			for (int i = 0; i < yaws; i++) this.yaws[i] = 360F * i / yaws;
		}

		float pitch() {
			return (float) (flat * Culling.halfHigh);
		}

		float cameraYaw() {
			return square ? (float) Culling.halfWide : 0;
		}
	}

	private static final View[] VIEWS = {
			new View("side", 'L', 0, false, 16),
			new View("side, its plane along x", 'L', 0, true, 16),
			new View("top", 'U', 0, false, 8),
			new View("bottom", 'D', 0, false, 8),
			new View("top, its plane flat", 'U', 1, false, 4),
			new View("bottom, its plane flat", 'D', -1, false, 4),
	};

	private enum Stage { START, AT_DROP, FAR, HALVING, GAME, DONE }

	private static final class Sweep {
		final Kind kind;
		final State state;
		final View view;
		final float yaw;
		Stage stage = Stage.START;
		double s, s0, lo, hi, slide, s1;
		boolean forced = true;
		int atDrop = -1, game = -1;
		double reach, pad;
		String edge = "";
		boolean beyond;

		Sweep(Kind kind, State state, View view, float yaw) {
			this.kind = kind;
			this.state = state;
			this.view = view;
			this.yaw = yaw;
		}

		String what() {
			return kind.name + " " + state.label + ", " + view.label + " (" + edge + "), facing " + (int) yaw;
		}
	}

	private final File out;
	private final SelfTest results;
	private final List<Kind> kinds = new ArrayList<>();
	private final List<Sweep> sweeps = new ArrayList<>();
	private final StringBuilder tsv = new StringBuilder("kind\tstate\tview\tedge\tyaw\tpixels_at_drop\treach\tpad\tgame_pixels\n");
	private final boolean fixOn;
	private int phase, ticks, sweep;
	private Kind kind;
	private Entity probe;
	private double cx, cz;
	private FrustumCuller culler;
	private volatile boolean measured;
	private long startedAt;
	private String window = "";
	private double pictureOut;

	// Pictures: a few kinds where beta drops them while most of them shows.
	private final List<Sweep> pictures = new ArrayList<>();
	private int picture, shot;
	private BufferedImage hidden;

	CullingMeasure(String outDir, SelfTest results) {
		this.out = new File(outDir);
		this.results = results;
		this.fixOn = fixOn();
		defineKinds();
	}

	/** Whether oldfaithful-fixes is here with render.entityCulling on (read reflectively: the harness doesn't build on it). */
	static boolean fixOn() {
		try {
			Class<?> c = Class.forName("org.studioquire.oldfaithful.fixes.FixesConfig");
			return (Boolean) c.getMethod("applied", String.class).invoke(null, "render.entityCulling")
					&& ((java.util.List<?>) c.getMethod("keys").invoke(null)).contains("render.entityCulling");
		} catch (ReflectiveOperationException | RuntimeException e) {
			return false;
		}
	}

	// ---- The kinds and their poses

	private static List<State> limbs() {
		return new ArrayList<>(Arrays.asList(
				new State("standing", (e, t) -> Culling.walk((LivingEntity) e, 0, 0, t)),
				new State("mid-stride", (e, t) -> Culling.walk((LivingEntity) e, 0, 1, t)),
				new State("other stride", (e, t) -> Culling.walk((LivingEntity) e, (float) Math.PI, 1, t))));
	}

	/** Fallen onto its side as it dies (beta's death roll is whole after 13 of its 20 ticks). */
	private static List<State> dying(List<State> states) {
		states.add(new State("fallen over dying", (e, t) -> ((LivingEntity) e).deathTime = 20));
		return states;
	}

	private static List<State> one() {
		return new ArrayList<>(Arrays.asList(new State("", (e, t) -> { })));
	}

	/** Each of {@code a} with each of {@code b} after it. */
	private static List<State> cross(List<State> a, List<State> b) {
		List<State> all = new ArrayList<>();
		for (State x : a) {
			for (State y : b) {
				all.add(new State((x.label + " " + y.label).trim(), (e, t) -> {
					x.pose.apply(e, t);
					y.pose.apply(e, t);
				}));
			}
		}
		return all;
	}

	private void defineKinds() {
		kinds.add(new Kind("zombie", ZombieEntity::new, 4, 2, dying(limbs())));
		kinds.add(new Kind("skeleton", SkeletonEntity::new, 4, 2, limbs()));
		kinds.add(new Kind("zombie pigman", PigZombieEntity::new, 4, 2, limbs()));
		kinds.add(new Kind("creeper", CreeperEntity::new, 4, 2, cross(limbs(), Arrays.asList(
				new State("", (e, t) -> fuse(e, 0)),
				new State("swollen", (e, t) -> fuse(e, 28))))));
		kinds.add(new Kind("spider", SpiderEntity::new, 4, 2, dying(limbs())));
		kinds.add(new Kind("cow", CowEntity::new, 4, 2, dying(limbs())));
		kinds.add(new Kind("pig", w -> {
			PigEntity p = new PigEntity(w);
			p.setSaddled(true);
			return p;
		}, 4, 2, limbs()));
		kinds.add(new Kind("sheep", SheepEntity::new, 4, 2, limbs()));
		kinds.add(new Kind("chicken", ChickenEntity::new, 4, 2, cross(limbs(), Arrays.asList(
				new State("wings in", (e, t) -> flap(e, -Math.PI / 2)),
				new State("wings half", (e, t) -> flap(e, 0)),
				new State("wings up", (e, t) -> flap(e, Math.PI / 2))))));
		kinds.add(new Kind("wolf", WolfEntity::new, 4, 2, cross(limbs(), Arrays.asList(
				new State("", (e, t) -> ((WolfEntity) e).setSitting(false)),
				new State("sitting", (e, t) -> ((WolfEntity) e).setSitting(true))))));
		List<State> squid = new ArrayList<>();
		for (int tilt : new int[] {0, -45, -90, -135, -180}) {
			for (float tentacles : new float[] {0, (float) Math.PI / 4}) {
				squid.add(new State("tilted " + tilt + (tentacles > 0 ? ", tentacles out" : ""), (e, t) -> {
					SquidEntity s = (SquidEntity) e;
					s.tiltAngle = s.lastTiltAngle = tilt;
					s.rollAngle = s.lastRollAngle = 0;
					s.tentacleAngle = s.lastTentacleAngle = tentacles;
				}));
			}
		}
		kinds.add(new Kind("squid", SquidEntity::new, 4, 4, squid));
		List<State> player = limbs();
		player.add(new State("swinging a quarter", (e, t) -> swing(e, 0.25F)));
		player.add(new State("swinging half", (e, t) -> swing(e, 0.5F)));
		kinds.add(new Kind("player with a sword", w -> player(w, ""), 4, 2, player));
		kinds.add(new Kind("player's name tag (16 letters)", w -> player(w, "abcdefghijklmnop"), 5, 3, one()));
		List<State> stretch = new ArrayList<>();
		for (float s : new float[] {-0.5F, 0, 1}) {
			stretch.add(new State(s < 0 ? "landing" : s > 0 ? "jumping" : "resting", (e, t) -> {
				((SlimeEntity) e).stretch = s;
				((SlimeEntity) e).lastStretch = s;
			}));
		}
		kinds.add(new Kind("small slime", w -> slime(w, 1), 4, 2, stretch));
		kinds.add(new Kind("big slime", w -> slime(w, 4), 6, 3, stretch));
		List<State> ghast = new ArrayList<>();
		for (int charge : new int[] {0, 20}) {
			for (int age : new int[] {0, 5, 10, 15}) {
				ghast.add(new State((charge > 0 ? "charging, " : "") + "age " + age, (e, t) -> {
					GhastEntity g = (GhastEntity) e;
					g.chargeTime = g.lastChargeTime = charge;
					g.age = age;
				}));
			}
		}
		kinds.add(new Kind("ghast", GhastEntity::new, 10, 6, dying(ghast)));
		kinds.add(new Kind("boat", BoatEntity::new, 4, 2, one()));
		kinds.add(new Kind("minecart", MinecartEntity::new, 4, 2, one()));
		List<State> items = new ArrayList<>();
		for (int age : new int[] {0, 16, 31, 47}) {
			items.add(new State("age " + age, (e, t) -> {
				((ItemEntity) e).itemAge = age;
				((ItemEntity) e).initialRotationAngle = 0;
			}));
		}
		kinds.add(new Kind("dropped apple", w -> new ItemEntity(w, 0, 0, 0, new ItemStack(Item.APPLE)), 4, 1, items));
		kinds.add(new Kind("dropped stack of dirt", w -> new ItemEntity(w, 0, 0, 0, new ItemStack(Block.DIRT, 64)), 4, 1, items));
		List<State> arrows = new ArrayList<>();
		for (float pitch : new float[] {0, 45, -45}) arrows.add(new State("pitched " + (int) pitch, (e, t) -> e.pitch = e.prevPitch = pitch));
		kinds.add(new Kind("arrow", ArrowEntity::new, 4, 2, arrows));
		if (!ONLY.isEmpty()) kinds.removeIf(k -> !ONLY.contains(k.name.toLowerCase(Locale.ROOT)) && !ONLY.contains(k.name.split(" ")[0].toLowerCase(Locale.ROOT)));
	}

	private static void fuse(Entity e, int ticks) {
		((CreeperFuseAccessor) e).oldfaithful$setFuseTime(ticks);
		((CreeperFuseAccessor) e).oldfaithful$setLastFuseTime(ticks);
	}

	/** A chicken's wings, beta's flap at {@code flap} with the full spread (its renderer: (sin flap + 1) times the spread). */
	private static void flap(Entity e, double flap) {
		ChickenEntity c = (ChickenEntity) e;
		c.flapProgress = c.prevFlapProgress = (float) flap;
		c.maxWingDeviation = c.prevMaxWingDeviation = 1;
	}

	private static void swing(Entity e, float progress) {
		LivingEntity l = (LivingEntity) e;
		l.swingAnimationProgress = l.lastSwingAnimationProgress = progress;
	}

	/** Another player as a server's would be, an iron sword in hand; no skin or cape is fetched (the probe isn't announced to the renderer). */
	private static Entity player(World w, String name) {
		OtherPlayerEntity p = new OtherPlayerEntity(w, name);
		p.skinUrl = null;
		p.capeUrl = null;
		p.inventory.main[p.inventory.selectedSlot] = new ItemStack(Item.IRON_SWORD);
		return p;
	}

	private static Entity slime(World w, int size) {
		SlimeEntity s = new SlimeEntity(w);
		s.setSize(size);
		return s;
	}

	// ---- Ticks: the world held still around the measurement

	boolean tick(Minecraft mc) {
		mc.options.difficulty = 0;
		mc.options.bobView = false;
		mc.world.setTime(6000);
		mc.world.getProperties().setRaining(false);
		mc.world.getProperties().setThundering(false);
		mc.world.getProperties().setRainTime(Integer.MAX_VALUE / 2);
		mc.world.setRainGradient(0);
		ticks++;
		switch (phase) {
			case 0:
				cx = Math.floor(mc.player.x) + 0.5;
				cz = Math.floor(mc.player.z) + 0.5;
				startedAt = System.currentTimeMillis();
				window = mc.displayWidth + "x" + mc.displayHeight;
				phase = 1;
				return false;
			case 1:
				Culling.hold(mc.player, cx, EYE, cz, 0, 0);
				if (ticks < 40) return false;
				for (Kind k : kinds) {
					for (State st : k.states) {
						for (View v : VIEWS) {
							for (float yaw : v.yaws) sweeps.add(new Sweep(k, st, v, yaw));
						}
					}
				}
				log("measuring " + kinds.size() + " kinds in " + sweeps.size() + " slides, render.entityCulling " + (fixOn ? "on" : "off or absent"));
				Culling.scene = this;
				phase = 2;
				return false;
			case 2:
				if (!measured) return false;
				Culling.scene = null;
				removeProbe(mc);
				summarise();
				if (!PICTURES || pictures.isEmpty()) {
					write();
					return true;
				}
				buildPlatform(mc.world);
				phase = 3;
				ticks = 0;
				return false;
			case 3:
				// The platform's chunks rebuilt and lit before any picture.
				Culling.hold(mc.player, cx, FLOOR + 1 + 1.62, cz, 0, 0);
				if (ticks < 40 || ticks < 600 && !((WorldRendererAccessor) mc.worldRenderer).oldfaithful$dirtyChunks().isEmpty()) return false;
				picture = 0;
				shot = 0;
				Culling.scene = this;
				phase = 4;
				return false;
			case 4:
				if (picture < pictures.size()) return false;
				Culling.scene = null;
				removeProbe(mc);
				write();
				return true;
			default:
				return true;
		}
	}

	// ---- Frames (Culling.Scene): one sample each

	private Sweep current() {
		return phase == 4 ? (picture < pictures.size() ? pictures.get(picture) : null) : sweep < sweeps.size() ? sweeps.get(sweep) : null;
	}

	@Override
	public void frameStart(Minecraft mc) {
		Sweep s = current();
		if (s == null) return;
		boolean flat = phase == 4 && platformed(s);
		Culling.hold(mc.player, cx, flat ? FLOOR + 1 + 1.62 : EYE, cz, s.view.cameraYaw(), s.view.pitch());
	}

	@Override
	public void entities(Minecraft mc, FrustumCuller culler, float tickDelta) {
		this.culler = culler;
		Sweep s = current();
		if (s == null) return;
		if (s.kind != kind) newProbe(mc, s.kind);
		if (phase == 4) {
			pictureEntities(mc, s, tickDelta);
			return;
		}
		if (s.stage == Stage.START) {
			s.s0 = dropAt(s, tickDelta);
			s.slide = s.kind.slide;
			s.s = s.s0 + JUST;
			s.forced = true;
			s.stage = Stage.AT_DROP;
		}
		pose(s, s.s, tickDelta, false);
		probe.ignoreFrustumCull = s.forced;
	}

	@Override
	public void drawn(Minecraft mc) {
		Sweep s = current();
		if (s == null) return;
		if (phase == 4) {
			pictureDrawn(mc, s);
			return;
		}
		int n = Culling.nearPixels(mc.displayWidth, mc.displayHeight, NEAR);
		switch (s.stage) {
			case AT_DROP:
				s.atDrop = n;
				s.edge = Culling.edge(culler, probe.boundingBox);
				if (n == 0) {
					s.s1 = s.s0;
					measure(s);
					return;
				}
				s.s = s.s0 + s.slide;
				s.stage = Stage.FAR;
				return;
			case FAR:
				if (n > 0) {
					if (s.slide < 32) {
						s.slide *= 2;
						s.s = s.s0 + s.slide;
						return;
					}
					s.beyond = true;
					s.lo = s.s;
				} else {
					s.lo = s.s0 + JUST;
					s.hi = s.s;
					s.s = (s.lo + s.hi) / 2;
					s.stage = Stage.HALVING;
					return;
				}
				s.s1 = s.lo;
				measure(s);
				return;
			case HALVING:
				if (n > 0) s.lo = s.s;
				else s.hi = s.s;
				if (s.hi - s.lo > STEP) {
					s.s = (s.lo + s.hi) / 2;
					return;
				}
				s.s1 = s.lo;
				measure(s);
				return;
			case GAME:
				s.game = n;
				finish(s);
				return;
			default:
		}
	}

	/** The reach and padding at the furthest point still drawn; then a frame the game's own way. */
	private void measure(Sweep s) {
		Box b = boxAt(s, s.s1);
		s.reach = Math.max(0, Culling.outside(culler, b));
		s.pad = Culling.padNeeded(culler, b);
		if (s.atDrop > 0) {
			// With the fix, the furthest point it's still on screen must be drawn; without it, beta drops it just past its box.
			s.s = fixOn ? s.s1 : s.s0 + JUST;
			s.forced = false;
			s.stage = Stage.GAME;
		} else {
			finish(s);
		}
	}

	private void finish(Sweep s) {
		s.stage = Stage.DONE;
		Kind k = s.kind;
		k.sweeps++;
		if (s.atDrop > 0) k.dropped++;
		if (k.furthest == null || s.reach > k.furthest.reach) k.furthest = s;
		k.reach = k.furthest.reach;
		k.pad = Math.max(k.pad, s.pad);
		if (k.widest == null || s.atDrop > k.widest.atDrop) k.widest = s;
		if (s.game >= 0) {
			boolean ok = fixOn ? s.game > 0 : s.game == 0;
			if (ok) k.validated++;
			else k.validFailed++;
		}
		tsv.append(String.format(Locale.ROOT, "%s\t%s\t%s\t%s\t%.1f\t%d\t%.3f\t%.3f\t%d%s\n", k.name, s.state.label, s.view.label, s.edge, s.yaw,
				s.atDrop, s.reach, s.pad, s.game, s.beyond ? "\tbeyond the slide" : ""));
		sweep++;
		Sweep next = sweep < sweeps.size() ? sweeps.get(sweep) : null;
		if (next == null || next.kind != k) {
			log(String.format(Locale.ROOT, "%s: dropped while drawn in %d of %d slides, reach past the box %.3f (%s), padding needed %.3f; most on screen as beta drops it %d pixels (%s)%s",
					k.name, k.dropped, k.sweeps, k.reach, k.furthest == null ? "-" : k.furthest.what(), k.pad, k.widest == null ? 0 : k.widest.atDrop,
					k.widest == null ? "-" : k.widest.what(), gameNote(k)));
		}
		if (next == null) measured = true;
	}

	private String gameNote(Kind k) {
		if (k.validated + k.validFailed == 0) return "";
		return fixOn ? String.format(Locale.ROOT, "; with the fix, drawn at its furthest in %d of %d", k.validated, k.validated + k.validFailed)
				: String.format(Locale.ROOT, "; the game itself dropped it in %d of %d", k.validated, k.validated + k.validFailed);
	}

	// ---- Where the probe goes

	/** Middle of the box at slide s: straight ahead at the kind's distance, then s blocks towards the view's edge (y left to the caller on the platform). */
	private double[] at(Sweep s, double slide, boolean flat) {
		double[] c = Culling.ahead(cx, EYE, cz, s.view.cameraYaw(), s.view.pitch(), s.view.toward, s.kind.distance, slide);
		if (flat) c[1] = 0;
		return c;
	}

	/** The probe's own yaw: the sweep's, relative to the camera's. */
	private static float yaw(Sweep s) {
		return s.view.cameraYaw() + s.yaw;
	}

	private void pose(Sweep s, double slide, float tickDelta, boolean flat) {
		double[] c = at(s, slide, flat);
		if (flat) {
			// Standing on the platform: the box's bottom on its top.
			Culling.place(probe, c[0], 0, c[2], yaw(s), 0);
			c[1] = FLOOR + 1 + (probe.boundingBox.maxY - probe.boundingBox.minY) / 2;
		}
		Culling.place(probe, c[0], c[1], c[2], yaw(s), 0);
		if (probe instanceof LivingEntity) ((LivingEntity) probe).deathTime = 0;
		s.state.pose.apply(probe, tickDelta);
	}

	private Box boxAt(Sweep s, double slide) {
		pose(s, slide, 1, false);
		return probe.boundingBox.copy();
	}

	/** The slide at which beta's culler first drops the box. */
	private double dropAt(Sweep s, float tickDelta) {
		double lo = 0, hi = s.kind.distance * 3 + 12;
		for (int i = 0; i < 48; i++) {
			double mid = (lo + hi) / 2;
			pose(s, mid, tickDelta, false);
			if (Culling.visible(culler, probe.boundingBox)) lo = mid;
			else hi = mid;
		}
		return hi;
	}

	private void newProbe(Minecraft mc, Kind k) {
		removeProbe(mc);
		kind = k;
		probe = k.make.apply(mc.world);
		Culling.place(probe, cx, EYE, cz + k.distance, 0, 0);
		// Straight into the world's list: not announced to the renderer, which would fetch a player's skin and cape.
		mc.world.entities.add(probe);
		Culling.frozen = probe;
	}

	private void removeProbe(Minecraft mc) {
		if (probe == null) return;
		// Dead, it stays in the world's list until the next tick: out of every view meanwhile.
		probe.ignoreFrustumCull = false;
		Culling.place(probe, cx, -1000, cz, 0, 0);
		probe.markDead();
		Culling.frozen = null;
		probe = null;
		kind = null;
	}

	// ---- Pictures

	private static final List<String> PICTURED = Arrays.asList("cow", "zombie", "spider", "squid", "ghast");

	/** On the platform: kinds that stand on the ground, seen from the side. Others in the sky, where they were measured. */
	private static boolean platformed(Sweep s) {
		return s.view.toward == 'L' && !s.kind.name.equals("ghast");
	}

	private void summarise() {
		for (Kind k : kinds) {
			if (!PICTURED.contains(k.name) || k.widest == null || k.widest.atDrop == 0) continue;
			Sweep best = null;
			for (Sweep s : sweeps) {
				if (s.kind != k || s.atDrop <= 0) continue;
				if (s.state.label.contains("dying")) continue;
				// The squid as it lies out of water; anything else as it stands.
				if (k.name.equals("squid") && !s.state.label.equals("tilted -90")) continue;
				if (!k.name.equals("ghast") && s.view.toward != 'L') continue;
				if (!k.name.equals("squid") && !k.name.equals("ghast") && !s.state.label.equals("standing")) continue;
				if (best == null || s.atDrop > best.atDrop) best = s;
			}
			if (best == null) continue;
			Sweep p = new Sweep(k, best.state, best.view, best.yaw);
			p.s0 = best.s0;
			// A little past where beta drops it, so the box is plainly out of view.
			p.s = best.s0 + Math.min(0.05, (best.s1 - best.s0) / 2);
			p.edge = best.edge;
			pictures.add(p);
		}
	}

	private void buildPlatform(World w) {
		int x0 = (int) Math.floor(cx), z0 = (int) Math.floor(cz);
		for (int x = x0 - 24; x <= x0 + 24; x++) {
			for (int z = z0 - 4; z <= z0 + 40; z++) {
				w.setBlock(x, FLOOR, z, Block.GRASS_BLOCK.id);
				for (int y = FLOOR + 1; y < 128; y++) {
					if (w.getBlockId(x, y, z) != 0) w.setBlock(x, y, z, 0);
				}
			}
		}
	}

	private void pictureEntities(Minecraft mc, Sweep s, float tickDelta) {
		boolean flat = platformed(s);
		// The first frame: beta's (the probe out of the way); the second: drawn where beta drops it.
		pose(s, shot == 0 ? s.s0 + 60 : s.s, tickDelta, flat);
		probe.ignoreFrustumCull = shot > 0 && !fixOn;
		if (shot > 0) pictureOut = Culling.outside(culler, probe.boundingBox);
	}

	private void pictureDrawn(Minecraft mc, Sweep s) {
		BufferedImage img = Culling.colour(mc.displayWidth, mc.displayHeight);
		if (shot == 0) {
			hidden = img;
			shot = 1;
			return;
		}
		String name = s.kind.name.replace(' ', '-') + "-at-the-edge.png";
		try {
			out.mkdirs();
			ImageIO.write(sideBySide(hidden, img), "png", new File(out, name));
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}
		log("picture " + name + ": " + s.what() + ", the box " + String.format(Locale.ROOT, "%.3f", pictureOut) + " blocks out of view"
				+ (fixOn ? "" : " (the right-hand frame forced: the fix is off)"));
		hidden = null;
		shot = 0;
		picture++;
	}

	/** Half of each frame's width, round where they differ, beta's on the left and the fixed game's on the right, each labelled. */
	private static BufferedImage sideBySide(BufferedImage beta, BufferedImage fixed) {
		int w = beta.getWidth(), h = beta.getHeight(), half = w / 2;
		int min = w, max = -1;
		for (int y = 0; y < h; y += 2) {
			for (int x = 0; x < w; x += 2) {
				if (beta.getRGB(x, y) != fixed.getRGB(x, y)) {
					min = Math.min(min, x);
					max = Math.max(max, x);
				}
			}
		}
		int x0 = max < 0 ? 0 : Math.max(0, Math.min(w - half, (min + max) / 2 - half / 2));
		BufferedImage both = new BufferedImage(half * 2 + 8, h, BufferedImage.TYPE_INT_RGB);
		Graphics2D g = both.createGraphics();
		g.setColor(Color.WHITE);
		g.fillRect(0, 0, both.getWidth(), h);
		g.drawImage(beta.getSubimage(x0, 0, half, h), 0, 0, null);
		g.drawImage(fixed.getSubimage(x0, 0, half, h), half + 8, 0, null);
		g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
		g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 28));
		label(g, "beta 1.7.3", 20, 44);
		label(g, "Old Faithful", half + 28, 44);
		g.dispose();
		return both;
	}

	private static void label(Graphics2D g, String text, int x, int y) {
		g.setColor(new Color(0, 0, 0, 150));
		g.drawString(text, x + 2, y + 2);
		g.setColor(Color.WHITE);
		g.drawString(text, x, y);
	}

	// ---- Output

	private void write() {
		StringBuilder txt = new StringBuilder();
		txt.append(String.format(Locale.ROOT, "Entity culling at the screen's edges (%s): %d kinds, %d slides, %d s; render.entityCulling %s; window %s%n%n",
				LABEL, kinds.size(), sweeps.size(), (System.currentTimeMillis() - startedAt) / 1000, fixOn ? "on" : "off or absent",
				window));
		txt.append(String.format(Locale.ROOT, "%-32s %8s %8s %8s %10s  %s%n", "kind", "reach", "pad", "dropped", "on screen", "the slide that reached furthest; the game's own frames"));
		for (Kind k : kinds) {
			txt.append(String.format(Locale.ROOT, "%-32s %8.3f %8.3f %4d/%-4d %10d  %s%s%n", k.name, k.reach, k.pad, k.dropped, k.sweeps,
					k.widest == null ? 0 : k.widest.atDrop, k.furthest == null ? "-" : k.furthest.what(), gameNote(k)));
		}
		txt.append("\nreach: how far the box lay outside the edge's plane (blocks) at the furthest point any of it was still drawn.\n"
				+ "pad: the growth on every side of the box that would have kept it drawn there.\n"
				+ "dropped: slides in which beta dropped it while some of it was on screen.\n"
				+ "on screen: the most pixels still on screen at the moment beta drops it (one slide).\n");
		try {
			out.mkdirs();
			Files.write(new File(out, "culling.txt").toPath(), txt.toString().getBytes(StandardCharsets.UTF_8));
			Files.write(new File(out, "sweeps.tsv").toPath(), tsv.toString().getBytes(StandardCharsets.UTF_8));
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}
		int failed = 0;
		for (Kind k : kinds) failed += k.validFailed;
		results.check("culling: measured " + sweeps.size() + " slides of " + kinds.size() + " kinds (" + new File(out, "culling.txt") + ")"
				+ (failed > 0 ? "; the game's own frames disagreed with " + (fixOn ? "the fix" : "beta's culling") + " in " + failed + " (sweeps.tsv)" : ""),
				measured);
	}

	private static List<String> list(String s) {
		List<String> l = new ArrayList<>();
		for (String p : s.split(",")) {
			if (!p.trim().isEmpty()) l.add(p.trim().toLowerCase(Locale.ROOT));
		}
		return l;
	}

	private static void log(String s) {
		System.out.println("[oldfaithful-selftest] (culling) " + s);
	}
}
