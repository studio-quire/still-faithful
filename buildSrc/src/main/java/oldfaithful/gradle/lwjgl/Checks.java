package oldfaithful.gradle.lwjgl;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.IntInsnNode;
import org.objectweb.asm.tree.LdcInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.VarInsnNode;
import org.objectweb.asm.tree.analysis.Analyzer;
import org.objectweb.asm.tree.analysis.AnalyzerException;
import org.objectweb.asm.tree.analysis.Frame;
import org.objectweb.asm.tree.analysis.SourceInterpreter;
import org.objectweb.asm.tree.analysis.SourceValue;

/**
 * LWJGL 2's argument checks for one binding method, read from its bytecode and rewritten as calls to our
 * {@code LegacyChecks}, to run at the top of whichever LWJGL 3 method or forwarder stands in for it. LWJGL 2 checked
 * every buffer it was given (size where it knew one, directness, null termination); LWJGL 3 mostly doesn't, so a
 * buffer too small for glReadPixels or glTexImage2D would be overrun in native memory where LWJGL 2 threw.
 * <p>
 * A check is copied when every argument is an expression over the method's parameters and constants: arithmetic,
 * {@code remaining()}, array length, and GLChecks' image-size functions. Others (NV_path_rendering's metrics, stride
 * conditionals, PointerBuffers) are left out and named in the bake report. GLChecks' {@code ensure*} checks, which
 * need LWJGL 2's state tracker (whether a pixel or vertex buffer object is bound), aren't attempted.
 */
final class Checks {
	static final String RUNTIME = "org/studioquire/oldfaithful/lwjgl/LegacyChecks";
	private static final String BUFFER_CHECKS = "org/lwjgl/BufferChecks";
	private static final String GL_CHECKS = "org/lwjgl/opengl/GLChecks";
	private static final Set<String> CHECKS = Set.of("checkBuffer", "checkDirect", "checkNullTerminated", "checkArray", "checkNotNull");
	private static final Set<String> SIZES = Set.of("calculateImageStorage", "calculateTexImage1DStorage", "calculateTexImage2DStorage",
			"calculateTexImage3DStorage");

	/** The checks found in one method: the code to insert, how many were copied, and descriptions of those that weren't. */
	record Found(InsnList code, int copied, List<String> skipped) {
		boolean isEmpty() {
			return copied == 0;
		}
	}

	private Checks() {
	}

	static Found extract(String owner, MethodNode impl) {
		InsnList code = new InsnList();
		List<String> skipped = new ArrayList<>();
		if (impl.instructions.size() == 0) return new Found(code, 0, skipped);
		Frame<SourceValue>[] frames;
		try {
			frames = new Analyzer<>(new SourceInterpreter()).analyze(owner, impl);
		} catch (AnalyzerException e) {
			skipped.add("unanalysable: " + e.getMessage());
			return new Found(code, 0, skipped);
		}
		int params = (Type.getArgumentsAndReturnSizes(impl.desc) >> 2) - 1;
		int copied = 0;
		for (int i = 0; i < impl.instructions.size(); i++) {
			if (!(impl.instructions.get(i) instanceof MethodInsnNode call) || call.getOpcode() != Opcodes.INVOKESTATIC
					|| !call.owner.equals(BUFFER_CHECKS) || !CHECKS.contains(call.name)) {
				continue;
			}
			Frame<SourceValue> frame = frames[i];
			if (frame == null) continue; // unreachable
			Type[] args = Type.getArgumentTypes(call.desc);
			InsnList one = new InsnList();
			boolean ok = runtimeDesc(call) != null;
			for (int a = 0; ok && a < args.length; a++) {
				ok = emit(impl, frames, frame.getStack(frame.getStackSize() - args.length + a), params, one, 0);
			}
			if (!ok) {
				skipped.add(call.name + call.desc);
				continue;
			}
			one.add(new MethodInsnNode(Opcodes.INVOKESTATIC, RUNTIME, call.name, runtimeDesc(call), false));
			code.add(one);
			copied++;
		}
		return new Found(code, copied, skipped);
	}

	/** Our LegacyChecks method for a BufferChecks call: the java.nio buffer types taken as Buffer; null if none fits. */
	private static String runtimeDesc(MethodInsnNode call) {
		Type[] args = Type.getArgumentTypes(call.desc);
		StringBuilder d = new StringBuilder("(");
		for (Type t : args) {
			String s = t.getDescriptor();
			if (s.startsWith("Ljava/nio/") && s.endsWith("Buffer;")) {
				boolean bytes = call.name.equals("checkNullTerminated") && args.length == 2;
				d.append(bytes ? s : "Ljava/nio/Buffer;");
			} else if (s.equals("I") || s.equals("[Ljava/lang/Object;") || s.equals("Ljava/lang/Object;")) {
				d.append(s);
			} else if (t.getSort() == Type.ARRAY && t.getElementType().getSort() == Type.OBJECT && call.name.equals("checkArray")) {
				d.append("[Ljava/lang/Object;");
			} else {
				return null;
			}
		}
		return d.append(")V").toString();
	}

	/**
	 * Appends code that recomputes one value of LWJGL 2's method from its parameters and constants, or returns false
	 * if the value comes from anything else.
	 */
	private static boolean emit(MethodNode impl, Frame<SourceValue>[] frames, SourceValue v, int params, InsnList out, int depth) {
		if (v.insns.size() != 1 || depth > 8) return false;
		AbstractInsnNode insn = v.insns.iterator().next();
		int index = impl.instructions.indexOf(insn);
		Frame<SourceValue> at = frames[index];
		switch (insn.getOpcode()) {
			case Opcodes.ILOAD, Opcodes.ALOAD -> {
				int slot = ((VarInsnNode) insn).var;
				if (slot >= params || stored(impl, slot)) return false;
				out.add(new VarInsnNode(insn.getOpcode(), slot));
				return true;
			}
			case Opcodes.ICONST_M1, Opcodes.ICONST_0, Opcodes.ICONST_1, Opcodes.ICONST_2, Opcodes.ICONST_3, Opcodes.ICONST_4, Opcodes.ICONST_5 -> {
				out.add(new InsnNode(insn.getOpcode()));
				return true;
			}
			case Opcodes.BIPUSH, Opcodes.SIPUSH -> {
				out.add(new IntInsnNode(insn.getOpcode(), ((IntInsnNode) insn).operand));
				return true;
			}
			case Opcodes.LDC -> {
				if (!(((LdcInsnNode) insn).cst instanceof Integer c)) return false;
				out.add(new LdcInsnNode(c));
				return true;
			}
			case Opcodes.IADD, Opcodes.ISUB, Opcodes.IMUL, Opcodes.IDIV, Opcodes.ISHL, Opcodes.ISHR -> {
				if (!emit(impl, frames, at.getStack(at.getStackSize() - 2), params, out, depth + 1)) return false;
				if (!emit(impl, frames, at.getStack(at.getStackSize() - 1), params, out, depth + 1)) return false;
				out.add(new InsnNode(insn.getOpcode()));
				return true;
			}
			case Opcodes.ARRAYLENGTH -> {
				if (!emit(impl, frames, at.getStack(at.getStackSize() - 1), params, out, depth + 1)) return false;
				out.add(new InsnNode(Opcodes.ARRAYLENGTH));
				return true;
			}
			case Opcodes.INVOKEVIRTUAL -> {
				MethodInsnNode m = (MethodInsnNode) insn;
				if (!m.owner.startsWith("java/nio/") || !m.name.equals("remaining") || !m.desc.equals("()I")) return false;
				if (!emit(impl, frames, at.getStack(at.getStackSize() - 1), params, out, depth + 1)) return false;
				out.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, m.owner, m.name, m.desc, false));
				return true;
			}
			case Opcodes.INVOKESTATIC -> {
				MethodInsnNode m = (MethodInsnNode) insn;
				if (!m.owner.equals(GL_CHECKS) || !SIZES.contains(m.name) || !m.desc.startsWith("(Ljava/nio/Buffer;")) return false;
				int n = Type.getArgumentTypes(m.desc).length;
				for (int a = 0; a < n; a++) {
					if (!emit(impl, frames, at.getStack(at.getStackSize() - n + a), params, out, depth + 1)) return false;
				}
				out.add(new MethodInsnNode(Opcodes.INVOKESTATIC, RUNTIME, m.name, m.desc, false));
				return true;
			}
			default -> {
				return false;
			}
		}
	}

	/** Whether the method ever assigns the slot (a parameter reused as a local wouldn't hold the argument). */
	private static boolean stored(MethodNode impl, int slot) {
		for (AbstractInsnNode insn : impl.instructions) {
			if (insn instanceof VarInsnNode v && v.var == slot && v.getOpcode() >= Opcodes.ISTORE && v.getOpcode() <= Opcodes.ASTORE) return true;
		}
		return false;
	}
}
