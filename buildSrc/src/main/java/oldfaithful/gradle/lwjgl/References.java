package oldfaithful.gradle.lwjgl;

import java.io.IOException;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

import org.objectweb.asm.AnnotationVisitor;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.FieldVisitor;
import org.objectweb.asm.Handle;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;

/**
 * Every reference a jar's code makes to classes, methods and fields under a package prefix (org/lwjgl/), as the JVM
 * will link them: invocations and field accesses with their owner, name and descriptor; classes named in
 * instructions, descriptors, supertypes and annotations. Classes the jar defines itself under the prefix are left
 * out (a mod shading part of LWJGL links against its own copy).
 */
public final class References {
	public enum Kind { CLASS, STATIC_METHOD, METHOD, STATIC_FIELD, FIELD, ANNOTATION }

	public record Ref(Kind kind, String owner, String name, String desc) implements Comparable<Ref> {
		@Override
		public int compareTo(Ref o) {
			return toString().compareTo(o.toString());
		}

		@Override
		public String toString() {
			return switch (kind) {
				case CLASS -> owner;
				case ANNOTATION -> "@" + owner + (name.isEmpty() ? "" : " " + name);
				case STATIC_METHOD, METHOD -> owner + "." + name + desc + (kind == Kind.STATIC_METHOD ? " (static)" : "");
				case STATIC_FIELD, FIELD -> owner + "." + name + ":" + desc + (kind == Kind.STATIC_FIELD ? " (static)" : "");
			};
		}
	}

	/** Reference to the classes that make it (entry paths). */
	private final Map<Ref, Set<String>> refs = new TreeMap<>();
	private final String prefix;
	private final Set<String> defined = new HashSet<>();

	private References(String prefix) {
		this.prefix = prefix;
	}

	public static References scan(Path jar, String prefix) throws IOException {
		return scan(jar, prefix, false);
	}

	/** With {@code keepDefined}, references to the jar's own classes stay in (for checking a library against itself). */
	public static References scan(Path jar, String prefix, boolean keepDefined) throws IOException {
		References r = new References(prefix);
		ClassPool.forEachClass(jar, (where, bytes) -> r.scanClass(where, bytes));
		if (!keepDefined) r.refs.keySet().removeIf(ref -> r.defined.contains(ref.owner()));
		return r;
	}

	public Map<Ref, Set<String>> all() {
		return refs;
	}

	private void add(Ref ref, String where) {
		refs.computeIfAbsent(ref, k -> new TreeSet<>()).add(where);
	}

	private void type(Type t, String where) {
		if (t.getSort() == Type.ARRAY) t = t.getElementType();
		if (t.getSort() == Type.OBJECT && t.getInternalName().startsWith(prefix)) add(new Ref(Kind.CLASS, t.getInternalName(), "", ""), where);
		if (t.getSort() == Type.METHOD) {
			for (Type a : t.getArgumentTypes()) type(a, where);
			type(t.getReturnType(), where);
		}
	}

	private void className(String internal, String where) {
		if (internal == null) return;
		type(internal.startsWith("[") ? Type.getType(internal) : Type.getObjectType(internal), where);
	}

	private void scanClass(String where, byte[] bytes) {
		String[] self = new String[1];
		new ClassReader(bytes).accept(new ClassVisitor(Opcodes.ASM9) {
			@Override
			public void visit(int version, int access, String name, String signature, String superName, String[] interfaces) {
				self[0] = name;
				if (name.startsWith(prefix)) defined.add(name);
				className(superName, where);
				if (interfaces != null) for (String i : interfaces) className(i, where);
			}

			@Override
			public AnnotationVisitor visitAnnotation(String descriptor, boolean visible) {
				return annotations(where);
			}

			@Override
			public FieldVisitor visitField(int access, String name, String descriptor, String signature, Object value) {
				type(Type.getType(descriptor), where);
				return null;
			}

			@Override
			public MethodVisitor visitMethod(int access, String name, String descriptor, String signature, String[] exceptions) {
				type(Type.getMethodType(descriptor), where);
				return new MethodVisitor(Opcodes.ASM9) {
					@Override
					public AnnotationVisitor visitAnnotation(String desc, boolean visible) {
						return annotations(where);
					}

					@Override
					public void visitMethodInsn(int opcode, String owner, String name, String desc, boolean itf) {
						className(owner, where);
						type(Type.getMethodType(desc), where);
						if (owner.startsWith(prefix)) {
							add(new Ref(opcode == Opcodes.INVOKESTATIC ? Kind.STATIC_METHOD : Kind.METHOD, owner, name, desc), where);
						}
					}

					@Override
					public void visitFieldInsn(int opcode, String owner, String name, String desc) {
						className(owner, where);
						type(Type.getType(desc), where);
						if (owner.startsWith(prefix)) {
							boolean isStatic = opcode == Opcodes.GETSTATIC || opcode == Opcodes.PUTSTATIC;
							add(new Ref(isStatic ? Kind.STATIC_FIELD : Kind.FIELD, owner, name, desc), where);
						}
					}

					@Override
					public void visitTypeInsn(int opcode, String type) {
						className(type, where);
					}

					@Override
					public void visitLdcInsn(Object value) {
						if (value instanceof Type t) type(t, where);
						if (value instanceof Handle h) visitMethodInsn(Opcodes.INVOKESTATIC, h.getOwner(), h.getName(), h.getDesc(), h.isInterface());
					}

					@Override
					public void visitInvokeDynamicInsn(String name, String desc, Handle bsm, Object... args) {
						type(Type.getMethodType(desc), where);
						for (Object a : args) {
							if (a instanceof Handle h && h.getOwner().startsWith(prefix)) {
								boolean isStatic = h.getTag() == Opcodes.H_INVOKESTATIC;
								add(new Ref(isStatic ? Kind.STATIC_METHOD : Kind.METHOD, h.getOwner(), h.getName(), h.getDesc()), where);
							}
							if (a instanceof Type t) type(t, where);
						}
					}

					@Override
					public void visitMultiANewArrayInsn(String descriptor, int dims) {
						type(Type.getType(descriptor), where);
					}

					@Override
					public void visitTryCatchBlock(org.objectweb.asm.Label start, org.objectweb.asm.Label end, org.objectweb.asm.Label handler, String type) {
						className(type, where);
					}
				};
			}
		}, ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
	}

	/**
	 * Class values in annotations (Mixin's {@code @Mixin(Mouse.class)}, a {@code targets = "org.lwjgl..."} string):
	 * recorded so a mixin into a LWJGL class shows up in the report; its injection points aren't checked here.
	 */
	private AnnotationVisitor annotations(String where) {
		return new AnnotationVisitor(Opcodes.ASM9) {
			@Override
			public void visit(String name, Object value) {
				if (value instanceof Type t && t.getSort() == Type.OBJECT && t.getInternalName().startsWith(prefix)) {
					add(new Ref(Kind.ANNOTATION, t.getInternalName(), "", ""), where);
				} else if (value instanceof String s && s.replace('.', '/').startsWith(prefix) && !s.contains(" ")) {
					add(new Ref(Kind.ANNOTATION, s.replace('.', '/'), "", ""), where);
				}
			}

			@Override
			public AnnotationVisitor visitAnnotation(String name, String descriptor) {
				return this;
			}

			@Override
			public AnnotationVisitor visitArray(String name) {
				return this;
			}
		};
	}
}
