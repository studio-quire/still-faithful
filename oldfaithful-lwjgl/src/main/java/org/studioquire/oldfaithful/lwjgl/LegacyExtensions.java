package org.studioquire.oldfaithful.lwjgl;

import java.util.HashSet;
import java.util.Set;

import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL30;
import org.lwjgl.opengl.GLCapabilities;

/**
 * The current context's extension strings, read as LWJGL 2's GLContext read them, for the ContextCapabilities flags
 * LWJGL 3's GLCapabilities has no field for. The bake generates calls to {@link #any}.
 */
public final class LegacyExtensions {
	private LegacyExtensions() {
	}

	/** GL 3.0 and later: glGetStringi, one by one; earlier: glGetString's space-separated list. */
	public static Set<String> current(GLCapabilities caps) {
		Set<String> set = new HashSet<>(512);
		if (caps.OpenGL30 && caps.glGetStringi != 0) {
			int n = GL11.glGetInteger(GL30.GL_NUM_EXTENSIONS);
			for (int i = 0; i < n; i++) {
				String e = GL30.glGetStringi(GL11.GL_EXTENSIONS, i);
				if (e != null) set.add(e);
			}
		} else {
			String all = GL11.glGetString(GL11.GL_EXTENSIONS);
			if (all != null) for (String e : all.split(" ")) if (!e.isEmpty()) set.add(e);
		}
		return set;
	}

	/** Whether any of the space-separated names is supported. */
	public static boolean any(Set<String> supported, String names) {
		for (String n : names.split(" ")) if (supported.contains(n)) return true;
		return false;
	}
}
