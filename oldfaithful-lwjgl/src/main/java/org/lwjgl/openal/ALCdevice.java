package org.lwjgl.openal;

/** LWJGL 2's ALCdevice: an OpenAL device handle. */
public final class ALCdevice {
	final long device;
	private boolean valid = true;

	ALCdevice(long device) {
		this.device = device;
	}

	public boolean isValid() {
		return valid;
	}

	void setInvalid() {
		valid = false;
	}

	@Override
	public boolean equals(Object o) {
		return o instanceof ALCdevice && ((ALCdevice) o).device == device;
	}

	@Override
	public int hashCode() {
		return Long.hashCode(device);
	}
}
