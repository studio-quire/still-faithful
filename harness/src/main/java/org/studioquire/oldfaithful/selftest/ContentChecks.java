package org.studioquire.oldfaithful.selftest;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import net.minecraft.entity.data.DataTracker;
import net.minecraft.entity.data.DataTrackerEntry;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;

import org.studioquire.oldfaithful.hardening.packet.ServerContent;

/** Client only: blocks, items and tracked entity data a server may name (ServerContent, DataTrackerMixin). */
final class ContentChecks {
	private final SelfTest t;

	ContentChecks(SelfTest t) {
		this.t = t;
	}

	void run() {
		t.check("server content: known blocks and items pass (stone, sand, iron shovel, music disc 13, none)",
				!throwsOn(() -> ServerContent.checkBlock(1)) && !throwsOn(() -> ServerContent.checkBlock(12))
						&& !throwsOn(() -> ServerContent.checkItem(256)) && !throwsOn(() -> ServerContent.checkItem(2256))
						&& !throwsOn(() -> ServerContent.checkItem(-1)) && !throwsOn(() -> ServerContent.checkItem(1)));
		t.check("server content: unknown ids are refused (block 200, items 0, 1000, 31999, 40000)",
				throwsOn(() -> ServerContent.checkBlock(200)) && throwsOn(() -> ServerContent.checkItem(0))
						&& throwsOn(() -> ServerContent.checkItem(1000)) && throwsOn(() -> ServerContent.checkItem(31999))
						&& throwsOn(() -> ServerContent.checkItem(40000)));

		byte[] chunk = new byte[32768];
		chunk[5 << 11 | 7 << 7 | 64] = 1;
		chunk[5 << 11 | 7 << 7 | 65] = (byte) 200;
		t.check("server content: a chunk is checked only where the packet filled it, and an unknown block there is refused",
				!throwsOn(() -> ServerContent.checkBlocks(chunk, 0, 0, 0, 16, 64, 16))
						&& throwsOn(() -> ServerContent.checkBlocks(chunk, 0, 0, 0, 16, 128, 16)));

		trackedData();
		riding();
		damageValues();
		positions();
	}

	/**
	 * Damage values items can't show: dyes past 15 and slabs past 3 (or below 0) index their name arrays out of range
	 * when hovered. A sweep over every registered item checks the probe also accepts what vanilla itself makes.
	 */
	private void damageValues() {
		int dye = 351, slab = 44;
		boolean known = !throwsOn(() -> ServerContent.checkItem(dye, 15)) && !throwsOn(() -> ServerContent.checkItem(slab, 3))
				&& !throwsOn(() -> ServerContent.checkStack(new ItemStack(35, 1, 14)))
				&& !throwsOn(() -> ServerContent.checkItem(358, 12345)); // a map: any number
		boolean refused = throwsOn(() -> ServerContent.checkItem(dye, 16)) && throwsOn(() -> ServerContent.checkItem(dye, -1))
				&& throwsOn(() -> ServerContent.checkStack(new ItemStack(slab, 1, 4)))
				&& throwsOn(() -> ServerContent.checkStack(new ItemStack(slab, 1, -3)));
		// With Old Faithful's fixes (#33), those items name themselves instead of crashing, so there's nothing to refuse.
		if (net.fabricmc.loader.api.FabricLoader.getInstance().isModLoaded("oldfaithful-fixes")) {
			refused = !throwsOn(() -> ServerContent.checkItem(dye, 16)) && !throwsOn(() -> ServerContent.checkItem(dye, -1))
					&& !throwsOn(() -> ServerContent.checkStack(new ItemStack(slab, 1, 4)))
					&& !throwsOn(() -> ServerContent.checkStack(new ItemStack(slab, 1, -3)));
		}
		// Every item at the damage values vanilla gives it: 0, and through its durability or subtypes.
		int checked = 0;
		StringBuilder wrong = new StringBuilder();
		for (int id = 1; id < Item.ITEMS.length; id++) {
			Item item = Item.ITEMS[id];
			if (item == null) continue;
			int top = item.getMaxDamage() > 0 ? item.getMaxDamage() : (id == dye ? 15 : id == slab ? 3 : 0);
			for (int d = 0; d <= top; d += Math.max(1, top / 8)) {
				final int fid = id, fd = d;
				checked++;
				if (throwsOn(() -> ServerContent.checkItem(fid, fd))) wrong.append(' ').append(id).append(':').append(d);
			}
		}
		t.check("damage values: dyes 16 and -1 and slabs 4 and -3 refused; known values pass; " + checked
				+ " vanilla item/damage pairs all accepted" + (wrong.length() > 0 ? " except" + wrong : ""),
				known && refused && wrong.length() == 0);
	}

	/** Positions the client takes as sent: beyond the world, or not numbers, they're refused. */
	private void positions() {
		boolean fine = !throwsOn(() -> ServerContent.checkPosition("position", 1e6, 64, -2.9e7))
				&& !throwsOn(() -> ServerContent.checkAngles("position", 359, -90));
		boolean refused = throwsOn(() -> ServerContent.checkPosition("position", 3.5e10, 64, 0))
				&& throwsOn(() -> ServerContent.checkPosition("position", 0, 64, -3.5e10))
				&& throwsOn(() -> ServerContent.checkPosition("position", Double.NaN, 64, 0))
				&& throwsOn(() -> ServerContent.checkPosition("position", 0, Double.POSITIVE_INFINITY, 0))
				&& throwsOn(() -> ServerContent.checkAngles("position", Float.NaN, 0));
		// What the refusal protects: past ~3.4e10 the entity search's chunk loop bound saturates at Integer.MAX_VALUE,
		// and "i <= MAX_VALUE" never ends.
		boolean saturates = net.minecraft.util.math.MathHelper.floor((3.5e10 + 2.0) / 16.0) == Integer.MAX_VALUE;
		t.check("positions: inside the world pass; 3.5e10, NaN and infinity are refused (3.5e10 does saturate the "
				+ "entity search's loop bound)", fine && refused && saturates);
	}

	/** An entity with no world, enough to link riders and vehicles. */
	private static final class Bare extends net.minecraft.entity.Entity {
		Bare() {
			super(null);
		}

		@Override
		protected void initDataTracker() {
		}

		@Override
		protected void readNbt(net.minecraft.nbt.NbtCompound nbt) {
		}

		@Override
		protected void writeNbt(net.minecraft.nbt.NbtCompound nbt) {
		}
	}

	private void riding() {
		try {
			Bare a = new Bare(), b = new Bare(), c = new Bare();
			boolean plain = !throwsOn(() -> ServerContent.checkRiding(a, b)) && !throwsOn(() -> ServerContent.checkRiding(a, null));
			b.vehicle = c; // b rides c
			c.vehicle = a; // c rides a
			boolean self = throwsOn(() -> ServerContent.checkRiding(a, a));
			boolean cycle = throwsOn(() -> ServerContent.checkRiding(a, b)); // a riding b would close a -> b -> c -> a
			t.check("riding: an ordinary mount passes; riding itself or closing a loop is refused", plain && self && cycle);
		} catch (Throwable e) {
			t.fail("riding loops", e);
		}
	}

	private void trackedData() {
		try {
			DataTracker tracker = new DataTracker();
			tracker.startTracking(0, (byte) 0);
			tracker.startTracking(16, "name");
			List<DataTrackerEntry> same = new ArrayList<>();
			same.add(new DataTrackerEntry(0, 0, (byte) 3));
			same.add(new DataTrackerEntry(4, 16, "other"));
			tracker.writeUpdatedEntries(same);
			boolean sameOk = tracker.getByte(0) == 3;

			List<DataTrackerEntry> changed = new ArrayList<>();
			changed.add(new DataTrackerEntry(2, 0, 7));
			boolean refused = throwsOn(() -> tracker.writeUpdatedEntries(changed));
			t.check("tracked data: values of the entity's own types are taken, a value changing type is refused",
					sameOk && refused && tracker.getByte(0) == 3);
		} catch (Throwable e) {
			t.fail("tracked data: types", e);
		}

		try {
			List<?> two = DataTracker.readEntries(trackedStream(2, true));
			boolean genuine = two != null && two.size() == 2;
			boolean bounded;
			try {
				DataTracker.readEntries(trackedStream(100_000, false));
				bounded = false;
			} catch (IOException e) {
				bounded = String.valueOf(e.getMessage()).contains("more than");
			}
			t.check("tracked data: a genuine list reads; one without an end stops at " + ServerContent.MAX_TRACKED_ENTRIES
					+ " entries", genuine && bounded);
		} catch (Throwable e) {
			t.fail("tracked data: list bound", e);
		}
	}

	private static DataInputStream trackedStream(int entries, boolean terminated) throws IOException {
		ByteArrayOutputStream bytes = new ByteArrayOutputStream();
		DataOutputStream out = new DataOutputStream(bytes);
		for (int i = 0; i < entries; i++) {
			out.writeByte(0 << 5 | (i & 31)); // a byte value at index i
			out.writeByte(1);
		}
		if (terminated) out.writeByte(127);
		return new DataInputStream(new ByteArrayInputStream(bytes.toByteArray()));
	}

	private interface Action {
		void run() throws Exception;
	}

	private static boolean throwsOn(Action action) {
		try {
			action.run();
			return false;
		} catch (Exception e) {
			return true;
		}
	}
}
