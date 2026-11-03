package org.lwjgl.openal;

/** LWJGL 2's ALCcontext: an OpenAL context handle. */
public final class ALCcontext {
	final long context;
	private boolean valid = true;

	ALCcontext(long context) {
		this.context = context;
	}

	public boolean isValid() {
		return valid;
	}

	void setInvalid() {
		valid = false;
	}

	@Override
	public boolean equals(Object o) {
		return o instanceof ALCcontext && ((ALCcontext) o).context == context;
	}

	@Override
	public int hashCode() {
		return Long.hashCode(context);
	}
}
