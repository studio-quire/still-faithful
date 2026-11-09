package org.studioquire.oldfaithful.selftest;

import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.SingleplayerInteractionManager;
import net.minecraft.client.gui.screen.MultiplayerScreen;
import net.minecraft.client.gui.screen.TitleScreen;
import net.minecraft.client.gui.widget.TextFieldWidget;
import org.lwjgl.Sys;
import org.lwjgl.opengl.Display;
import org.lwjgl.opengl.GL11;
import org.lwjgl.sdl.SDLEvents;
import org.lwjgl.sdl.SDLHints;
import org.lwjgl.sdl.SDLVideo;
import org.lwjgl.sdl.SDLKeycode;
import org.lwjgl.sdl.SDLTimer;
import org.lwjgl.sdl.SDL_Event;
import org.studioquire.oldfaithful.hardening.Branding;
import org.studioquire.oldfaithful.lwjgl.Settings;
import org.studioquire.oldfaithful.lwjgl.Window;
import org.studioquire.oldfaithful.selftest.mixin.MultiplayerScreenAccessor;

/**
 * The client start-up test's in-game half (`:harness:clientStartupTest`, launched with
 * {@code -Doldfaithful.selftest.startup=true} on our LWJGL with a hidden window): from the game's own tick, it checks
 * the main menu came up as a player's would (title, hidden window, mouse setting, sound), pastes into the server
 * address with a synthetic Ctrl+V, then creates and loads a small world, lets it run a moment and stops the game the
 * way its close button does. Results are {@code [oldfaithful-selftest]} lines; the task reads them and the log.
 * <p>
 * The window's placement is checked against what the task launched with ({@code oldfaithful.selftest.expect*}: the
 * launcher's window setting, or a remembered placement). With {@code oldfaithful.selftest.menuOnly} the run stops
 * after the main menu; {@code oldfaithful.selftest.rearrange=<w>x<h>@<x>,<y>} first restores, resizes and moves the
 * hidden window as a player would, then maximises it, so the task can read back what the display mod kept.
 */
public final class StartupCheck {
	public static final boolean ENABLED = Boolean.getBoolean("oldfaithful.selftest.startup");
	private static final String WORLD = "oldfaithful-startup";
	/** A session holding the hidden world open longer sets this, to ask something of it from outside. */
	private static final int WORLD_TICKS = Integer.getInteger("oldfaithful.selftest.worldTicks", 100);
	private static final boolean MENU_ONLY = Boolean.getBoolean("oldfaithful.selftest.menuOnly");
	/** A first start of Old Faithful (no options.txt yet): checked at the title screen, which it then quits by its button. */
	private static final boolean FIRST_START = Boolean.getBoolean("oldfaithful.selftest.firstStart");
	private static final String REARRANGE = System.getProperty("oldfaithful.selftest.rearrange");
	/** host:port of a test server to join instead of making a world (MultiplayerCheck). */
	private static final String JOIN = System.getProperty("oldfaithful.selftest.joinServer");
	private static final long STALL_MS = Long.getLong("oldfaithful.selftest.stallMs", 150_000L);
	/** The world checks of other mods alone: no edition's own checks, which expect its switches as they ship (`:harness:libraryTest`). */
	private static final boolean WORLD_CHECKS_ONLY = Boolean.getBoolean("oldfaithful.selftest.joinWorldChecks") || Boolean.getBoolean("oldfaithful.selftest.worldChecksOnly");
	/** The settings screen's checks alone, past the edition's own (`clientStartupTestLibrary`). */
	private static final boolean SETTINGS_ONLY = Boolean.getBoolean("oldfaithful.selftest.settingsOnly");
	/** The conflict harness: a world that's missing this mod, whose warning screen is checked and gone through. */
	private static final String EXPECT_MISSING = System.getProperty("oldfaithful.selftest.expectMissing");
	/** The conflict harness: host:port of a server to try, reporting what screen it ends on instead of playing. */
	private static final String TRY_SERVER = System.getProperty("oldfaithful.selftest.tryServer");
	/** After a refusal's drawn: presses Match This Server's Mods and reports what the screen says then (`libraryPackTest`). */
	private static final boolean USE_SET = Boolean.getBoolean("oldfaithful.selftest.useServerSet");
	/** The conflict harness: host:port of a hostile server, joined again and again until its kick says it's done. */
	private static final String HOSTILE = System.getProperty("oldfaithful.selftest.hostile");
	/**
	 * {@code -Ptest.only=fence,chat} on an edition's start-up test (docs/building.md): the checks in the world whose names
	 * hold one of these and no others, for iterating on one thing. The names are fixes, options, settings and each
	 * {@link WorldCheck}'s class (StillCheck, FenceCheck, DuskCheck...), in any case. Null for the whole test.
	 */
	private static final java.util.List<String> ONLY = only(System.getProperty("oldfaithful.selftest.only"));
	/** "<name>:<mod id>" for an edition beyond Old Faithful: its title line, and the name its settings button carries. */
	static final String EDITION = System.getProperty("oldfaithful.selftest.edition");
	/** Other mods' test-only checks in the running world, after the Fixed edition's ({@link WorldCheck}). */
	private static java.util.List<WorldCheck> worldChecks;
	private static int worldCheck;
	private static final long JOIN_LIMIT_MS = 90_000L;
	private static long joinStarted;
	private static int refusedTicks;

	private static final SelfTest results = new SelfTest();
	private static int phase;
	private static int wait;
	/** Passes of the game's loop that drew (GameRendererStartupMixin), each after one read of the window's events. */
	public static int frames;
	private static int pausedUntilFrame;
	private static int worldTicks;
	private static FixesCheck fixes;
	private static OptionsCheck options;
	private static SettingsCheck settings;
	private static MultiplayerCheck multiplayer;
	/** Still Faithful's fences walked into on a test server instead of the multiplayer fixes' checks. */
	private static final boolean FENCE_WALK = Boolean.getBoolean("oldfaithful.selftest.fenceWalk");
	private static FenceWalk fenceWalk;
	private static int fenceX = Integer.MIN_VALUE, fenceZ;
	private static SceneShot scene;
	private static LedgeHunt hunt;
	private static long startedAt;
	private static long framesReadAt;
	private static int frameSeconds, frameTotal, worldSeconds, worldFrames, fewestFrames = Integer.MAX_VALUE;
	private static String clipboard;
	private static int pasteTries;
	/** Rows of beta's version-line grey in the title screen's corner, counted as separate lines; -1 until drawn. */
	private static volatile int titleLines = -1;

	private StartupCheck() {
	}

	public static void tick(Minecraft mc) {
		if (startedAt == 0) startedAt = System.currentTimeMillis();
		readFrames(mc);
		if (phase < 99 && System.currentTimeMillis() - startedAt > STALL_MS) {
			results.fail("start-up test stalled in phase " + phase + " (screen " + mc.currentScreen + ")", new AssertionError());
			finish(mc);
			return;
		}
		try {
			step(mc);
		} catch (Throwable t) {
			results.fail("start-up test, phase " + phase, t);
			finish(mc);
		}
	}

	/**
	 * The swap interval: what our layer asked for must be what the display mod's setting says, and the GL must report
	 * it in effect. A software GL with no display to sync to (Mesa's llvmpipe under Xvfb, on the Linux test images)
	 * accepts the request and keeps 0, or refuses it; there only the request is checked, and the output says so.
	 */
	private static void vsyncCheck() {
		int expected = Settings.vsync ? 1 : 0;
		int asked = Window.swapInterval();
		java.nio.IntBuffer interval = org.lwjgl.BufferUtils.createIntBuffer(1);
		boolean readable = SDLVideo.SDL_GL_GetSwapInterval(interval);
		String inEffect = readable ? String.valueOf(interval.get(0)) : "unreadable";
		String renderer = String.valueOf(GL11.glGetString(GL11.GL_RENDERER));
		boolean noSwapControl = Window.swapIntervalRefused() != null
				|| renderer.contains("llvmpipe") || renderer.contains("softpipe");
		if (readable && interval.get(0) == expected) {
			results.check("vsync: swap interval " + inEffect + ", as the display mod's setting asks (" + expected + ")", asked == expected);
		} else if (noSwapControl) {
			results.check("vsync: asked for swap interval " + asked + ", as the display mod's setting asks (" + expected + "); this GL ("
					+ renderer + ") has no display to sync to and keeps " + inEffect
					+ (Window.swapIntervalRefused() != null ? " (refused: " + Window.swapIntervalRefused() + ")" : "")
					+ ", so only the request can be checked here", asked == expected);
		} else {
			results.check("vsync: swap interval " + inEffect + ", as the display mod's setting asks (" + expected + ")", false);
		}
	}

	/**
	 * The line says which edition this is: Old Faithful's name and version where its fix mod is, else Pure Faithful's;
	 * a run of another edition names it ({@link #EDITION}: its name, and the mod whose version it carries).
	 */
	private static void titleLineCheck() {
		FabricLoader loader = FabricLoader.getInstance();
		String expected = EDITION != null
				? EDITION.split(":")[0] + " " + loader.getModContainer(EDITION.split(":")[1]).get().getMetadata().getVersion().getFriendlyString()
				: loader.getModContainer("oldfaithful-fixes")
				.map(m -> "Old Faithful " + m.getMetadata().getVersion().getFriendlyString())
				.orElseGet(() -> "Pure Faithful " + loader.getModContainer("purefaithful-hardening").get().getMetadata().getVersion().getFriendlyString());
		results.check("the title line reads \"" + Branding.TEXT + "\" (expected \"" + expected + "\")", expected.equals(Branding.TEXT));
	}

	private static void step(Minecraft mc) throws Exception {
		switch (phase) {
			case 0:
				if (!(mc.currentScreen instanceof TitleScreen) || titleLines < 0) return;
				results.pass("main menu reached");
				if (LedgeHunt.ENABLED) {
					// `:harness:ledgeHunt`: straight into a world; none of the menu's checks, so it runs on any set of mods.
					org.lwjgl.opengl.Display.setSwapInterval(0);
					phase = 3;
					return;
				}
				results.check("title screen: \"Minecraft Beta 1.7.3\" and the edition's line under it, in beta's grey (" + titleLines
						+ " lines; title-corner.png in the run folder)", titleLines == 2);
				titleLineCheck();
				results.check("window title is \"Minecraft\", as on LWJGL 2: \"" + Display.getTitle() + "\"", "Minecraft".equals(Display.getTitle()));
				results.check("window created and never shown, never active", Display.isCreated() && !Display.isVisible() && !Display.isActive());
				results.check("mouse: system pointer speed and acceleration (the display mod's default)", !Settings.rawMouse
						&& "1".equals(SDLHints.SDL_GetHint(SDLHints.SDL_HINT_MOUSE_RELATIVE_SYSTEM_SCALE)));
				if (!WORLD_CHECKS_ONLY) vsyncCheck();
				// Hidden clients waiting on the refresh stall the user's display, so vsync is off from here on; the
				// settings check turns it on for its own VSync button only, after vsyncCheck has seen it as it ships.
				org.lwjgl.opengl.Display.setSwapInterval(0);
				windowChecks(mc);
				fullscreenChecks(mc);
				if (OptionsCheck.loaded() && !WORLD_CHECKS_ONLY) OptionsCheck.titleScreen(results, mc.currentScreen);
				if (FIRST_START) OptionsCheck.firstStart(results, mc);
				System.out.println("[oldfaithful-selftest] GL: " + GL11.glGetString(GL11.GL_VERSION) + " / " + GL11.glGetString(GL11.GL_RENDERER));
				if (REARRANGE != null) {
					phase = 10;
				} else if (TRY_SERVER != null || HOSTILE != null) {
					connect(mc, TRY_SERVER != null ? TRY_SERVER : HOSTILE);
					phase = TRY_SERVER != null ? 41 : 42;
				} else if (JOIN != null) {
					// `:harness:fixesMultiplayerTest`: the multiplayer fixes, on a test server.
					String[] hostPort = JOIN.split(":");
					mc.setScreen(new net.minecraft.client.gui.screen.ConnectScreen(mc, hostPort[0], Integer.parseInt(hostPort[1])));
					if (FENCE_WALK) {
						// `:harness:fencesMultiplayerTest`: Still Faithful's fences walked into on a test server.
						phase = 23;
						return;
					}
					if (Boolean.getBoolean("oldfaithful.selftest.joinWorldChecks")) {
						// `:harness:libraryRulesTest`: another mod's world checks, as a client on a server.
						phase = 21;
						return;
					}
					multiplayer = new MultiplayerCheck(results);
					phase = 20;
				} else if (MENU_ONLY) {
					finish(mc);
				} else {
					mc.setScreen(new MultiplayerScreen(mc.currentScreen));
					phase = 1;
				}
				return;
			case 1:
				if (!(mc.currentScreen instanceof MultiplayerScreen)) return;
				// Read now, as the paste will: the test never writes the clipboard (it's the user's) or prints it.
				clipboard = Sys.getClipboard();
				pushKey(224, SDLKeycode.SDL_KMOD_LCTRL, true);
				pushKey(25, SDLKeycode.SDL_KMOD_LCTRL, true);
				pushKey(25, SDLKeycode.SDL_KMOD_LCTRL, false);
				pushKey(224, 0, false);
				phase = 2;
				pause(5);
				return;
			case 2:
				if (pausing()) return;
				TextFieldWidget field = ((MultiplayerScreenAccessor) mc.currentScreen).oldfaithful$serverField();
				String c = clipboard == null ? "" : clipboard;
				String expected = c.substring(0, Math.min(32, c.length()));
				// The clipboard is the desktop's, shared with whoever is at it: something copied between the read and the
				// paste, or held open by another program at the paste (which reads as empty), isn't ours. Read and paste
				// again then, twice at most; a paste of an unchanged clipboard that comes out wrong fails at once.
				if (!expected.equals(field.getText()) && pasteTries < 2) {
					String now = Sys.getClipboard();
					if (!c.equals(now == null ? "" : now) || field.getText().isEmpty()) {
						pasteTries++;
						field.setText("");
						phase = 1;
						return;
					}
				}
				results.check("paste: Ctrl+V in the server address field pastes the clipboard through SDL ("
						+ (c.isEmpty() ? "the clipboard holds no text, so only the key path ran" : expected.length() + " characters, contents not shown")
						+ (pasteTries > 0 ? "; read again " + pasteTries + " times, the clipboard changed or was busy" : "") + ")",
						expected.equals(field.getText()));
				mc.setScreen(new TitleScreen());
				phase = 3;
				return;
			case 3:
				if (!(mc.currentScreen instanceof TitleScreen)) return;
				results.pass("back at the main menu");
				// As the Create World screen does.
				if (EXPECT_MISSING != null) {
					// Through the world list, as a player gets there: it clears the screen on either side of asking for the world.
					net.minecraft.client.gui.screen.world.SelectWorldScreen list = new net.minecraft.client.gui.screen.world.SelectWorldScreen(mc.currentScreen);
					mc.setScreen(list);
					list.selectWorld(0);
					phase = 40;
					return;
				}
				mc.interactionManager = new SingleplayerInteractionManager(mc);
				mc.startGame(WORLD, "Old Faithful start-up test", SceneShot.ENABLED ? SceneShot.SEED : LedgeHunt.ENABLED ? LedgeHunt.SEED : 7373L);
				mc.setScreen(null);
				phase = 4;
				return;
			case 40:
				// The core's screen, as a player meets it: drawn, then gone through.
				if (mc.currentScreen == null || !mc.currentScreen.getClass().getSimpleName().equals("MissingContentScreen")) return;
				if (++wait < 10) return;
				wait = 0;
				String shown = LibraryScreens.text();
				results.check("missing content screen: drawn, naming " + EXPECT_MISSING + " (" + shown + ")", shown.contains(EXPECT_MISSING) && shown.contains("aren't here"));
				LibraryScreens.shot("missing-content");
				phase = 43;
				return;
			case 43:
				if (++wait < 3) return;
				wait = 0;
				results.check("missing content screen: Make a Backup and Play", LibraryScreens.click(mc.currentScreen, "Make a Backup and Play"));
				phase = 4;
				return;
			case 41:
				// The harness's refusals: what a player ends up looking at.
				if (mc.world != null && mc.currentScreen == null) {
					if (++wait < 40) return;
					System.out.println("[library-harness] SCREEN admitted");
					finish(mc);
					return;
				}
				if (!refusal(mc)) return;
				if (wait == 0) LibraryScreens.clear();
				if (++wait < 15) return;
				wait = 0;
				System.out.println("[library-harness] SCREEN " + mc.currentScreen.getClass().getSimpleName() + " | " + LibraryScreens.text());
				results.pass("the refusal was drawn");
				LibraryScreens.shot("refusal");
				phase = 44;
				return;
			case 44:
				if (++wait < 3) return;
				if (USE_SET) {
					wait = 0;
					results.check("Match This Server's Mods is on the refusal", LibraryScreens.click(mc.currentScreen, "Match This Server's Mods"));
					LibraryScreens.clear();
					phase = 45;
					return;
				}
				finish(mc);
				return;
			case 45:
				if (++wait < 15) return;
				System.out.println("[library-harness] SET " + LibraryScreens.text());
				finish(mc);
				return;
			case 42:
				// A hostile server: each attack must end in a screen, and the next connection follows.
				if (!refusal(mc)) return;
				if (wait == 0) LibraryScreens.clear();
				if (++wait < 4) return;
				wait = 0;
				String said = LibraryScreens.text();
				LibraryScreens.clear();
				System.out.println("[library-harness] SCREEN " + mc.currentScreen.getClass().getSimpleName() + " | " + said);
				if (said.contains("harness-done")) {
					results.pass("the hostile server's attacks all ended in a screen");
					finish(mc);
				} else {
					connect(mc, HOSTILE);
				}
				return;
			case 4:
				if (mc.world == null || mc.player == null) return;
				if (++worldTicks < WORLD_TICKS) return;
				results.check("world created, loaded and running: player at y " + Math.round(mc.player.y) + ", world time " + mc.world.getTime()
						+ " after " + worldTicks + " game ticks" + (mc.currentScreen == null ? "" : ", but " + mc.currentScreen.getClass().getName() + " is open"), mc.world.getTime() >= WORLD_TICKS / 2 && mc.currentScreen == null);
				results.check("sound system initialised (paulscode on our OpenAL)", paulscode.sound.SoundSystem.initialized());
				if (LedgeHunt.ENABLED) {
					hunt = new LedgeHunt(results);
					phase = 31;
					return;
				}
				if (SceneShot.ENABLED) {
					// `:harness:sceneShot`: pictures of the world instead of the checks in it.
					scene = new SceneShot(results);
					phase = 30;
					return;
				}
				if (SETTINGS_ONLY) {
					settingsOrFinish(mc);
					return;
				}
				if (!WORLD_CHECKS_ONLY && net.fabricmc.loader.api.FabricLoader.getInstance().isModLoaded("oldfaithful-fixes") && wanted("fixes")) {
					fixes = new FixesCheck(results);
					fixes.start(mc);
					phase = 5;
					return;
				}
				optionsOrFinish(mc);
				return;
			case 5:
				// The Fixed edition's fixes in the running world (`clientStartupTestFixed`).
				if (fixes.tick(mc)) optionsOrFinish(mc);
				return;
			case 8:
				if (refused(mc)) return;
				// Another edition's checks (WorldCheck), one after another.
				if (worldChecks.get(worldCheck).tick(mc)) {
					if (++worldCheck < worldChecks.size()) worldChecks.get(worldCheck).start(mc, results);
					else optionsOrFinish(mc);
				}
				return;
			case 6:
				// Old Faithful's options mod in the running world.
				if (options.tick(mc)) settingsOrFinish(mc);
				return;
			case 7:
				// Old Faithful's settings screen.
				if (settings.tick(mc)) finish(mc);
				return;
			case 20:
				if (multiplayer.tick(mc)) finish(mc);
				return;
			case 21:
				if (refused(mc)) return;
				if (joinStarted == 0) joinStarted = System.currentTimeMillis();
				if (System.currentTimeMillis() - joinStarted > JOIN_LIMIT_MS) {
					results.fail("never got into the server's world, on screen " + mc.currentScreen, new AssertionError());
					finish(mc);
					return;
				}
				if (mc.world == null || mc.player == null || mc.currentScreen != null) return;
				if (++worldTicks < 20) return;
				optionsOrFinish(mc);
				return;
			case 23:
				if (refused(mc)) return;
				if (fenceWalk == null) {
					if (joinStarted == 0) joinStarted = System.currentTimeMillis();
					if (System.currentTimeMillis() - joinStarted > JOIN_LIMIT_MS) {
						results.fail("never got into the server's world, or the fence scene never arrived", new AssertionError());
						finish(mc);
						return;
					}
					if (mc.world == null || mc.player == null || mc.currentScreen != null || !mc.player.onGround) return;
					if (fenceX == Integer.MIN_VALUE) {
						fenceX = net.minecraft.util.math.MathHelper.floor(mc.player.x);
						fenceZ = net.minecraft.util.math.MathHelper.floor(mc.player.z) - 20;
						mc.player.sendChatMessage("!oftest fences " + fenceX + " " + fenceZ);
					}
					int y = MultiplayerScenes.FLOOR + 1, fence = net.minecraft.block.Block.FENCE.id;
					if (mc.world.getBlockId(fenceX, y, fenceZ) != fence || mc.world.getBlockId(fenceX + FenceScene.CORNER, y, fenceZ + 1) != fence) return;
					boolean thinClient = Boolean.getBoolean("oldfaithful.selftest.mp.thinClient"), thinServer = Boolean.getBoolean("oldfaithful.selftest.mp.thinServer");
					String label = (thinClient ? "Still Faithful client" : "client without it") + ", " + (thinServer ? "Still Faithful server" : "server without it");
					fenceWalk = new FenceWalk(results, label, thinClient && thinServer,
							(game, x, feet, z, yaw) -> game.player.sendChatMessage("!oftest tp " + x + " " + feet + " " + z + " " + yaw),
							fenceX, MultiplayerScenes.FLOOR, fenceZ);
				}
				if (fenceWalk.tick(mc)) finish(mc);
				return;
			case 30:
				if (scene.tick(mc)) finish(mc);
				return;
			case 31:
				if (hunt.wallDone()) hunt.endNatural();
				if (hunt.tick(mc)) finish(mc);
				return;
			case 10: {
				// As a player would: out of maximised, then resized and moved. The display mod sees it through the
				// window's events only (nothing here asks it), and the maximise after must not overwrite it.
				String[] sizeAt = REARRANGE.split("@");
				String[] wh = sizeAt[0].split("x"), xy = sizeAt[1].split(",");
				long w = Window.handle();
				SDLVideo.SDL_RestoreWindow(w);
				SDLVideo.SDL_SetWindowSize(w, Integer.parseInt(wh[0]), Integer.parseInt(wh[1]));
				SDLVideo.SDL_SetWindowPosition(w, Integer.parseInt(xy[0]), Integer.parseInt(xy[1]));
				phase = 11;
				pause(5);
				return;
			}
			case 11:
				if (pausing()) return;
				SDLVideo.SDL_MaximizeWindow(Window.handle());
				results.check("rearranged: " + Display.getWidth() + "x" + Display.getHeight() + " at " + Display.getX() + "," + Display.getY()
						+ ", then maximised (" + REARRANGE + ")", REARRANGE.equals(Display.getWidth() + "x" + Display.getHeight() + "@" + Display.getX() + "," + Display.getY())
						&& maximized());
				phase = 12;
				pause(5);
				return;
			case 12:
				if (pausing()) return;
				finish(mc);
				return;
			default:
		}
	}

	/**
	 * A wait for something that arrives through the window's events (a key, a resize), which are read once a pass of the
	 * game's loop: so many ticks, and three passes besides. Ticks alone aren't enough: after a slow frame the loop runs
	 * up to ten ticks before it reads events again, and on a busy PC the title screen's first frames are that slow (a
	 * resize unread at its check failed `clientStartupTest`'s window launch, 2026-11-09).
	 */
	private static void pause(int ticks) {
		wait = ticks;
		pausedUntilFrame = frames + 3;
	}

	private static boolean pausing() {
		return --wait > 0 || frames < pausedUntilFrame;
	}

	private static void connect(Minecraft mc, String hostPort) {
		String[] parts = hostPort.split(":");
		mc.setScreen(new net.minecraft.client.gui.screen.ConnectScreen(mc, parts[0], Integer.parseInt(parts[1])));
	}

	/** Whether the game has come to rest on a screen that isn't on the way into a world. */
	private static boolean refusal(Minecraft mc) {
		return mc.currentScreen != null && !(mc.currentScreen instanceof net.minecraft.client.gui.screen.ConnectScreen)
				&& !(mc.currentScreen instanceof net.minecraft.client.gui.screen.DownloadingTerrainScreen);
	}

	private static boolean maximized() {
		return (SDLVideo.SDL_GetWindowFlags(Window.handle()) & SDLVideo.SDL_WINDOW_MAXIMIZED) != 0;
	}

	/**
	 * Where the window opened, against what the task expects: the size (the window's and the game's), the position,
	 * maximised or not. A hidden window holds a maximise pending until it's shown, and its flags say so.
	 */
	private static void windowChecks(Minecraft mc) {
		String size = System.getProperty("oldfaithful.selftest.expectSize");
		if (size != null) {
			String window = Display.getWidth() + "x" + Display.getHeight(), game = mc.displayWidth + "x" + mc.displayHeight;
			results.check("window opened at " + size + ": " + window + ", the game's size " + game, size.equals(window) && size.equals(game));
		}
		String at = System.getProperty("oldfaithful.selftest.expectAt");
		if (at != null) {
			String got = Display.getX() + "," + Display.getY();
			results.check("window opened at position " + at + ": " + got, at.equals(got));
		}
		String max = System.getProperty("oldfaithful.selftest.expectMaximized");
		if (max != null) {
			results.check("window opened " + (Boolean.parseBoolean(max) ? "maximised" : "not maximised") + ": maximised " + maximized(),
					Boolean.parseBoolean(max) == maximized());
		}
	}

	private static boolean sdlFullscreen() {
		return (SDLVideo.SDL_GetWindowFlags(Window.handle()) & SDLVideo.SDL_WINDOW_FULLSCREEN) != 0;
	}

	private static String windowState(Minecraft mc) {
		return Display.getWidth() + "x" + Display.getHeight() + " at " + Display.getX() + "," + Display.getY() + ", maximised " + maximized()
				+ ", the game's size " + mc.displayWidth + "x" + mc.displayHeight + ", SDL fullscreen flag " + sdlFullscreen();
	}

	/**
	 * Fullscreen kept between launches ({@code window.remember}): {@code expectFullscreen} checks the window opened
	 * so; {@code leaveFullscreen} then presses F11's toggle, which must bring back the kept window (the expected size,
	 * position and maximised state again); {@code fullscreenAtStop} goes fullscreen so the run stops in it. Borderless
	 * only (the default): exclusive would change the display's video mode, which the private desktop can't contain.
	 */
	private static void fullscreenChecks(Minecraft mc) {
		String expect = System.getProperty("oldfaithful.selftest.expectFullscreen");
		if (expect != null) {
			boolean want = Boolean.parseBoolean(expect);
			results.check("window opened " + (want ? "fullscreen" : "not fullscreen") + ": " + windowState(mc), Display.isFullscreen() == want);
		}
		if (Boolean.getBoolean("oldfaithful.selftest.leaveFullscreen")) {
			mc.toggleFullscreen();
			String size = System.getProperty("oldfaithful.selftest.expectSize"), at = System.getProperty("oldfaithful.selftest.expectAt");
			results.check("F11 out of fullscreen: back to the kept window (" + size + " at " + at + ", maximised): " + windowState(mc),
					!Display.isFullscreen() && !sdlFullscreen() && maximized()
							&& (Display.getWidth() + "x" + Display.getHeight()).equals(size) && (Display.getX() + "," + Display.getY()).equals(at));
		}
		if (Boolean.getBoolean("oldfaithful.selftest.fullscreenAtStop")) {
			if (Settings.exclusiveFullscreen) throw new IllegalStateException("fullscreenAtStop needs borderless fullscreen");
			mc.toggleFullscreen();
			results.check("F11 into fullscreen, to stop in it: " + windowState(mc), Display.isFullscreen());
		}
	}

	/**
	 * Called after the title screen draws, before the frame is shown: counts the lines in the version text's grey
	 * (b1.7.3's 0x505050, which its darkened background never reaches) above the logo, and saves that corner.
	 */
	public static void titleDrawn(int screenWidth) {
		if (titleLines >= 0) return;
		int scale = Math.max(1, Display.getWidth() / Math.max(1, screenWidth));
		int w = Math.min(Display.getWidth(), 120 * scale);
		int h = Math.min(Display.getHeight(), 28 * scale);
		java.nio.ByteBuffer px = org.lwjgl.BufferUtils.createByteBuffer(w * h * 4);
		GL11.glReadPixels(0, Display.getHeight() - h, w, h, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, px);
		java.awt.image.BufferedImage img = new java.awt.image.BufferedImage(w, h, java.awt.image.BufferedImage.TYPE_INT_RGB);
		int lines = 0;
		boolean inLine = false;
		for (int y = 0; y < h; y++) {
			boolean grey = false;
			for (int x = 0; x < w; x++) {
				int i = ((h - 1 - y) * w + x) * 4;
				int r = px.get(i) & 0xFF, g = px.get(i + 1) & 0xFF, b = px.get(i + 2) & 0xFF;
				img.setRGB(x, y, r << 16 | g << 8 | b);
				if (r == 0x50 && g == 0x50 && b == 0x50) grey = true;
			}
			if (grey && !inLine) lines++;
			inLine = grey;
		}
		try {
			javax.imageio.ImageIO.write(img, "png", new java.io.File("title-corner.png"));
		} catch (java.io.IOException e) {
			throw new java.io.UncheckedIOException(e);
		}
		titleLines = lines;
	}

	/** A client that's been turned away or dropped has nothing to wait for: the run fails now, and says why. */
	private static boolean refused(Minecraft mc) {
		if (JOIN == null || mc.currentScreen == null) return false;
		String screen = mc.currentScreen.getClass().getSimpleName();
		if (!screen.equals("MismatchScreen") && !(mc.currentScreen instanceof net.minecraft.client.gui.screen.DisconnectedScreen)) return false;
		// Its text is read from what's been drawn.
		if (++refusedTicks < 10) return true;
		results.fail("refused by the server, on " + screen + ": " + LibraryScreens.text(), new AssertionError());
		finish(mc);
		return true;
	}

	private static java.util.List<String> only(String names) {
		if (names == null || names.trim().isEmpty()) return null;
		java.util.List<String> only = new java.util.ArrayList<>();
		for (String name : names.split(",")) {
			if (!name.trim().isEmpty()) only.add(name.trim().toLowerCase(java.util.Locale.ROOT));
		}
		return only;
	}

	/** Whether the check of this name runs: all do, unless {@link #ONLY} names some. */
	private static boolean wanted(String name) {
		if (ONLY == null) return true;
		for (String o : ONLY) {
			if (name.toLowerCase(java.util.Locale.ROOT).contains(o)) return true;
		}
		return false;
	}

	/** Says a run of some checks wasn't the whole test, and fails it over a name that is no check's, with the names there are. */
	private static void onlyNote() {
		// "window" is the task's own: its short launches after this one (ClientStartupTask).
		java.util.List<String> names = new java.util.ArrayList<>(java.util.Arrays.asList("fixes", "options", "settings", "window"));
		for (WorldCheck check : FabricLoader.getInstance().getEntrypoints(WorldCheck.ENTRYPOINT, WorldCheck.class)) names.add(check.getClass().getSimpleName());
		java.util.List<String> unknown = new java.util.ArrayList<>();
		for (String o : ONLY) {
			boolean known = false;
			for (String name : names) known |= name.toLowerCase(java.util.Locale.ROOT).contains(o);
			if (!known) unknown.add(o);
		}
		results.check("only " + ONLY + " ran: not the whole test" + (unknown.isEmpty() ? "" : "; no check here is named " + unknown + ", they are " + names), unknown.isEmpty());
	}

	/**
	 * Whether render chunks are waiting to be built in a start-up test's world. The game sleeps 10 ms a frame while its
	 * window lacks focus, which a hidden one always does (about 64 frames a second on Windows' timer), and a frame that
	 * starts late builds one or two chunks, so a view of a few hundred took seconds and every check that waits for one
	 * to be built went at that pace. While chunks wait the loop doesn't sleep (MinecraftBenchMixin), and vanilla builds
	 * them as it does for a window with focus; with none waiting it sleeps as before, so an idle test takes no more CPU.
	 */
	public static boolean chunksWaiting(Minecraft mc) {
		return ENABLED && mc.world != null && mc.worldRenderer != null
				&& !((org.studioquire.oldfaithful.selftest.mixin.WorldRendererAccessor) mc.worldRenderer).oldfaithful$dirtyChunks().isEmpty();
	}

	private static void optionsOrFinish(Minecraft mc) throws ReflectiveOperationException {
		if (worldChecks == null) {
			worldChecks = new java.util.ArrayList<>(FabricLoader.getInstance().getEntrypoints(WorldCheck.ENTRYPOINT, WorldCheck.class));
			worldChecks.removeIf(check -> !wanted(check.getClass().getSimpleName()));
			if (!worldChecks.isEmpty()) {
				worldChecks.get(0).start(mc, results);
				phase = 8;
				return;
			}
		}
		if (!OptionsCheck.loaded() || WORLD_CHECKS_ONLY) {
			finish(mc);
			return;
		}
		if (!wanted("options")) {
			settingsOrFinish(mc);
			return;
		}
		options = new OptionsCheck(results);
		options.start(mc);
		phase = 6;
	}

	private static void settingsOrFinish(Minecraft mc) {
		if (!SettingsCheck.loaded() || !SETTINGS_ONLY && !wanted("settings")) {
			finish(mc);
			return;
		}
		settings = new SettingsCheck(results);
		settings.start(mc);
		phase = 7;
	}

	/** The game's own count, once a second: how the run really went, since a check may lean on what a frame rate gets done. */
	private static void readFrames(Minecraft mc) {
		long now = System.currentTimeMillis();
		if (now - framesReadAt < 1000L || mc.debugText.isEmpty()) return;
		framesReadAt = now;
		System.out.println("[selftest-frames] " + mc.debugText + ", phase " + phase);
		int frames = Integer.parseInt(mc.debugText.substring(0, mc.debugText.indexOf(' ')));
		frameSeconds++;
		frameTotal += frames;
		if (mc.world != null) {
			worldSeconds++;
			worldFrames += frames;
			fewestFrames = Math.min(fewestFrames, frames);
		}
	}

	private static void finish(Minecraft mc) {
		if (frameSeconds > 0) {
			results.pass("frame rate: " + frameTotal / frameSeconds + " fps over " + frameSeconds + " s"
					+ (worldSeconds > 0 ? ", in a world " + worldFrames / worldSeconds + " over " + worldSeconds + " s, lowest " + fewestFrames : ""));
		}
		phase = 99;
		if (ONLY != null) onlyNote();
		results.done();
		if (FIRST_START && OptionsCheck.quit(mc)) return;
		// As the window's close button does: the game saves and stops itself.
		mc.scheduleStop();
	}

	public static void pushKey(int scancode, int mod, boolean down) {
		SDL_Event ev = SDL_Event.calloc();
		try {
			ev.key().type(down ? SDLEvents.SDL_EVENT_KEY_DOWN : SDLEvents.SDL_EVENT_KEY_UP).timestamp(SDLTimer.SDL_GetTicksNS())
					.windowID(Window.windowId()).scancode(scancode).mod((short) mod).down(down);
			SDLEvents.SDL_PushEvent(ev);
		} finally {
			ev.free();
		}
	}
}
