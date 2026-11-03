package org.lwjgl.openal;

import java.nio.IntBuffer;

import org.studioquire.oldfaithful.lwjgl.Into;

/** LWJGL 2's ALC10 methods that take device and context objects, added to LWJGL 3's ALC10 at build time. */
@Into("org.lwjgl.openal.ALC10")
final class LegacyALC10 {
	private LegacyALC10() {
	}

	private static long d(ALCdevice device) {
		return device == null ? 0L : device.device;
	}

	public static String alcGetString(ALCdevice device, int pname) {
		return ALC10.alcGetString(d(device), pname);
	}

	public static void alcGetInteger(ALCdevice device, int pname, IntBuffer integerdata) {
		ALC10.alcGetIntegerv(d(device), pname, integerdata);
	}

	public static ALCdevice alcOpenDevice(String devicename) {
		long dev = ALC10.alcOpenDevice(devicename);
		return dev == 0 ? null : new ALCdevice(dev);
	}

	public static boolean alcCloseDevice(ALCdevice device) {
		boolean ok = ALC10.alcCloseDevice(d(device));
		if (device != null) device.setInvalid();
		return ok;
	}

	public static ALCcontext alcCreateContext(ALCdevice device, IntBuffer attrList) {
		long ctx = ALC10.alcCreateContext(d(device), attrList);
		return ctx == 0 ? null : new ALCcontext(ctx);
	}

	public static int alcMakeContextCurrent(ALCcontext context) {
		return ALC10.alcMakeContextCurrent(context == null ? 0L : context.context) ? ALC10.ALC_TRUE : ALC10.ALC_FALSE;
	}

	public static void alcProcessContext(ALCcontext context) {
		ALC10.alcProcessContext(context.context);
	}

	public static ALCcontext alcGetCurrentContext() {
		long ctx = ALC10.alcGetCurrentContext();
		if (ctx == 0) return null;
		ALCcontext ours = LegacyAL.getContext();
		return ours != null && ours.context == ctx ? ours : new ALCcontext(ctx);
	}

	public static ALCdevice alcGetContextsDevice(ALCcontext context) {
		long dev = ALC10.alcGetContextsDevice(context.context);
		if (dev == 0) return null;
		ALCdevice ours = LegacyAL.getDevice();
		return ours != null && ours.device == dev ? ours : new ALCdevice(dev);
	}

	public static void alcSuspendContext(ALCcontext context) {
		ALC10.alcSuspendContext(context.context);
	}

	public static void alcDestroyContext(ALCcontext context) {
		ALC10.alcDestroyContext(context.context);
		context.setInvalid();
	}

	public static int alcGetError(ALCdevice device) {
		return ALC10.alcGetError(d(device));
	}

	public static boolean alcIsExtensionPresent(ALCdevice device, String extName) {
		return ALC10.alcIsExtensionPresent(d(device), extName);
	}

	public static int alcGetEnumValue(ALCdevice device, String enumName) {
		return ALC10.alcGetEnumValue(d(device), enumName);
	}
}
