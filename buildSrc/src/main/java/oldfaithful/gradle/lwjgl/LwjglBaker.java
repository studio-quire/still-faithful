package oldfaithful.gradle.lwjgl;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.jar.Attributes;
import java.util.jar.Manifest;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.commons.ClassRemapper;
import org.objectweb.asm.commons.SimpleRemapper;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.AnnotationNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.FieldNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.LdcInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.VarInsnNode;

/**
 * Builds the one jar our LWJGL component ships: LWJGL 3 (core, OpenGL, OpenAL, SDL) with the LWJGL 2 API on top.
 * <ul>
 * <li>LWJGL 3's classes, unchanged except where the LWJGL 2 API needs a method LWJGL 3 lacks: a forwarding method is
 * added to that class ({@link Forwarders}), so {@code GL11.glGetFloat(int, FloatBuffer)} calls
 * {@code GL11.glGetFloatv}. One jar, one copy of every class: nothing is transformed at runtime.</li>
 * <li>Our own classes (Display, Keyboard, Mouse, ...), and "patch" classes marked {@code @Into(target)}, whose public
 * static methods are forwarded from the target LWJGL 3 class (e.g. LWJGL 2's {@code AL.create()}). A package-private
 * LWJGL 3 method in the way is renamed, with its callers; a public one is a build error.</li>
 * <li>{@code ContextCapabilities}, generated: LWJGL 2's flags, filled from LWJGL 3's {@code GLCapabilities}.</li>
 * <li>A few small LWJGL 2 classes and the lwjgl_util packages (vector, glu, geometry), as compiled by our LWJGL 2
 * build; they're BSD like the rest, and the self link check proves what they call exists.</li>
 * </ul>
 * Entries are written sorted with one timestamp, so the same inputs give the same jar.
 */
public final class LwjglBaker {
	public static final String INTO = "Lorg/studioquire/oldfaithful/lwjgl/Into;";
	/** One timestamp for every entry, in the zip's local-time field, as tools/mirror.py does. */
	private static final java.time.LocalDateTime EPOCH = java.time.LocalDateTime.of(1980, 2, 1, 0, 0);
	private static final String CAPS = "org/lwjgl/opengl/ContextCapabilities";
	private static final String GLCAPS = "org/lwjgl/opengl/GLCapabilities";
	private static final String EXTENSIONS = "org/studioquire/oldfaithful/lwjgl/LegacyExtensions";

	public record Inputs(List<Path> lwjgl3Jars, Path legacyJar, Path legacyUtilJar, List<String> legacyClasses,
			List<String> legacyUtilPrefixes, List<String> legacyUtilExcludes, Path ours, Map<String, byte[]> extraEntries) {
	}

	private final Inputs in;
	/** Every entry of the output jar. */
	private final Map<String, byte[]> out = new TreeMap<>();
	private final Map<String, ClassNode> modified = new HashMap<>();
	private final StringBuilder report = new StringBuilder();

	private LwjglBaker(Inputs in) {
		this.in = in;
	}

	/** Bakes the jar, writes it, and returns a plain-text report of what was generated and what wasn't. */
	public static String bake(Inputs in, Path output) throws IOException {
		LwjglBaker b = new LwjglBaker(in);
		b.run();
		b.write(output);
		return b.report.toString();
	}

	private void run() throws IOException {
		for (Path jar : in.lwjgl3Jars()) {
			read(jar, (name, bytes) -> {
				if (skipFromLwjgl3(name)) return;
				if (out.put(name, bytes) != null && name.endsWith(".class")) throw new IllegalStateException("duplicate " + name);
			});
		}
		Map<String, byte[]> legacy = new HashMap<>();
		read(in.legacyJar(), legacy::put);
		Map<String, byte[]> legacyUtil = new HashMap<>();
		read(in.legacyUtilJar(), legacyUtil::put);
		Map<String, byte[]> ours = new TreeMap<>();
		read(in.ours(), (name, bytes) -> {
			if (name.endsWith(".class")) ours.put(name, bytes);
		});

		copyLegacy(legacy, legacyUtil);
		List<ClassNode> patches = new ArrayList<>();
		for (Map.Entry<String, byte[]> e : ours.entrySet()) {
			if (out.containsKey(e.getKey())) throw new IllegalStateException("our " + e.getKey() + " would replace an existing class");
			out.put(e.getKey(), e.getValue());
			ClassNode node = node(e.getValue());
			if (into(node) != null) patches.add(node);
		}
		applyPatches(patches);
		generateForwarders(legacy);
		generateCapabilities(legacy);
		for (Map.Entry<String, ClassNode> e : modified.entrySet()) out.put(e.getKey() + ".class", bytes(e.getValue()));
		out.putAll(in.extraEntries());
		// For tests that load every changed class, and for anyone wondering what was touched.
		out.put("META-INF/oldfaithful-lwjgl/changed-classes.txt", String.join("\n", new TreeSet<>(modified.keySet())).concat("\n")
				.getBytes(java.nio.charset.StandardCharsets.UTF_8));
		out.put("META-INF/oldfaithful-lwjgl/bake-report.txt", report.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8));
		Manifest manifest = new Manifest();
		manifest.getMainAttributes().put(Attributes.Name.MANIFEST_VERSION, "1.0");
		manifest.getMainAttributes().put(new Attributes.Name("Multi-Release"), "true");
		manifest.getMainAttributes().put(Attributes.Name.IMPLEMENTATION_TITLE, "Pure Faithful LWJGL: the LWJGL 2 API on LWJGL 3 and SDL3");
		ByteArrayOutputStream m = new ByteArrayOutputStream();
		manifest.write(m);
		out.put("META-INF/MANIFEST.MF", m.toByteArray());
	}

	/** LWJGL 3 entries we leave out: per-jar metadata that a merged jar would get wrong. */
	private static boolean skipFromLwjgl3(String name) {
		return name.equals("META-INF/MANIFEST.MF") || name.equals("META-INF/INDEX.LIST") || name.endsWith("module-info.class")
				|| name.endsWith(".SF") || name.endsWith(".RSA") || name.endsWith(".DSA")
				// Hashes of LWJGL's own natives; ours differ (the extra entries carry them).
				|| (name.startsWith("META-INF/") && name.endsWith(".sha1") && !name.startsWith("META-INF/versions/"))
				|| name.endsWith("/");
	}

	private void copyLegacy(Map<String, byte[]> legacy, Map<String, byte[]> legacyUtil) {
		int util = 0;
		for (String c : in.legacyClasses()) {
			int n = 0;
			for (Map.Entry<String, byte[]> e : legacy.entrySet()) {
				String name = e.getKey();
				if (name.equals(c + ".class") || name.startsWith(c + "$")) {
					if (out.put(name, e.getValue()) != null) throw new IllegalStateException("LWJGL 2 " + name + " clashes with LWJGL 3");
					n++;
				}
			}
			if (n == 0) throw new IllegalStateException("no " + c + " in " + in.legacyJar());
		}
		for (Map.Entry<String, byte[]> e : legacyUtil.entrySet()) {
			String name = e.getKey();
			if (!name.endsWith(".class")) continue;
			boolean include = in.legacyUtilPrefixes().stream().anyMatch(name::startsWith)
					&& in.legacyUtilExcludes().stream().noneMatch(name::startsWith);
			if (!include) continue;
			if (out.put(name, e.getValue()) != null) throw new IllegalStateException("lwjgl_util " + name + " clashes");
			util++;
		}
		report.append("LWJGL 2 classes copied: ").append(in.legacyClasses()).append("; lwjgl_util classes: ").append(util).append('\n');
	}

	private static String into(ClassNode node) {
		for (List<AnnotationNode> list : java.util.Arrays.asList(node.invisibleAnnotations, node.visibleAnnotations)) {
			if (list == null) continue;
			for (AnnotationNode a : list) {
				if (a.desc.equals(INTO)) return ((String) a.values.get(1)).replace('.', '/');
			}
		}
		return null;
	}

	/** Forwards each patch class's public static methods from its target. */
	private void applyPatches(List<ClassNode> patches) {
		Map<String, String> renames = new HashMap<>();
		for (ClassNode patch : patches) {
			String target = into(patch);
			ClassNode t = modifiable(target);
			for (MethodNode m : patch.methods) {
				if ((m.access & Opcodes.ACC_PUBLIC) == 0 || (m.access & Opcodes.ACC_STATIC) == 0) continue;
				MethodNode existing = find(t, m.name, m.desc);
				if (existing != null) {
					if ((existing.access & (Opcodes.ACC_PUBLIC | Opcodes.ACC_PROTECTED)) != 0) {
						throw new IllegalStateException(patch.name + "." + m.name + m.desc + " would replace public " + target + "." + m.name);
					}
					renames.put(target + "." + m.name + m.desc, "lwjgl3$" + m.name);
				}
			}
		}
		if (!renames.isEmpty()) {
			// Rename in the declaring classes and every caller in LWJGL 3 (package-private, so only its own package).
			SimpleRemapper remapper = new SimpleRemapper(renames);
			Set<String> packages = new TreeSet<>();
			for (String key : renames.keySet()) packages.add(key.substring(0, key.lastIndexOf('/', key.indexOf('.')) + 1));
			for (String name : new ArrayList<>(out.keySet())) {
				if (!name.endsWith(".class")) continue;
				String cls = name.substring(0, name.length() - 6);
				String pkg = cls.startsWith("META-INF/versions/") ? cls.replaceFirst("^META-INF/versions/\\d+/", "") : cls;
				if (packages.stream().noneMatch(p -> pkg.startsWith(p) && pkg.indexOf('/', p.length()) < 0)) continue;
				if (!mentionsAny(out.get(name), renames.keySet())) continue;
				if (cls.startsWith("META-INF/versions/")) {
					ClassWriter w = new ClassWriter(0);
					new ClassReader(out.get(name)).accept(new ClassRemapper(w, remapper), 0);
					out.put(name, w.toByteArray());
				} else {
					ClassNode src = modifiable(cls);
					ClassNode renamed = new ClassNode();
					src.accept(new ClassRemapper(renamed, remapper));
					modified.put(cls, renamed);
				}
			}
			report.append("Renamed in LWJGL 3 to make room: ").append(new TreeMap<>(renames)).append('\n');
		}
		for (ClassNode patch : patches) {
			String target = into(patch);
			ClassNode t = modifiable(target);
			int n = 0;
			for (MethodNode m : patch.methods) {
				if ((m.access & Opcodes.ACC_PUBLIC) == 0 || (m.access & Opcodes.ACC_STATIC) == 0) continue;
				t.methods.add(forwarder(m.name, m.desc, patch.name));
				n++;
			}
			checkVersioned(target);
			report.append("Patch ").append(patch.name).append(" -> ").append(target).append(": ").append(n).append(" methods\n");
		}
	}

	/** Whether a class calls or declares any of the methods named in rename keys ("owner.name+desc"). */
	private static boolean mentionsAny(byte[] bytes, Set<String> keys) {
		boolean[] found = new boolean[1];
		new ClassReader(bytes).accept(new org.objectweb.asm.ClassVisitor(Opcodes.ASM9) {
			private String self;

			@Override
			public void visit(int version, int access, String name, String signature, String superName, String[] interfaces) {
				self = name;
			}

			@Override
			public org.objectweb.asm.MethodVisitor visitMethod(int access, String name, String desc, String signature, String[] exceptions) {
				if (keys.contains(self + "." + name + desc)) found[0] = true;
				return new org.objectweb.asm.MethodVisitor(Opcodes.ASM9) {
					@Override
					public void visitMethodInsn(int opcode, String owner, String n, String d, boolean itf) {
						if (keys.contains(owner + "." + n + d)) found[0] = true;
					}
				};
			}
		}, ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
		return found[0];
	}

	private static MethodNode forwarder(String name, String desc, String toOwner) {
		MethodNode m = new MethodNode(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, name, desc, null, null);
		int local = 0;
		for (Type a : Type.getArgumentTypes(desc)) {
			m.instructions.add(new VarInsnNode(a.getOpcode(Opcodes.ILOAD), local));
			local += a.getSize();
		}
		m.instructions.add(new MethodInsnNode(Opcodes.INVOKESTATIC, toOwner, name, desc, false));
		m.instructions.add(new InsnNode(Type.getReturnType(desc).getOpcode(Opcodes.IRETURN)));
		return m;
	}

	/** A class we add methods to must not have a multi-release variant, which would hide our additions on newer Javas. */
	private void checkVersioned(String cls) {
		for (String name : out.keySet()) {
			if (name.startsWith("META-INF/versions/") && name.endsWith("/" + cls + ".class")) {
				throw new IllegalStateException(cls + " has a multi-release variant " + name + "; patching it isn't supported");
			}
		}
	}

	/** Forwarders for every LWJGL 2 GL and AL binding method that LWJGL 3 has under another name or signature. */
	private void generateForwarders(Map<String, byte[]> legacy) {
		ClassPool pool = new ClassPool();
		Map<String, ClassNode> legacyNodes = new TreeMap<>();
		for (Map.Entry<String, byte[]> e : legacy.entrySet()) {
			String name = e.getKey();
			if (!name.endsWith(".class")) continue;
			String cls = name.substring(0, name.length() - 6);
			if ((cls.startsWith("org/lwjgl/opengl/") || cls.startsWith("org/lwjgl/openal/")) && !cls.contains("$")) {
				legacyNodes.put(cls, node(e.getValue()));
			}
		}
		Set<String> missingClasses = new TreeSet<>();
		int total = 0, already = 0, generated = 0, typed = 0;
		Map<String, List<String>> unresolved = new TreeMap<>();
		for (ClassNode legacyClass : legacyNodes.values()) {
			if ((legacyClass.access & Opcodes.ACC_PUBLIC) == 0) continue;
			List<MethodNode> api = legacyStatics(legacyClass, legacyNodes);
			if (api.isEmpty()) continue;
			if (!out.containsKey(legacyClass.name + ".class")) {
				missingClasses.add(legacyClass.name.substring(legacyClass.name.lastIndexOf('/') + 1));
				continue;
			}
			Map<String, Boolean> available = lwjgl3Statics(legacyClass.name);
			ClassNode target = null;
			for (MethodNode m : api) {
				total++;
				Checks.Found checks = Checks.extract(legacyClass.name, m);
				if (!checks.skipped().isEmpty()) {
					checksSkipped.computeIfAbsent(simpleName(legacyClass.name), k -> new TreeSet<>()).add(m.name + checks.skipped());
				}
				if (available.containsKey(m.name + m.desc)) {
					already++;
					if (!checks.isEmpty()) checkExisting(legacyClass.name, m, checks);
					continue;
				}
				String missingType = missingType(m.desc);
				Forwarders.Plan plan = missingType == null ? Forwarders.plan(legacyClass.name, m, available) : null;
				if (plan == null && missingType == null && !declaredIn(legacyClass, m)) {
					// Inherited in LWJGL 2 from a class LWJGL 3 doesn't have (ARBProgram, ARBBufferObject): the same GL
					// function, wherever LWJGL 3 puts it.
					plan = elsewhere(legacyClass.name, m, legacyClass.name.substring(0, legacyClass.name.lastIndexOf('/') + 1));
				}
				if (plan == null) {
					unresolved.computeIfAbsent(legacyClass.name.substring(legacyClass.name.lastIndexOf('/') + 1), k -> new ArrayList<>())
							.add(m.name + m.desc + (missingType != null ? " [type " + missingType + "]" : ""));
					continue;
				}
				if (target == null) target = modifiable(legacyClass.name);
				MethodNode forwarder = plan.build();
				if (!checks.isEmpty() && checkedMethods.add(legacyClass.name + "." + m.name + m.desc)) prependChecks(forwarder, checks);
				target.methods.add(forwarder);
				available.put(m.name + m.desc, true);
				generated++;
				if (plan.insertsType()) typed++;
			}
			if (target != null) checkVersioned(legacyClass.name);
		}
		report.append("GL/AL binding methods in LWJGL 2: ").append(total).append("; already in LWJGL 3: ").append(already)
				.append("; forwarders generated: ").append(generated).append(" (").append(typed).append(" supplying a GL type)")
				.append("; unmapped: ").append(total - already - generated).append('\n');
		report.append("LWJGL 2 binding classes LWJGL 3 doesn't have (not provided): ").append(missingClasses).append('\n');
		report.append("LWJGL 2's argument checks restored: ").append(checksCopied).append(" in ").append(checkedMethods.size())
				.append(" methods; not restored (by class: method[checks]):\n");
		checksSkipped.forEach((c, ms) -> report.append("  ").append(c).append(" (").append(ms.size()).append("): ").append(String.join(", ", ms)).append('\n'));
		report.append("Unmapped methods, by class:\n");
		unresolved.forEach((c, ms) -> report.append("  ").append(c).append(" (").append(ms.size()).append("): ").append(String.join(", ", ms)).append('\n'));
	}

	private final Set<String> checkedMethods = new HashSet<>();
	private final Map<String, Set<String>> checksSkipped = new TreeMap<>();
	private int checksCopied;

	/** LWJGL 2's checks at the top of the LWJGL 3 method (declared in the class or a superclass) its callers reach. */
	private void checkExisting(String cls, MethodNode legacy, Checks.Found checks) {
		for (String c = cls; c != null && out.containsKey(c + ".class"); ) {
			ClassNode n = modified.containsKey(c) ? modified.get(c) : node(out.get(c + ".class"));
			MethodNode existing = find(n, legacy.name, legacy.desc);
			if (existing != null && (existing.access & Opcodes.ACC_STATIC) != 0) {
				if (!checkedMethods.add(c + "." + legacy.name + legacy.desc)) return;
				prependChecks(find(modifiable(c), legacy.name, legacy.desc), checks);
				checkVersioned(c);
				return;
			}
			c = n.superName;
		}
		throw new IllegalStateException("no LWJGL 3 " + cls + "." + legacy.name + legacy.desc + " to check in");
	}

	private void prependChecks(MethodNode m, Checks.Found checks) {
		InsnList copy = new InsnList();
		for (AbstractInsnNode insn : checks.code()) copy.add(insn.clone(Map.of()));
		m.instructions.insert(copy);
		checksCopied += checks.copied();
	}

	private static String simpleName(String cls) {
		return cls.substring(cls.lastIndexOf('/') + 1);
	}

	private static boolean declaredIn(ClassNode c, MethodNode m) {
		return find(c, m.name, m.desc) != null;
	}

	private Map<String, Map<String, Boolean>> packageStatics;

	/** A plan forwarding to whichever class of the package declares the method; a class with the same vendor prefix first. */
	private Forwarders.Plan elsewhere(String from, MethodNode m, String pkg) {
		if (packageStatics == null) {
			packageStatics = new TreeMap<>();
			for (String name : out.keySet()) {
				if (!name.startsWith(pkg) || !name.endsWith(".class") || name.indexOf('/', pkg.length()) >= 0 || name.contains("$")) continue;
				String cls = name.substring(0, name.length() - 6);
				packageStatics.put(cls, lwjgl3Statics(cls));
			}
		}
		String simple = from.substring(pkg.length());
		String vendor = simple.replaceAll("^([A-Z]+?)[A-Z][a-z].*$", "$1");
		List<String> owners = new ArrayList<>(packageStatics.keySet());
		owners.sort(java.util.Comparator.comparing(o -> o.substring(pkg.length()).startsWith(vendor) ? 0 : 1));
		for (String owner : owners) {
			if (owner.equals(from)) continue;
			Forwarders.Plan p = Forwarders.plan(owner, m, packageStatics.get(owner));
			if (p != null) return p;
		}
		return null;
	}

	/** Public static methods callable through an LWJGL 2 class: its own and its LWJGL 2 superclasses'. */
	private static List<MethodNode> legacyStatics(ClassNode c, Map<String, ClassNode> all) {
		Map<String, MethodNode> methods = new LinkedHashMap<>();
		for (ClassNode k = c; k != null; k = all.get(k.superName)) {
			for (MethodNode m : k.methods) {
				if ((m.access & Opcodes.ACC_PUBLIC) != 0 && (m.access & Opcodes.ACC_STATIC) != 0) methods.putIfAbsent(m.name + m.desc, m);
			}
		}
		return new ArrayList<>(methods.values());
	}

	/** name+desc of public static methods reachable through an output class, superclasses included. */
	private Map<String, Boolean> lwjgl3Statics(String cls) {
		Map<String, Boolean> methods = new HashMap<>();
		for (String c = cls; c != null && out.containsKey(c + ".class"); ) {
			ClassNode n = modified.containsKey(c) ? modified.get(c) : node(out.get(c + ".class"));
			for (MethodNode m : n.methods) {
				if ((m.access & Opcodes.ACC_PUBLIC) != 0 && (m.access & Opcodes.ACC_STATIC) != 0) methods.putIfAbsent(m.name + m.desc, true);
			}
			c = n.superName;
		}
		return methods;
	}

	/** An org/lwjgl type in a descriptor that the output jar won't have, or null. */
	private String missingType(String desc) {
		List<Type> types = new ArrayList<>(List.of(Type.getArgumentTypes(desc)));
		types.add(Type.getReturnType(desc));
		for (Type t : types) {
			if (t.getSort() == Type.ARRAY) t = t.getElementType();
			if (t.getSort() == Type.OBJECT && t.getInternalName().startsWith("org/lwjgl/") && !out.containsKey(t.getInternalName() + ".class")) {
				return t.getInternalName();
			}
		}
		return null;
	}

	/**
	 * org.lwjgl.opengl.ContextCapabilities: LWJGL 2's public boolean flags (OpenGL11..., GL_ARB_...), each copied
	 * from LWJGL 3's GLCapabilities field of the same name; the few LWJGL 3 doesn't know stay false.
	 */
	private void generateCapabilities(Map<String, byte[]> legacy) {
		ClassNode legacyCaps = node(legacy.get(CAPS + ".class"));
		ClassNode glCaps = node(out.get(GLCAPS + ".class"));
		Set<String> known = new TreeSet<>();
		for (FieldNode f : glCaps.fields) {
			if ((f.access & Opcodes.ACC_PUBLIC) != 0 && (f.access & Opcodes.ACC_STATIC) == 0 && f.desc.equals("Z")) known.add(f.name);
		}
		ClassNode caps = new ClassNode();
		caps.visit(Opcodes.V1_8, Opcodes.ACC_PUBLIC | Opcodes.ACC_FINAL | Opcodes.ACC_SUPER, CAPS, null, "java/lang/Object", null);
		MethodNode init = new MethodNode(Opcodes.ACC_PUBLIC, "<init>", "(L" + GLCAPS + ";)V", null, null);
		init.instructions.add(new VarInsnNode(Opcodes.ALOAD, 0));
		init.instructions.add(new MethodInsnNode(Opcodes.INVOKESPECIAL, "java/lang/Object", "<init>", "()V", false));
		Map<String, List<String>> extensionsOf = legacyFlagExtensions(legacyCaps);
		Set<String> withFunctions = new TreeSet<>();
		for (MethodNode m : legacyCaps.methods) {
			if (m.name.endsWith("_initNativeFunctionAddresses")) withFunctions.add("GL_" + m.name.substring(0, m.name.length() - "_initNativeFunctionAddresses".length()));
		}
		init.instructions.add(new VarInsnNode(Opcodes.ALOAD, 1));
		init.instructions.add(new MethodInsnNode(Opcodes.INVOKESTATIC, EXTENSIONS, "current", "(L" + GLCAPS + ";)Ljava/util/Set;", false));
		init.instructions.add(new VarInsnNode(Opcodes.ASTORE, 2));
		int copied = 0;
		List<String> fromStrings = new ArrayList<>(), noBinding = new ArrayList<>();
		for (FieldNode f : legacyCaps.fields) {
			if ((f.access & Opcodes.ACC_PUBLIC) == 0 || (f.access & Opcodes.ACC_STATIC) != 0 || !f.desc.equals("Z")) continue;
			caps.fields.add(new FieldNode(Opcodes.ACC_PUBLIC | Opcodes.ACC_FINAL, f.name, "Z", null, null));
			init.instructions.add(new VarInsnNode(Opcodes.ALOAD, 0));
			List<String> extensions = extensionsOf.getOrDefault(f.name, List.of());
			if (known.contains(f.name)) {
				init.instructions.add(new VarInsnNode(Opcodes.ALOAD, 1));
				init.instructions.add(new FieldInsnNode(Opcodes.GETFIELD, GLCAPS, f.name, "Z"));
				copied++;
			} else if (!extensions.isEmpty() && extensions.stream().noneMatch(withFunctions::contains)) {
				// LWJGL 2 set these from the extension string alone (no functions to load): so do we.
				init.instructions.add(new VarInsnNode(Opcodes.ALOAD, 2));
				init.instructions.add(new LdcInsnNode(String.join(" ", extensions)));
				init.instructions.add(new MethodInsnNode(Opcodes.INVOKESTATIC, EXTENSIONS, "any", "(Ljava/util/Set;Ljava/lang/String;)Z", false));
				fromStrings.add(f.name);
			} else {
				// An extension with functions LWJGL 3 has no binding for, so its LWJGL 2 class isn't in the jar: LWJGL 2
				// reported false when it couldn't load an extension's functions, and true would send callers into a
				// NoClassDefFoundError.
				init.instructions.add(new InsnNode(Opcodes.ICONST_0));
				noBinding.add(f.name);
			}
			init.instructions.add(new FieldInsnNode(Opcodes.PUTFIELD, CAPS, f.name, "Z"));
		}
		init.instructions.add(new InsnNode(Opcodes.RETURN));
		caps.methods.add(init);
		modified.put(CAPS, caps);
		report.append("ContextCapabilities: ").append(copied).append(" flags from GLCapabilities; ").append(fromStrings.size())
				.append(" from the extension string, as LWJGL 2 set them: ").append(fromStrings).append("; always false (functions LWJGL 3 has no binding for): ")
				.append(noBinding).append('\n');
	}

	/**
	 * The extension strings each LWJGL 2 flag is set from, read from its constructor: every string tested between one
	 * flag's assignment and the previous one ({@code supported_extensions.contains("GL_...")}, sometimes two or'd).
	 */
	private static Map<String, List<String>> legacyFlagExtensions(ClassNode legacyCaps) {
		Map<String, List<String>> result = new HashMap<>();
		for (MethodNode m : legacyCaps.methods) {
			if (!m.name.equals("<init>")) continue;
			List<String> pending = new ArrayList<>();
			for (AbstractInsnNode insn : m.instructions) {
				if (insn instanceof LdcInsnNode l && l.cst instanceof String s) {
					pending.add(s);
				} else if (insn instanceof FieldInsnNode fi && fi.getOpcode() == Opcodes.PUTFIELD && fi.owner.equals(CAPS) && fi.desc.equals("Z")) {
					result.put(fi.name, List.copyOf(pending));
					pending.clear();
				} else if (insn instanceof FieldInsnNode fi && fi.getOpcode() == Opcodes.PUTFIELD) {
					pending.clear();
				}
			}
		}
		return result;
	}

	private ClassNode modifiable(String cls) {
		return modified.computeIfAbsent(cls, c -> {
			byte[] bytes = out.get(c + ".class");
			if (bytes == null) throw new IllegalStateException("no class " + c + " to patch");
			return node(bytes);
		});
	}

	private static MethodNode find(ClassNode c, String name, String desc) {
		for (MethodNode m : c.methods) if (m.name.equals(name) && m.desc.equals(desc)) return m;
		return null;
	}

	private static ClassNode node(byte[] bytes) {
		ClassNode n = new ClassNode();
		new ClassReader(bytes).accept(n, 0);
		return n;
	}

	/** Existing frames are kept; added methods are straight-line, so they need none. */
	private static byte[] bytes(ClassNode n) {
		ClassWriter w = new ClassWriter(ClassWriter.COMPUTE_MAXS);
		n.accept(w);
		return w.toByteArray();
	}

	private interface EntryConsumer {
		void accept(String name, byte[] bytes) throws IOException;
	}

	private static void read(Path jar, EntryConsumer action) throws IOException {
		if (Files.isDirectory(jar)) {
			try (var walk = Files.walk(jar)) {
				for (Path p : (Iterable<Path>) walk.filter(Files::isRegularFile)::iterator) {
					action.accept(jar.relativize(p).toString().replace('\\', '/'), Files.readAllBytes(p));
				}
			}
			return;
		}
		try (InputStream raw = Files.newInputStream(jar); ZipInputStream zip = new ZipInputStream(raw)) {
			ZipEntry e;
			while ((e = zip.getNextEntry()) != null) {
				if (!e.isDirectory()) action.accept(e.getName(), zip.readAllBytes());
			}
		}
	}

	private void write(Path output) throws IOException {
		Files.createDirectories(output.getParent());
		try (OutputStream o = Files.newOutputStream(output); ZipOutputStream zip = new ZipOutputStream(o)) {
			List<String> names = new ArrayList<>(out.keySet());
			names.remove("META-INF/MANIFEST.MF");
			names.add(0, "META-INF/MANIFEST.MF");
			for (String name : names) {
				ZipEntry e = new ZipEntry(name);
				e.setTimeLocal(EPOCH);
				zip.putNextEntry(e);
				zip.write(out.get(name));
				zip.closeEntry();
			}
		}
	}
}
