package org.lwjgl.openal;

import java.util.concurrent.Semaphore;

import org.lwjgl.LWJGLException;
import org.studioquire.oldfaithful.lwjgl.Into;

/**
 * LWJGL 2's AL.create/destroy and friends, added to LWJGL 3's AL at build time: one device and context, opened and
 * made current as LWJGL 2 did. Added: when the system's default output changes (headphones plugged or unplugged),
 * the device moves to the new default (OpenAL Soft's system events and reopen extensions), so sound doesn't stop.
 */
@Into("org.lwjgl.openal.AL")
final class LegacyAL {
	private static ALCdevice device;
	private static ALCcontext context;
	private static boolean created;
	private static int[] attributes;
	private static SOFTSystemEventProc events;
	private static Thread follower;
	private static final Semaphore defaultChanged = new Semaphore(0);

	private LegacyAL() {
	}

	public static void create() throws LWJGLException {
		create(null, 44100, 60, false);
	}

	public static void create(String deviceArguments, int contextFrequency, int contextRefresh, boolean contextSynchronized) throws LWJGLException {
		create(deviceArguments, contextFrequency, contextRefresh, contextSynchronized, true);
	}

	public static void create(String deviceArguments, int contextFrequency, int contextRefresh, boolean contextSynchronized,
			boolean openDevice) throws LWJGLException {
		if (created) throw new IllegalStateException("Only one OpenAL context may be instantiated at any one time.");
		try {
			ALC.getFunctionProvider();
		} catch (RuntimeException | LinkageError e) {
			throw new LWJGLException("Could not load OpenAL: " + e, e);
		}
		created = true;
		if (!openDevice) return;
		try {
			long dev = ALC10.alcOpenDevice(deviceArguments);
			if (dev == 0) throw new LWJGLException("Could not open ALC device");
			device = new ALCdevice(dev);
			ALCCapabilities alc = ALC.createCapabilities(dev);
			attributes = contextFrequency == -1 ? new int[] {0} : new int[] {ALC10.ALC_FREQUENCY, contextFrequency,
					ALC10.ALC_REFRESH, contextRefresh, ALC10.ALC_SYNC, contextSynchronized ? ALC10.ALC_TRUE : ALC10.ALC_FALSE, 0};
			long ctx = ALC10.alcCreateContext(dev, attributes);
			if (ctx == 0) throw new LWJGLException("Could not create ALC context");
			context = new ALCcontext(ctx);
			ALC10.alcMakeContextCurrent(ctx);
			AL.createCapabilities(alc);
			if (deviceArguments == null) followDefaultDevice(alc);
		} catch (LWJGLException | RuntimeException e) {
			destroy();
			throw e;
		}
	}

	private static void followDefaultDevice(ALCCapabilities alc) {
		if (!alc.ALC_SOFT_system_events || !alc.ALC_SOFT_reopen_device) return;
		if (SOFTSystemEvents.alcEventIsSupportedSOFT(SOFTSystemEvents.ALC_EVENT_TYPE_DEFAULT_DEVICE_CHANGED_SOFT,
				SOFTSystemEvents.ALC_PLAYBACK_DEVICE_SOFT) != SOFTSystemEvents.ALC_EVENT_SUPPORTED_SOFT) return;
		// The callback runs on the audio system's thread and mustn't call ALC; it only wakes ours.
		events = SOFTSystemEventProc.create((eventType, deviceType, dev, length, message, userParam) -> {
			if (eventType == SOFTSystemEvents.ALC_EVENT_TYPE_DEFAULT_DEVICE_CHANGED_SOFT
					&& deviceType == SOFTSystemEvents.ALC_PLAYBACK_DEVICE_SOFT) defaultChanged.release();
		});
		SOFTSystemEvents.alcEventCallbackSOFT(events, 0L);
		SOFTSystemEvents.alcEventControlSOFT(new int[] {SOFTSystemEvents.ALC_EVENT_TYPE_DEFAULT_DEVICE_CHANGED_SOFT}, true);
		defaultChanged.drainPermits();
		follower = new Thread(LegacyAL::follow, "OpenAL default device follower");
		follower.setDaemon(true);
		follower.start();
	}

	private static void follow() {
		while (true) {
			try {
				defaultChanged.acquire();
				// Changes come in bursts (several endpoints at once); settle, then reopen once.
				Thread.sleep(200);
				defaultChanged.drainPermits();
			} catch (InterruptedException e) {
				return;
			}
			reopen();
		}
	}

	/** Moves the open device to the system's current default output. */
	static synchronized boolean reopen() {
		ALCdevice d = device;
		if (d == null) return false;
		boolean ok = SOFTReopenDevice.alcReopenDeviceSOFT(d.device, (CharSequence) null, attributes);
		System.out.println("[purefaithful-lwjgl] Default sound output changed; " + (ok ? "moved to it: "
				+ ALC10.alcGetString(d.device, ALC10.ALC_DEVICE_SPECIFIER) : "couldn't move (error " + ALC10.alcGetError(d.device) + ")"));
		return ok;
	}

	public static synchronized void destroy() {
		if (follower != null) {
			follower.interrupt();
			follower = null;
		}
		if (events != null) {
			SOFTSystemEvents.alcEventControlSOFT(new int[] {SOFTSystemEvents.ALC_EVENT_TYPE_DEFAULT_DEVICE_CHANGED_SOFT}, false);
			SOFTSystemEvents.alcEventCallbackSOFT(null, 0L);
			events.free();
			events = null;
		}
		if (context != null) {
			ALC10.alcMakeContextCurrent(0);
			ALC10.alcDestroyContext(context.context);
			context.setInvalid();
			context = null;
		}
		if (device != null) {
			ALC10.alcCloseDevice(device.device);
			device.setInvalid();
			device = null;
		}
		created = false;
	}

	public static boolean isCreated() {
		return created;
	}

	public static ALCcontext getContext() {
		return context;
	}

	public static ALCdevice getDevice() {
		return device;
	}
}
