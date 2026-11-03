package oldfaithful.gradle.lwjgl;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
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
 * Maps an LWJGL 2 binding method to the LWJGL 3 method that does the same thing, by the naming conventions the two
 * generators follow, and writes the forwarding method. LWJGL 2 drops a function's type suffix and overloads on the
 * buffer type ({@code glGetFloat(int, FloatBuffer)} is {@code glGetFloatv}; {@code glGetQueryObjectuARB} is
 * {@code glGetQueryObjectuivARB}), and for some pointer functions infers the GL type from the buffer
 * ({@code glVertexPointer(int size, int stride, FloatBuffer)}) or from an {@code unsigned} flag
 * ({@code glColorPointer(int size, boolean unsigned, int stride, ByteBuffer)}). LWJGL 3 keeps the C names and the
 * type argument. Nothing here is copied from either library: it's their naming, applied.
 */
final class Forwarders {
	/** Type suffixes in the order tried; signed before unsigned, so an LWJGL 2 name without 'u' maps to the signed one. */
	private static final String[] TYPE_SUFFIXES = {
			"v", "fv", "dv", "iv", "sv", "bv", "i64v", "uiv", "usv", "ubv", "ui64v", "f", "d", "i", "s", "b", "i64",
			"Iiv", "Ifv", "Iuiv", "Pointerv", "xv", "x", "i_v"};
	/** Extension suffixes LWJGL keeps after the type suffix. */
	private static final Pattern EXTENSION = Pattern.compile("^(.*?)(ARB|EXT|NV|NVX|ATI|AMD|APPLE|KHR|INTEL|SGIS|IBM|SUN|OES|GREMEDY|OVR|MESA)$");

	private static final int GL_BYTE = 0x1400, GL_SHORT = 0x1402, GL_INT = 0x1404, GL_FLOAT = 0x1406, GL_DOUBLE = 0x140A;

	/** How an LWJGL 2 method reaches its LWJGL 3 counterpart. */
	record Plan(String owner, MethodNode legacy, String targetName, String targetDesc, int insertAt, int typeConstant,
			boolean fromUnsignedFlag) {
		/** The forwarder: legacy's name and descriptor, calling the target in {@code owner}. */
		MethodNode build() {
			MethodNode m = new MethodNode(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, legacy.name, legacy.desc, null, null);
			Type[] args = Type.getArgumentTypes(legacy.desc);
			int local = 0;
			for (int i = 0; i < args.length; i++) {
				if (i == insertAt && !fromUnsignedFlag) push(m, typeConstant);
				if (i == insertAt && fromUnsignedFlag) {
					// signed type + flag: GL_UNSIGNED_BYTE/SHORT/INT are each their signed type + 1.
					push(m, typeConstant);
					m.instructions.add(new VarInsnNode(Opcodes.ILOAD, local));
					m.instructions.add(new InsnNode(Opcodes.IADD));
				} else {
					m.instructions.add(new VarInsnNode(args[i].getOpcode(Opcodes.ILOAD), local));
				}
				local += args[i].getSize();
			}
			if (insertAt == args.length) push(m, typeConstant);
			m.instructions.add(new MethodInsnNode(Opcodes.INVOKESTATIC, owner, targetName, targetDesc, false));
			m.instructions.add(new InsnNode(Type.getReturnType(legacy.desc).getOpcode(Opcodes.IRETURN)));
			return m;
		}

		private static void push(MethodNode m, int value) {
			m.instructions.add(value <= Short.MAX_VALUE ? new IntInsnNode(Opcodes.SIPUSH, value) : new LdcInsnNode(value));
		}

		boolean insertsType() {
			return insertAt >= 0;
		}
	}

	/** Candidate LWJGL 3 names for an LWJGL 2 name, most likely first. */
	static List<String> candidateNames(String name) {
		List<String> names = new ArrayList<>();
		names.add(name);
		Matcher ext = EXTENSION.matcher(name);
		String stem = ext.matches() ? ext.group(1) : name;
		String suffix = ext.matches() ? ext.group(2) : "";
		for (String t : TYPE_SUFFIXES) names.add(stem + t + suffix);
		return names;
	}

	/**
	 * The plan for one LWJGL 2 method, given its implementation in LWJGL 2 and the public static methods of the
	 * LWJGL 3 class (name+desc, including inherited), or null if no convention applies.
	 */
	static Plan plan(String owner, MethodNode legacy, Map<String, ?> lwjgl3Methods) {
		List<String> names = candidateNames(legacy.name);
		// Same arguments, another name; or Strings taken as CharSequence.
		String charSequences = legacy.desc.replace("Ljava/lang/String;", "Ljava/lang/CharSequence;");
		String ret0 = Type.getReturnType(legacy.desc).getDescriptor();
		if (ret0.equals("Ljava/lang/String;")) charSequences = charSequences.substring(0, charSequences.lastIndexOf(')') + 1) + ret0;
		for (String desc : new String[] {legacy.desc, charSequences}) {
			for (String n : names) {
				if (lwjgl3Methods.containsKey(n + desc)) return new Plan(owner, legacy, n, desc, -1, 0, false);
			}
		}
		// The GL type inferred from the buffer, or from an "unsigned" flag: LWJGL 2's implementation passes it to its
		// native call at the position LWJGL 3 takes it as an argument (both follow the C signature).
		Type[] args = Type.getArgumentTypes(legacy.desc);
		if (args.length == 0 || glTypeOf(args[args.length - 1]) == 0) return null;
		TypeArgument found = typeArgument(owner, legacy);
		if (found == null) return null;
		Type ret = Type.getReturnType(legacy.desc);
		List<Type> target = new ArrayList<>(List.of(args));
		if (found.fromFlag()) {
			if (found.position() >= args.length || args[found.position()] != Type.BOOLEAN_TYPE) return null;
			target.set(found.position(), Type.INT_TYPE);
		} else {
			if (found.position() > args.length) return null;
			target.add(found.position(), Type.INT_TYPE);
		}
		String desc = Type.getMethodDescriptor(ret, target.toArray(new Type[0]));
		for (String n : names) {
			if (lwjgl3Methods.containsKey(n + desc)) {
				return new Plan(owner, legacy, n, desc, found.position(), found.signedConstant(), found.fromFlag());
			}
		}
		return null;
	}

	/** Where LWJGL 2's implementation puts a constant GL type among its native call's arguments. */
	record TypeArgument(int position, int signedConstant, boolean fromFlag) {
	}

	/**
	 * Finds the native call (the one taking the most arguments) in LWJGL 2's implementation and the argument that
	 * comes from a GL type constant: one constant (inferred from the buffer), or two adjacent ones chosen by a
	 * flag (signed and unsigned).
	 */
	static TypeArgument typeArgument(String owner, MethodNode impl) {
		if (impl.instructions.size() == 0) return null;
		Frame<SourceValue>[] frames;
		try {
			frames = new Analyzer<>(new SourceInterpreter()).analyze(owner, impl);
		} catch (AnalyzerException e) {
			return null;
		}
		MethodInsnNode nativeCall = null;
		int callIndex = -1;
		for (int i = 0; i < impl.instructions.size(); i++) {
			if (impl.instructions.get(i) instanceof MethodInsnNode m && m.getOpcode() == Opcodes.INVOKESTATIC
					&& (nativeCall == null || Type.getArgumentTypes(m.desc).length > Type.getArgumentTypes(nativeCall.desc).length)) {
				nativeCall = m;
				callIndex = i;
			}
		}
		if (nativeCall == null || frames[callIndex] == null) return null;
		Frame<SourceValue> frame = frames[callIndex];
		int argc = Type.getArgumentTypes(nativeCall.desc).length;
		for (int a = 0; a < argc; a++) {
			SourceValue v = frame.getStack(frame.getStackSize() - argc + a);
			List<Integer> constants = new ArrayList<>();
			for (var insn : v.insns) {
				if (insn instanceof IntInsnNode i && insn.getOpcode() == Opcodes.SIPUSH) constants.add(i.operand);
				else if (insn instanceof LdcInsnNode l && l.cst instanceof Integer c) constants.add(c);
				else constants.add(null);
			}
			if (constants.contains(null) || constants.isEmpty()) continue;
			if (constants.size() == 1 && isGlType(constants.get(0))) return new TypeArgument(a, constants.get(0), false);
			if (constants.size() == 2) {
				int lo = Math.min(constants.get(0), constants.get(1)), hi = Math.max(constants.get(0), constants.get(1));
				if (hi == lo + 1 && isGlType(lo)) return new TypeArgument(a, lo, true);
			}
		}
		return null;
	}

	private static boolean isGlType(int v) {
		return v >= GL_BYTE && v <= GL_DOUBLE;
	}

	private static int glTypeOf(Type t) {
		return switch (t.getDescriptor()) {
			case "Ljava/nio/ByteBuffer;" -> GL_BYTE;
			case "Ljava/nio/ShortBuffer;" -> GL_SHORT;
			case "Ljava/nio/IntBuffer;" -> GL_INT;
			case "Ljava/nio/FloatBuffer;" -> GL_FLOAT;
			case "Ljava/nio/DoubleBuffer;" -> GL_DOUBLE;
			default -> 0;
		};
	}
}
