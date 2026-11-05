package org.studioquire.oldfaithful.selftest;

import net.minecraft.client.Minecraft;

/**
 * What a client really has of given cells, for a server-side mod's test: {@code -Doldfaithful.selftest.expectBlocks="x,y,z,id;..."} waits for them all to read so, as chunks arrive, and fails naming
 * those that never did.
 */
public final class BlockCheck implements WorldCheck {
	private static final String EXPECTED = System.getProperty("oldfaithful.selftest.expectBlocks", "");
	private static final int LIMIT = 600;

	private SelfTest results;
	private int ticks;

	@Override
	public void start(Minecraft mc, SelfTest results) {
		this.results = results;
	}

	private static String wrong(Minecraft mc) {
		StringBuilder wrong = new StringBuilder();
		for (String cell : EXPECTED.split(";")) {
			String[] v = cell.split(",");
			int have = mc.world.getBlockId(Integer.parseInt(v[0]), Integer.parseInt(v[1]), Integer.parseInt(v[2]));
			if (have != Integer.parseInt(v[3])) wrong.append(' ').append(cell).append(" is ").append(have);
		}
		return wrong.toString();
	}

	@Override
	public boolean tick(Minecraft mc) {
		if (EXPECTED.isEmpty()) return true;
		String wrong = wrong(mc);
		if (!wrong.isEmpty() && ++ticks < LIMIT) return false;
		results.check("the client has what the server built, " + EXPECTED.split(";").length + " cells" + wrong, wrong.isEmpty());
		return true;
	}
}
