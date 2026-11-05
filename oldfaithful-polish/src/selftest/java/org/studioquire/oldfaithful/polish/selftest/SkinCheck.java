package org.studioquire.oldfaithful.polish.selftest;

import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

import javax.imageio.ImageIO;

import net.minecraft.block.Block;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screen.ingame.InventoryScreen;
import net.minecraft.client.network.OtherPlayerEntity;
import net.minecraft.client.texture.ImageDownload;
import net.minecraft.client.texture.ImageProcessor;
import net.minecraft.entity.Entity;
import net.minecraft.entity.mob.SkeletonEntity;
import net.minecraft.entity.mob.ZombieEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.world.World;
import org.studioquire.oldfaithful.hardening.util.Https;
import org.studioquire.oldfaithful.polish.ModernSkin;
import org.studioquire.oldfaithful.polish.PolishConfig;
import org.studioquire.oldfaithful.polish.selftest.mixin.SkinDownloads;
import org.studioquire.oldfaithful.selftest.ScreenCapture;
import org.studioquire.oldfaithful.selftest.SelfTest;
import org.studioquire.oldfaithful.selftest.WorldCheck;

/**
 * Modern skins (`skins.modern`) in the running world, with generated skins served through hardening's real path
 * ({@link TestSkins}). Pictures in `skins/` of the run folder: every skin from four sides, above and below, the poses,
 * the mobs on the same biped model, the first-person hand, third person, the inventory's figure. Where the switch is
 * off, modern skins must come out as beta draws them; everything is held still, so a classic skin's and a mob's pictures
 * can be compared between a run with the switch and one without.
 *
 * Figures don't tick, so a pose is exactly what's set on them. The checks read a loud skin's hues off the picture: which
 * parts appear, on which side of the figure, and how wide an arm is. What only an eye can judge is in the pictures.
 */
public final class SkinCheck implements WorldCheck {
	private static final String KEY = "skins.modern";
	private static final File OUT = new File("skins");
	private static final int G = 120;
	private static final double EYE = G + 1.62;
	/** A figure's feet below the camera, so its middle is in the middle of the picture, and its distance away. */
	private static final double FEET = EYE - 0.9, AWAY = 2.0;
	private static final int FRONT = 180, BACK = 0, RIGHT_SIDE = 90, LEFT_SIDE = 270;
	private static final int PRESENT = 20;

	// Read by the selftest mixins.
	public static volatile boolean still;
	public static volatile Entity onShow;
	public static volatile int drawn;

	public static Https.Response answer(String url) {
		return TestSkins.answer(url);
	}

	/** A player that holds whatever pose it's given. */
	private static final class Figure extends OtherPlayerEntity {
		boolean sneaking, sleeping, riding;
		float swing;

		Figure(World world, String name) {
			super(world, name);
		}

		@Override
		public void tick() {
		}

		@Override
		public boolean isSneaking() {
			return sneaking;
		}

		@Override
		public boolean isSleeping() {
			return sleeping;
		}

		@Override
		public boolean hasVehicle() {
			return riding;
		}

		@Override
		public float getHandSwingProgress(float tickDelta) {
			return swing;
		}

		void reset() {
			sneaking = sleeping = riding = false;
			swing = 0;
			hurtTime = 0;
			walkAnimationProgress = walkAnimationSpeed = lastWalkAnimationSpeed = 0;
			inventory.main[inventory.selectedSlot] = null;
			java.util.Arrays.fill(inventory.armor, null);
		}
	}

	private static final class StillZombie extends ZombieEntity {
		StillZombie(World world) {
			super(world);
		}

		@Override
		public void tick() {
		}
	}

	private static final class StillSkeleton extends SkeletonEntity {
		StillSkeleton(World world) {
			super(world);
		}

		@Override
		public void tick() {
		}
	}

	private interface Step {
		/** One tick; true when done. */
		boolean tick(Minecraft mc);
	}

	private SelfTest results;
	private World world;
	private boolean on;
	private double ox, oz;
	private double cameraPitch;
	private Object[] saved;
	private String ownSkin;
	private boolean holdsHand;
	private final Deque<Step> plan = new ArrayDeque<>();
	private final Map<String, Figure> figures = new LinkedHashMap<>();
	private final List<Entity> stage = new ArrayList<>();
	private Entity showing;
	private Figure twin;
	private int swapInterval = -1;
	/** The backdrop's blocks as they were, x, y, z, id. */
	private final List<int[]> under = new ArrayList<>();

	@Override
	public void start(Minecraft mc, SelfTest results) {
		this.results = results;
		this.world = mc.world;
		on = PolishConfig.applied(KEY);
		still = true;
		// The start-up test already turns vsync off (and only the settings check's VSync button turns it on); hidden clients waiting on the
		// refresh are what's been found to stall the display (root docs/gpu-resets.md > Silent stalls): off while these
		// hundred pictures are taken, then put back as it was.
		swapInterval = swapInterval();
		org.lwjgl.opengl.Display.setSwapInterval(0);
		DuskCheck.frozen = true;
		saved = new Object[] {mc.options.difficulty, mc.options.hideHud, mc.options.bobView, mc.options.thirdPerson,
				mc.player.x, mc.player.y - mc.player.standingEyeHeight, mc.player.z};
		ownSkin = mc.player.skinUrl;
		mc.options.difficulty = 0;
		mc.options.hideHud = true;
		mc.options.bobView = false;
		mc.options.thirdPerson = false;
		world.getProperties().setRaining(false);
		world.getProperties().setThundering(false);
		world.getProperties().setRainTime(1_000_000);
		world.getProperties().setThunderTime(1_000_000);
		world.setRainGradient(0.0F);
		ox = Math.floor(mc.player.x) + 0.5;
		oz = Math.floor(mc.player.z) + 0.5;
		backdrop();
		for (String name : new String[] {TestSkins.WIDE, TestSkins.SLIM, TestSkins.OLD, TestSkins.SUIT, TestSkins.SUIT_SLIM, TestSkins.CAPED}) {
			Figure f = new Figure(world, name);
			figures.put(name, f);
			enter(f);
		}
		// A second player of the same name, another case: the same skin, no second lookup.
		twin = new Figure(world, TestSkins.SLIM.toLowerCase(java.util.Locale.ROOT));
		enter(twin);
		plan.add(until("every test skin to arrive", () -> {
			if (image(mc, twin.skinUrl) == null) return false;
			for (String name : figures.keySet()) {
				if (image(mc, skin(name)) == null) return false;
			}
			return image(mc, cape(TestSkins.CAPED)) != null;
		}));
		plan.add(c -> {
			downloads(c);
			processing();
			return true;
		});
		String[] loud = {TestSkins.WIDE, TestSkins.SLIM, TestSkins.OLD};
		for (String name : figures.keySet()) {
			if (name.equals(TestSkins.CAPED)) continue;
			sides(name);
		}
		for (String name : loud) {
			from(name, "above", -3.0, 89);
			from(name, "below", 0.7, -89);
		}
		poses(TestSkins.WIDE);
		poses(TestSkins.SUIT_SLIM);
		poses(TestSkins.OLD);
		poses(TestSkins.CAPED);
		shot(TestSkins.CAPED + "-back", figure(TestSkins.CAPED, BACK, null), null);
		shot(TestSkins.CAPED + "-back-walk", figure(TestSkins.CAPED, 30, x -> {
			x.walkAnimationProgress = 2.35F;
			x.walkAnimationSpeed = x.lastWalkAnimationSpeed = 1.0F;
		}), null);
		mobs();
		own();
	}

	/**
	 * White wool behind the stage and below it, so a picture's colours are the figure's: grass, water or a sunset sky
	 * could pass for a part's hue.
	 */
	private void backdrop() {
		int x0 = (int) Math.floor(ox), z0 = (int) Math.floor(oz);
		for (int x = x0 - 12; x <= x0 + 12; x++) {
			for (int y = G - 8; y <= G + 10; y++) {
				wool(x, y, z0 + 5);
			}
			for (int z = z0 - 4; z <= z0 + 5; z++) wool(x, G - 8, z);
		}
	}

	private void wool(int x, int y, int z) {
		under.add(new int[] {x, y, z, world.getBlockId(x, y, z)});
		world.setBlock(x, y, z, Block.WOOL.id);
	}

	/** Spawned behind the camera, and kept there until shown. */
	private void enter(Entity e) {
		stage.add(e);
		park(e);
		world.spawnEntity(e);
	}

	private void park(Entity e) {
		e.setPositionAndAnglesKeepPrevAngles(ox, FEET, oz - 6, 0, 0);
	}

	private static String skin(String name) {
		return "http://s3.amazonaws.com/MinecraftSkins/" + name + ".png";
	}

	private static String cape(String name) {
		return "http://s3.amazonaws.com/MinecraftCloaks/" + name + ".png";
	}

	private static BufferedImage image(Minecraft mc, String url) {
		ImageDownload d = ((SkinDownloads) mc.textureManager).oldfaithful$skinCheckDownloads().get(url);
		return d == null ? null : d.image;
	}

	private void downloads(Minecraft mc) {
		for (String name : figures.keySet()) {
			BufferedImage image = image(mc, skin(name));
			boolean classic = name.equals(TestSkins.OLD);
			boolean whole = on && !classic;
			results.check("skins: " + name + " arrived " + image.getWidth() + "x" + image.getHeight() + " (" + (whole ? "whole" : "beta's canvas") + ")",
					image.getHeight() == (whole ? 64 : 32));
			if (on) results.check("skins: " + name + " is " + (whole ? "" : "not ") + "a modern skin", image instanceof ModernSkin == whole);
			results.check("skins: one name lookup and one profile for " + name + ", from hardening alone",
					TestSkins.ASKED.getOrDefault("name " + name, 0) == 1 && TestSkins.ASKED.getOrDefault("profile " + name, 0) == 1);
		}
		if (on) {
			results.check("skins: a slim profile gives a slim skin", ((ModernSkin) image(mc, skin(TestSkins.SLIM))).slim && ((ModernSkin) image(mc, skin(TestSkins.SUIT_SLIM))).slim);
			results.check("skins: the same player in another case is slim too", ((ModernSkin) image(mc, twin.skinUrl)).slim);
			results.check("skins: a profile without the flag gives a wide skin", !((ModernSkin) image(mc, skin(TestSkins.WIDE))).slim && !((ModernSkin) image(mc, skin(TestSkins.SUIT))).slim);
		}
		BufferedImage cape = image(mc, cape(TestSkins.CAPED));
		results.check("skins: a cape stays on beta's canvas", cape.getHeight() == 32 && !(cape instanceof ModernSkin));
	}

	/** The transparency rules, straight on the processor: beta's hat rule for every layer, and the base opaque. */
	private void processing() {
		if (!on) return;
		ImageProcessor processor = ModernSkin.processor(skin("QuireNobody"));
		BufferedImage painted = new BufferedImage(64, 64, BufferedImage.TYPE_INT_ARGB);
		for (int y = 0; y < 64; y++) {
			for (int x = 0; x < 64; x++) painted.setRGB(x, y, 0xFF808080);
		}
		// Every layer solid, as a tool knowing no layers would paint them, but for one hole in the left sleeve's.
		painted.setRGB(50, 50, 0);
		painted.setRGB(20, 20, 0x20808080);
		painted.setRGB(36, 52, 0x20808080);
		painted.setRGB(4, 4, 0x20808080);
		BufferedImage out = processor.process(painted);
		results.check("skins: a solid hat is cleared, as beta clears it", alpha(out, 40, 8) == 0);
		results.check("skins: a solid jacket, sleeve and trouser are cleared by the hat's rule",
				alpha(out, 20, 36) == 0 && alpha(out, 44, 36) == 0 && alpha(out, 4, 36) == 0 && alpha(out, 4, 52) == 0);
		results.check("skins: a layer with a hole keeps its pixels", alpha(out, 52, 52) == 255);
		results.check("skins: head, body, arms and legs are opaque whatever the file says",
				alpha(out, 4, 4) == 255 && alpha(out, 20, 20) == 255 && alpha(out, 36, 52) == 255);
		BufferedImage classic = processor.process(new BufferedImage(64, 32, BufferedImage.TYPE_INT_ARGB));
		results.check("skins: a 64x32 skin goes to beta's processor", classic.getHeight() == 32 && !(classic instanceof ModernSkin));
		BufferedImage large = processor.process(new BufferedImage(128, 128, BufferedImage.TYPE_INT_ARGB));
		results.check("skins: any other size goes to beta's processor", large.getHeight() == 32 && !(large instanceof ModernSkin));
		ModernSkin.profileRead("not a profile");
		ModernSkin.profileRead("{\"name\":\"QuireOdd\",\"properties\":[{\"name\":\"textures\",\"value\":\"!!\"}]}");
		ModernSkin.profileRead("{\"name\":\"QuireOdd\",\"properties\":7}");
		results.check("skins: an odd profile is read as no flag, without an error", true);
	}

	private static int alpha(BufferedImage image, int x, int y) {
		return image.getRGB(x, y) >>> 24;
	}

	// ---- Plan

	private void sides(String name) {
		boolean loud = name.equals(TestSkins.WIDE) || name.equals(TestSkins.SLIM) || name.equals(TestSkins.OLD);
		shot(name + "-front", figure(name, FRONT, null), loud ? img -> parts(name, "front", img, false) : null);
		shot(name + "-back", figure(name, BACK, null), loud ? img -> parts(name, "back", img, true) : null);
		shot(name + "-right", figure(name, RIGHT_SIDE, null), null);
		shot(name + "-left", figure(name, LEFT_SIDE, null), null);
	}

	private void from(String name, String label, double above, int pitch) {
		Figure f = figures.get(name);
		shot(name + "-" + label, c -> {
			f.reset();
			place(f, ox, EYE + above, oz, FRONT);
			cameraPitch = pitch;
		}, null);
	}

	private void poses(String name) {
		Figure f = figures.get(name);
		shot(name + "-walk", figure(name, 215, x -> {
			x.walkAnimationProgress = 2.35F;
			x.walkAnimationSpeed = x.lastWalkAnimationSpeed = 1.0F;
		}), null);
		shot(name + "-swing", figure(name, 215, x -> x.swing = 0.35F), null);
		shot(name + "-sneak", figure(name, RIGHT_SIDE, x -> x.sneaking = true), null);
		shot(name + "-sneak-front", figure(name, FRONT, x -> x.sneaking = true), null);
		shot(name + "-ride", figure(name, 215, x -> x.riding = true), null);
		shot(name + "-sleep", c -> {
			f.reset();
			f.sleeping = true;
			// Lying, a sleeper's feet are where it stands and its head 1.6 towards -x.
			place(f, ox + 0.8, FEET - 0.6, oz + 1.8, 0);
			cameraPitch = 35;
		}, null);
		shot(name + "-hurt", figure(name, FRONT, x -> x.hurtTime = 5), null);
		shot(name + "-bow", figure(name, 215, x -> x.inventory.main[x.inventory.selectedSlot] = new ItemStack(Item.BOW)), null);
		shot(name + "-sword", figure(name, 215, x -> x.inventory.main[x.inventory.selectedSlot] = new ItemStack(Item.DIAMOND_SWORD)), null);
		shot(name + "-block", figure(name, 215, x -> x.inventory.main[x.inventory.selectedSlot] = new ItemStack(Block.STONE)), null);
		shot(name + "-armour", figure(name, 215, x -> {
			x.inventory.armor[0] = new ItemStack(Item.IRON_BOOTS);
			x.inventory.armor[1] = new ItemStack(Item.IRON_LEGGINGS);
			x.inventory.armor[2] = new ItemStack(Item.IRON_CHESTPLATE);
			x.inventory.armor[3] = new ItemStack(Item.IRON_HELMET);
		}), null);
		shot(name + "-armour-ride", figure(name, 215, x -> {
			x.riding = true;
			x.inventory.armor[1] = new ItemStack(Item.IRON_LEGGINGS);
			x.inventory.armor[2] = new ItemStack(Item.IRON_CHESTPLATE);
		}), null);
	}

	private void mobs() {
		Entity[] mobs = {new StillZombie(world), new StillSkeleton(world)};
		String[] names = {"zombie", "skeleton"};
		for (Entity e : mobs) enter(e);
		for (int i = 0; i < mobs.length; i++) {
			Entity mob = mobs[i];
			for (int yaw : new int[] {FRONT, 215}) {
				shot(names[i] + "-" + yaw, c -> place(mob, ox, FEET, oz + AWAY, yaw), null);
			}
		}
	}

	/** The player's own: the hand, third person and the inventory, wearing each skin in turn. */
	private void own() {
		for (String name : new String[] {TestSkins.WIDE, TestSkins.SLIM, TestSkins.OLD, TestSkins.SUIT, TestSkins.SUIT_SLIM}) {
			plan.add(c -> {
				wear(c, skin(name));
				c.options.hideHud = false;
				holdsHand = true;
				showing = null;
				onShow = null;
				for (Entity e : stage) park(e);
				return true;
			});
			plan.add(wait(30));
			shot(name + "-hand", c -> {
			}, name.equals(TestSkins.WIDE) ? this::sleeveInHand : null);
			plan.add(c -> {
				c.player.inventory.main[c.player.inventory.selectedSlot] = new ItemStack(Item.MAP);
				return true;
			});
			plan.add(wait(30));
			// Beta lowers a map, and the hands with it, unless you look down.
			shot(name + "-map", c -> cameraPitch = 60, null);
			plan.add(c -> {
				c.player.inventory.main[c.player.inventory.selectedSlot] = null;
				cameraPitch = 0;
				c.options.hideHud = true;
				c.options.thirdPerson = true;
				return true;
			});
			plan.add(wait(30));
			shot(name + "-third-person", c -> {
			}, null);
			plan.add(c -> {
				c.options.thirdPerson = false;
				c.setScreen(new InventoryScreen(c.player));
				return true;
			});
			plan.add(wait(3));
			shot(name + "-inventory", c -> {
			}, null);
			plan.add(c -> {
				c.setScreen(null);
				holdsHand = false;
				return true;
			});
		}
	}

	private void wear(Minecraft mc, String url) {
		mc.worldRenderer.notifyEntityRemoved(mc.player);
		mc.player.skinUrl = url;
		mc.worldRenderer.notifyEntityAdded(mc.player);
	}

	private Consumer<Minecraft> figure(String name, int yaw, Consumer<Figure> pose) {
		Figure f = figures.get(name);
		return c -> {
			f.reset();
			if (pose != null) pose.accept(f);
			place(f, ox, FEET, oz + AWAY, yaw);
		};
	}

	private void place(Entity e, double x, double y, double z, float yaw) {
		if (showing != null && showing != e) park(showing);
		showing = e;
		onShow = e;
		drawn = 0;
		cameraPitch = 0;
		e.setPositionAndAnglesKeepPrevAngles(x, y, z, yaw, 0);
		e.prevYaw = yaw;
		e.prevPitch = 0;
		if (e instanceof net.minecraft.entity.LivingEntity) {
			net.minecraft.entity.LivingEntity l = (net.minecraft.entity.LivingEntity) e;
			l.bodyYaw = l.lastBodyYaw = yaw;
		}
		if (e instanceof PlayerEntity) {
			PlayerEntity p = (PlayerEntity) e;
			p.capeX = p.prevCapeX = x;
			p.capeY = p.prevCapeY = y;
			p.capeZ = p.prevCapeZ = z;
		}
	}

	private Step wait(int ticks) {
		int[] left = {ticks};
		return c -> --left[0] <= 0;
	}

	private Step until(String what, BooleanSupplier done) {
		int[] left = {200};
		return c -> {
			if (done.getAsBoolean()) return true;
			if (--left[0] > 0) return false;
			results.check("skins: waited for " + what, false);
			return true;
		};
	}

	private void shot(String name, Consumer<Minecraft> set, Consumer<BufferedImage> check) {
		File file = new File(OUT, name + ".png");
		plan.add(c -> {
			set.accept(c);
			return true;
		});
		plan.add(wait(3));
		// A busy GPU can draw fewer frames than ticks: the figure must have been drawn in a couple of frames first.
		int[] patience = {200};
		plan.add(c -> {
			if (onShow == null || drawn >= 2) return true;
			if (--patience[0] > 0) return false;
			results.check("skins: " + name + ": the figure was drawn", false);
			return true;
		});
		ScreenCapture[] capture = {null};
		plan.add(c -> {
			if (capture[0] == null) capture[0] = ScreenCapture.request(file, -1, -1);
			if (!capture[0].done()) return false;
			if (check != null) {
				try {
					check.accept(ImageIO.read(file));
				} catch (IOException e) {
					results.fail("skins: reading " + name, e);
				}
			}
			return true;
		});
	}

	@Override
	public boolean tick(Minecraft mc) {
		hold(mc);
		while (!plan.isEmpty()) {
			if (!plan.peek().tick(mc)) return false;
			plan.remove();
		}
		restore(mc);
		return true;
	}

	/** The camera where it should be, the sun high. */
	private void hold(Minecraft mc) {
		PlayerEntity p = mc.player;
		p.noClip = true;
		p.velocityX = p.velocityY = p.velocityZ = 0;
		p.fireTicks = 0;
		p.health = 20;
		p.setPositionAndAnglesKeepPrevAngles(ox, G, oz, 0.0F, (float) cameraPitch);
		p.prevYaw = p.yaw;
		p.prevPitch = p.pitch;
		if (!holdsHand) p.inventory.main[p.inventory.selectedSlot] = null;
		world.setTime(6000);
		world.setRainGradient(0.0F);
	}

	private void restore(Minecraft mc) {
		for (Entity e : stage) world.remove(e);
		for (int i = under.size() - 1; i >= 0; i--) {
			int[] b = under.get(i);
			world.setBlock(b[0], b[1], b[2], b[3]);
		}
		mc.options.difficulty = (Integer) saved[0];
		mc.options.hideHud = (Boolean) saved[1];
		mc.options.bobView = (Boolean) saved[2];
		mc.options.thirdPerson = (Boolean) saved[3];
		wear(mc, ownSkin);
		mc.player.noClip = false;
		mc.player.setPositionAndAnglesKeepPrevAngles((Double) saved[4], (Double) saved[5], (Double) saved[6], 0.0F, 0.0F);
		DuskCheck.frozen = false;
		still = false;
		onShow = null;
		if (swapInterval >= 0) org.lwjgl.opengl.Display.setSwapInterval(swapInterval);
	}

	/** What our LWJGL component last set, or -1 where it isn't the one running. */
	private static int swapInterval() {
		try {
			return (Integer) Class.forName("org.studioquire.oldfaithful.lwjgl.Window").getMethod("swapInterval").invoke(null);
		} catch (ReflectiveOperationException | RuntimeException e) {
			return -1;
		}
	}

	// ---- Reading pictures

	/** Where a part's hue is in a picture: how many pixels, and the columns they span. */
	private static final class Seen {
		int count, minX = Integer.MAX_VALUE, maxX = -1;
		long sumX;

		double middle() {
			return (double) sumX / count;
		}
	}

	private static Seen[] look(BufferedImage image) {
		Seen[] seen = new Seen[12];
		for (int i = 0; i < 12; i++) seen[i] = new Seen();
		float[] hsb = new float[3];
		for (int y = 0; y < image.getHeight(); y++) {
			for (int x = 0; x < image.getWidth(); x++) {
				int rgb = image.getRGB(x, y);
				Color.RGBtoHSB(rgb >> 16 & 0xFF, rgb >> 8 & 0xFF, rgb & 0xFF, hsb);
				if (hsb[1] < 0.8F || hsb[2] < 0.15F) continue;
				float step = hsb[0] * 12;
				int part = Math.round(step);
				if (Math.abs(step - part) > 0.25F) continue;
				Seen s = seen[part % 12];
				s.count++;
				s.sumX += x;
				s.minX = Math.min(s.minX, x);
				s.maxX = Math.max(s.maxX, x);
			}
		}
		return seen;
	}

	/** Which parts a loud skin shows on which side of a figure that faces the camera, or its back to it. */
	private void parts(String name, String view, BufferedImage image, boolean fromBehind) {
		Seen[] s = look(image);
		boolean whole = on && !name.equals(TestSkins.OLD);
		double middle = image.getWidth() / 2.0;
		int[] always = {TestSkins.BODY, TestSkins.HEAD, TestSkins.HAT, TestSkins.RIGHT_ARM, TestSkins.RIGHT_LEG};
		int[] layers = {TestSkins.LEFT_ARM, TestSkins.LEFT_LEG, TestSkins.JACKET, TestSkins.RIGHT_SLEEVE, TestSkins.LEFT_SLEEVE, TestSkins.RIGHT_TROUSER, TestSkins.LEFT_TROUSER};
		String label = "skins: " + name + " from the " + view + ": ";
		for (int part : always) results.check(label + "part " + part + " shows (" + s[part].count + " pixels)", s[part].count > PRESENT);
		for (int part : layers) {
			results.check(label + "part " + part + (whole ? " shows" : " doesn't") + " (" + s[part].count + " pixels)", s[part].count > PRESENT == whole);
		}
		if (whole) {
			// A figure's right hand is on the left of the picture when it faces the camera.
			for (int part : new int[] {TestSkins.RIGHT_ARM, TestSkins.RIGHT_SLEEVE, TestSkins.RIGHT_LEG, TestSkins.RIGHT_TROUSER}) {
				results.check(label + "part " + part + " is on the figure's right", s[part].middle() < middle != fromBehind);
			}
			for (int part : new int[] {TestSkins.LEFT_ARM, TestSkins.LEFT_SLEEVE, TestSkins.LEFT_LEG, TestSkins.LEFT_TROUSER}) {
				results.check(label + "part " + part + " is on the figure's left", s[part].middle() > middle != fromBehind);
			}
		} else {
			results.check(label + "the right arm's texture is on both arms, as beta mirrors it", s[TestSkins.RIGHT_ARM].minX < middle && s[TestSkins.RIGHT_ARM].maxX > middle);
			results.check(label + "the right leg's texture is on both legs", s[TestSkins.RIGHT_LEG].minX < middle && s[TestSkins.RIGHT_LEG].maxX > middle);
		}
		if (view.equals("front")) widths.put(name, s[TestSkins.RIGHT_ARM].maxX - s[TestSkins.RIGHT_ARM].minX + 1);
		if (on && widths.containsKey(TestSkins.WIDE) && widths.containsKey(TestSkins.SLIM) && !armsTold) {
			armsTold = true;
			double ratio = (double) widths.get(TestSkins.SLIM) / widths.get(TestSkins.WIDE);
			results.check(String.format("skins: a slim arm is %.2f of a wide one's width, about three quarters", ratio), ratio > 0.65 && ratio < 0.85);
		}
	}

	private final Map<String, Integer> widths = new LinkedHashMap<>();
	private boolean armsTold;

	/** With the switch, the hand's checkerboard shows the sleeve's hue over the arm's. */
	private void sleeveInHand(BufferedImage image) {
		Seen[] s = look(image);
		results.check("skins: the first-person arm shows (" + s[TestSkins.RIGHT_ARM].count + " pixels)", s[TestSkins.RIGHT_ARM].count > PRESENT);
		results.check("skins: its sleeve " + (on ? "shows" : "doesn't") + " (" + s[TestSkins.RIGHT_SLEEVE].count + " pixels)", s[TestSkins.RIGHT_SLEEVE].count > PRESENT == on);
	}
}
