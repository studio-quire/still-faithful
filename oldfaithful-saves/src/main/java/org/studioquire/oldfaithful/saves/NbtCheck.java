package org.studioquire.oldfaithful.saves;

/**
 * Walks serialized NBT (b1.7.3's tag types, 0 to 10) and checks every length against the bytes actually there, before
 * the game's parser sees it. Damaged data can claim a byte array of two billion bytes or a list of two billion
 * elements, which makes the game's parser run out of memory and crash the game every time the chunk loads; an unknown
 * tag type makes it throw a NullPointerException. Returns null if the data is sound, otherwise what's wrong.
 */
final class NbtCheck {
	private static final int MAX_DEPTH = 512;

	private final byte[] b;
	private int p;

	private NbtCheck(byte[] b) {
		this.b = b;
	}

	static String check(byte[] data) {
		NbtCheck c = new NbtCheck(data);
		try {
			int type = c.u8();
			if (type != 10) return "root tag is type " + type + ", not a compound";
			c.string();
			c.payload(10, 0);
			return null;
		} catch (Bad e) {
			return e.getMessage() + " at byte " + c.p + " of " + data.length;
		}
	}

	private static final class Bad extends Exception {
		Bad(String message) {
			super(message, null, false, false);
		}
	}

	private void need(long n) throws Bad {
		if (n < 0 || p + n > b.length) throw new Bad("data ends early (needs " + n + " more bytes)");
	}

	private int u8() throws Bad {
		need(1);
		return b[p++] & 0xFF;
	}

	private int i32() throws Bad {
		need(4);
		int v = (b[p] & 0xFF) << 24 | (b[p + 1] & 0xFF) << 16 | (b[p + 2] & 0xFF) << 8 | (b[p + 3] & 0xFF);
		p += 4;
		return v;
	}

	private void string() throws Bad {
		need(2);
		int len = (b[p] & 0xFF) << 8 | (b[p + 1] & 0xFF);
		p += 2;
		need(len);
		p += len;
	}

	private void payload(int type, int depth) throws Bad {
		if (depth > MAX_DEPTH) throw new Bad("nested deeper than " + MAX_DEPTH);
		switch (type) {
			case 1: need(1); p += 1; return;
			case 2: need(2); p += 2; return;
			case 3: case 5: need(4); p += 4; return;
			case 4: case 6: need(8); p += 8; return;
			case 7: {
				int len = i32();
				if (len < 0) throw new Bad("byte array of negative length " + len);
				need(len);
				p += len;
				return;
			}
			case 8: string(); return;
			case 9: {
				int element = u8();
				int count = i32();
				if (count < 0) throw new Bad("list of negative length " + count);
				if (count == 0) return;
				if (element == 0) throw new Bad("list of " + count + " end tags");
				if (element > 10) throw new Bad("list of unknown tag type " + element);
				// Every element takes at least one byte, which bounds the count by what's left.
				need(count);
				for (int i = 0; i < count; i++) payload(element, depth + 1);
				return;
			}
			case 10: {
				while (true) {
					int t = u8();
					if (t == 0) return;
					if (t > 10) throw new Bad("unknown tag type " + t);
					string();
					payload(t, depth + 1);
				}
			}
			default:
				throw new Bad("unknown tag type " + type);
		}
	}
}
