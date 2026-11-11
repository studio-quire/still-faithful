package org.studioquire.oldfaithful.selftest;

import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.List;
import java.util.Map;

import net.minecraft.client.Minecraft;
import net.minecraft.client.sound.Sound;

import org.studioquire.oldfaithful.hardening.Services;
import org.studioquire.oldfaithful.hardening.assets.BetaSounds;
import org.studioquire.oldfaithful.hardening.mixin.client.SoundEntryAccessor;
import org.studioquire.oldfaithful.selftest.mixin.SoundManagerAccessor;

/**
 * Beta's own bow, door and explosion sounds in the running game (the hardening's {@link BetaSounds}): each of the four
 * pools holds one sound, and its file is the pinned one. The game's tests copy those files into the game folder's
 * cache as they copy its sounds, so they're in place before the first load; without them the game fetches them, and
 * this waits for that.
 */
final class BetaSoundsCheck {
	private static final long WAIT_MS = 30_000;
	private static long asked;

	private BetaSoundsCheck() {
	}

	/** True while the game is still fetching them, for at most half a minute. */
	static boolean waiting() {
		if (Services.BETA_SOUNDS.settled()) return false;
		if (asked == 0) asked = System.currentTimeMillis();
		return System.currentTimeMillis() - asked < WAIT_MS;
	}

	@SuppressWarnings("unchecked")
	static void check(Minecraft mc, SelfTest results) {
		try {
			Map<String, List<Sound>> pools = ((SoundEntryAccessor) ((SoundManagerAccessor) mc.soundManager).selftest$sounds()).oldfaithful$pools();
			for (BetaSounds.Pinned p : BetaSounds.PINNED) {
				List<Sound> pool = pools.get(p.pool);
				if (pool == null || pool.size() != 1) {
					results.check("sounds: " + p.pool + " holds beta's own recording alone (it holds "
							+ (pool == null ? "nothing" : pool.size() + ": " + ids(pool)) + ")", false);
					continue;
				}
				byte[] bytes = Files.readAllBytes(Paths.get(pool.get(0).soundFile.toURI()));
				String sha = BetaSounds.sha256(bytes);
				results.check("sounds: " + p.pool + " holds beta's own recording alone (" + pool.get(0).id + ", SHA-256 "
						+ sha.substring(0, 8) + "...)", p.sha256.equals(sha));
			}
		} catch (Exception e) {
			results.fail("sounds: beta's own recordings", e);
		}
	}

	private static String ids(List<Sound> pool) {
		StringBuilder sb = new StringBuilder();
		for (Sound s : pool) sb.append(sb.length() == 0 ? "" : ", ").append(s.id);
		return sb.toString();
	}
}
