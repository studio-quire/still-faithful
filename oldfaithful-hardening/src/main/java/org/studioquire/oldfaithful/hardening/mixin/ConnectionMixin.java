package org.studioquire.oldfaithful.hardening.mixin;

import java.io.InputStream;
import java.net.Socket;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicLong;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.network.Connection;
import net.minecraft.network.NetworkHandler;
import net.minecraft.network.packet.Packet;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import org.studioquire.oldfaithful.hardening.packet.ConnectionLimits;
import org.studioquire.oldfaithful.hardening.packet.CountingInputStream;
import org.studioquire.oldfaithful.hardening.util.Log;

/**
 * Two guards on b1.7.3's connection, on both sides:
 * <ul>
 * <li>The read queue is bounded ({@link ConnectionLimits}), each packet weighed by the bytes it took to read: over
 * the bound, the reader thread pauses (its read reports nothing read, so it sleeps its usual 100 ms and tries again)
 * until the game has handled enough.</li>
 * <li>On the client, a packet whose handling throws ends the connection with the error, instead of crashing the
 * whole game to its crash screen: anything a server can send that the client's code can't take (an unknown entity
 * type, a slot that doesn't exist, ...) used to do that. The server already disconnects a client whose packet
 * throws, and keeps doing so its own way.</li>
 * </ul>
 */
@Mixin(Connection.class)
abstract class ConnectionMixin {
	@Shadow private List readQueue;
	@Shadow private NetworkHandler networkHandler;

	@Unique private final AtomicLong oldfaithful$queuedBytes = new AtomicLong();
	/** Weights of the queued packets, in queue order (the game only ever takes the first). */
	@Unique private final ConcurrentLinkedQueue<Long> oldfaithful$weights = new ConcurrentLinkedQueue<Long>();
	/** The socket's input, counted; null if another mod's stream sits in its place. */
	@Unique private CountingInputStream oldfaithful$counter;
	@Unique private long oldfaithful$counted;
	@Unique private volatile boolean oldfaithful$pauseLogged;

	@Shadow
	public abstract void disconnect(String reason, Object... args);

	@Inject(method = "read", at = @At("HEAD"), cancellable = true)
	private void oldfaithful$pauseWhenFull(CallbackInfoReturnable<Boolean> cir) {
		boolean serverSide = networkHandler.isServerSide();
		int packets = readQueue.size();
		long bytes = oldfaithful$queuedBytes.get();
		if (ConnectionLimits.full(serverSide, packets, bytes)) {
			if (!oldfaithful$pauseLogged) {
				oldfaithful$pauseLogged = true;
				Log.info("Read queue full (" + packets + " packets, " + bytes + " bytes waiting to be handled); reading "
						+ "pauses until the game catches up" + (serverSide ? " [" + oldfaithful$describe() + "]" : ""));
			}
			cir.setReturnValue(false);
		}
	}

	@WrapOperation(method = "<init>", at = @At(value = "INVOKE", target = "Ljava/net/Socket;getInputStream()Ljava/io/InputStream;"))
	private InputStream oldfaithful$countInput(Socket socket, Operation<InputStream> original) {
		oldfaithful$counter = new CountingInputStream(original.call(socket));
		return oldfaithful$counter;
	}

	@WrapOperation(method = "read", at = @At(value = "INVOKE", target = "Ljava/util/List;add(Ljava/lang/Object;)Z"))
	private boolean oldfaithful$countQueued(List queue, Object packet, Operation<Boolean> original) {
		long wire = 1;
		if (oldfaithful$counter != null) {
			long now = oldfaithful$counter.count();
			wire = Math.max(1, now - oldfaithful$counted);
			oldfaithful$counted = now;
		}
		long weight = ConnectionLimits.weight(packet, wire);
		oldfaithful$weights.add(weight);
		oldfaithful$queuedBytes.addAndGet(weight);
		return original.call(queue, packet);
	}

	@WrapOperation(method = "tick", at = @At(value = "INVOKE", target = "Ljava/util/List;remove(I)Ljava/lang/Object;"))
	private Object oldfaithful$countHandled(List queue, int index, Operation<Object> original) {
		Object packet = original.call(queue, index);
		Long weight = oldfaithful$weights.poll();
		if (weight != null) oldfaithful$queuedBytes.addAndGet(-weight);
		return packet;
	}

	@WrapOperation(method = "tick", at = @At(value = "INVOKE",
			target = "Lnet/minecraft/network/packet/Packet;apply(Lnet/minecraft/network/NetworkHandler;)V"))
	private void oldfaithful$applySafely(Packet packet, NetworkHandler handler, Operation<Void> original) {
		if (handler.isServerSide()) {
			original.call(packet, handler);
			return;
		}
		try {
			original.call(packet, handler);
		} catch (RuntimeException | StackOverflowError e) {
			Log.info("The server sent a packet this client can't handle (" + packet.getClass().getName()
					+ "); disconnecting instead of crashing: " + e);
			e.printStackTrace(System.out);
			// Nothing after a broken packet is applied; with the queue empty, the game shows the disconnect at once.
			readQueue.clear();
			oldfaithful$weights.clear();
			oldfaithful$queuedBytes.set(0);
			disconnect("disconnect.genericReason", "Internal exception: " + e);
		}
	}

	@Unique
	private String oldfaithful$describe() {
		try {
			return String.valueOf(((Connection) (Object) this).getAddress());
		} catch (Throwable t) {
			return "?";
		}
	}
}
