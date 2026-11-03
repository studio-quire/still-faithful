package oldfaithful.gradle.lwjgl;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.FieldVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

/**
 * The classes in a set of jars, with just enough to resolve a member reference the way the JVM does: superclass,
 * interfaces, and each declared method and field with its access flags. Nested jars (Fabric's META-INF/jars) are
 * read too; multi-release variants (META-INF/versions) aren't, since the base classes are what Java 8+ all share.
 */
public final class ClassPool {
	public record Member(String name, String desc, int access) {
		public boolean isStatic() {
			return (access & Opcodes.ACC_STATIC) != 0;
		}
	}

	public record ClassInfo(String name, String superName, List<String> interfaces, int access,
			Map<String, Member> methods, Map<String, Member> fields) {
	}

	private final Map<String, ClassInfo> classes = new HashMap<>();

	public static ClassPool of(List<Path> jars) throws IOException {
		ClassPool pool = new ClassPool();
		for (Path jar : jars) pool.add(jar);
		return pool;
	}

	public void add(Path jar) throws IOException {
		forEachClass(jar, (entry, bytes) -> {
			ClassInfo info = read(bytes);
			classes.putIfAbsent(info.name(), info);
		});
	}

	/** A class from the jars, or one of the JDK's (java/, javax/) read from the running JDK. */
	public ClassInfo get(String name) {
		ClassInfo info = classes.get(name);
		if (info == null && (name.startsWith("java/") || name.startsWith("javax/"))) {
			try (InputStream in = ClassLoader.getSystemResourceAsStream(name + ".class")) {
				if (in != null) {
					info = read(in.readAllBytes());
					classes.put(name, info);
				}
			} catch (IOException e) {
				throw new java.io.UncheckedIOException(e);
			}
		}
		return info;
	}

	public Map<String, ClassInfo> all() {
		return classes;
	}

	/** JVMS 5.4.3.3: the class, its superclasses, then superinterfaces. */
	public Member resolveMethod(String owner, String name, String desc) {
		for (String c = owner; c != null; ) {
			ClassInfo info = get(c);
			if (info == null) break;
			Member m = info.methods().get(name + desc);
			if (m != null) return m;
			c = info.superName();
		}
		return resolveInInterfaces(owner, name, desc, true);
	}

	private Member resolveInInterfaces(String owner, String name, String desc, boolean skipSelf) {
		ClassInfo info = get(owner);
		if (info == null) return null;
		if (!skipSelf) {
			Member m = info.methods().get(name + desc);
			if (m != null) return m;
		}
		for (String i : info.interfaces()) {
			Member m = resolveInInterfaces(i, name, desc, false);
			if (m != null) return m;
		}
		return info.superName() == null ? null : resolveInInterfaces(info.superName(), name, desc, true);
	}

	public Member resolveField(String owner, String name, String desc) {
		ClassInfo info = get(owner);
		if (info == null) return null;
		Member f = info.fields().get(name + ":" + desc);
		if (f != null) return f;
		for (String i : info.interfaces()) {
			f = resolveField(i, name, desc);
			if (f != null) return f;
		}
		return info.superName() == null ? null : resolveField(info.superName(), name, desc);
	}

	static ClassInfo read(byte[] bytes) {
		Map<String, Member> methods = new LinkedHashMap<>();
		Map<String, Member> fields = new LinkedHashMap<>();
		String[] header = new String[2];
		List<String> interfaces = new ArrayList<>();
		int[] access = new int[1];
		new ClassReader(bytes).accept(new ClassVisitor(Opcodes.ASM9) {
			@Override
			public void visit(int version, int acc, String name, String signature, String superName, String[] ifaces) {
				header[0] = name;
				header[1] = superName;
				access[0] = acc;
				if (ifaces != null) interfaces.addAll(List.of(ifaces));
			}

			@Override
			public MethodVisitor visitMethod(int acc, String name, String desc, String signature, String[] exceptions) {
				methods.put(name + desc, new Member(name, desc, acc));
				return null;
			}

			@Override
			public FieldVisitor visitField(int acc, String name, String desc, String signature, Object value) {
				fields.put(name + ":" + desc, new Member(name, desc, acc));
				return null;
			}
		}, ClassReader.SKIP_CODE | ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
		return new ClassInfo(header[0], header[1], interfaces, access[0], methods, fields);
	}

	/** Every .class in a jar and in the jars nested in it, as (entry path, bytes). */
	public static void forEachClass(Path jar, BiConsumer<String, byte[]> action) throws IOException {
		try (InputStream in = Files.newInputStream(jar)) {
			forEachClass(in, jar.getFileName().toString(), action);
		}
	}

	private static void forEachClass(InputStream raw, String where, BiConsumer<String, byte[]> action) throws IOException {
		ZipInputStream zip = new ZipInputStream(raw);
		ZipEntry e;
		while ((e = zip.getNextEntry()) != null) {
			String name = e.getName();
			if (name.startsWith("META-INF/versions/")) continue;
			if (name.endsWith(".class") && !name.endsWith("module-info.class")) {
				action.accept(where + "!" + name, zip.readAllBytes());
			} else if (name.endsWith(".jar")) {
				forEachClass(new ByteArrayInputStream(zip.readAllBytes()), where + "!" + name, action);
			}
		}
	}
}
