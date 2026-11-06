package oldfaithful.gradle;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Handle;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.AnnotationNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.FieldNode;
import org.objectweb.asm.tree.InvokeDynamicInsnNode;
import org.objectweb.asm.tree.JumpInsnNode;
import org.objectweb.asm.tree.LdcInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.TypeInsnNode;

/**
 * The conflict harness's first stage (docs/library-harness.md): what can be read from a mod's jar without launching anything. Jars are the
 * named ({@code -dev}) ones, so targets read as the source does. Each failure names the mod and the rule.
 */
final class LibraryStatic {
	/** The core may touch what it likes: that's its job. It keeps to the rules of what a jar holds, and of mixins. */
	static final String CORE = "faithful-core";

	private static final String CLIENT = "CLIENT", SERVER = "SERVER";
	private static final String ENVIRONMENT = "Lnet/fabricmc/api/Environment;";
	private static final String MIXIN = "Lorg/spongepowered/asm/mixin/Mixin;";
	private static final String OVERWRITE = "Lorg/spongepowered/asm/mixin/Overwrite;";
	private static final String REDIRECT = "Lorg/spongepowered/asm/mixin/injection/Redirect;";
	private static final Set<String> INJECTORS = Set.of(REDIRECT, OVERWRITE,
			"Lorg/spongepowered/asm/mixin/injection/Inject;", "Lorg/spongepowered/asm/mixin/injection/ModifyArg;", "Lorg/spongepowered/asm/mixin/injection/ModifyArgs;",
			"Lorg/spongepowered/asm/mixin/injection/ModifyConstant;", "Lorg/spongepowered/asm/mixin/injection/ModifyVariable;",
			"Lcom/llamalad7/mixinextras/injector/ModifyExpressionValue;", "Lcom/llamalad7/mixinextras/injector/ModifyReturnValue;",
			"Lcom/llamalad7/mixinextras/injector/ModifyReceiver;", "Lcom/llamalad7/mixinextras/injector/WrapWithCondition;",
			"Lcom/llamalad7/mixinextras/injector/v2/WrapWithCondition;", "Lcom/llamalad7/mixinextras/injector/wrapoperation/WrapOperation;",
			"Lcom/llamalad7/mixinextras/injector/wrapmethod/WrapMethod;");

	/** A mod's jar, with what the task knows of it. */
	record Subject(Path jar, boolean library, boolean selftest) {
		Subject(Path jar, boolean library) {
			this(jar, library, false);
		}
	}

	/** Where a selftest's code runs: from these entrypoints, and what they reach. */
	private static final List<String> TEST_COMMON = List.of("faithful:mod", "faithful:test"), TEST_CLIENT = List.of("faithful:client", "faithful:test-client", "faithful:test-without", "oldfaithful-selftest:world");
	/** The test API's client half, which a server hasn't got. */
	private static final List<String> TEST_CLIENT_CLASSES = List.of("org/studioquire/oldfaithful/selftest/", "org/studioquire/faithful/harness/test/client/");

	/** The game: which side each class and member is on, from the marks the jar merger left. */
	private static final class Game {
		record Info(String superName, List<String> interfaces, String side, Map<String, String> members, Set<String> oneSidedInterfaces) {
		}

		final Map<String, Info> classes = new HashMap<>();

		static String side(List<AnnotationNode> visible, List<AnnotationNode> invisible) {
			for (List<AnnotationNode> list : java.util.Arrays.asList(visible, invisible)) {
				if (list == null) continue;
				for (AnnotationNode a : list) {
					if (!a.desc.equals(ENVIRONMENT) || a.values == null) continue;
					for (int i = 0; i + 1 < a.values.size(); i += 2) {
						if (a.values.get(i).equals("value")) return ((String[]) a.values.get(i + 1))[1];
					}
				}
			}
			return null;
		}

		void read(Path jar) throws IOException {
			try (ZipFile zip = new ZipFile(jar.toFile())) {
				for (ZipEntry e : java.util.Collections.list(zip.entries())) {
					if (!e.getName().endsWith(".class")) continue;
					ClassNode c = node(zip, e);
					Map<String, String> members = new HashMap<>();
					for (MethodNode m : c.methods) {
						String s = side(m.visibleAnnotations, m.invisibleAnnotations);
						members.put(m.name + m.desc, s == null ? "" : s);
					}
					for (FieldNode f : c.fields) {
						String s = side(f.visibleAnnotations, f.invisibleAnnotations);
						members.put(f.name + ":" + f.desc, s == null ? "" : s);
					}
					classes.put(c.name, new Info(c.superName, c.interfaces, side(c.visibleAnnotations, c.invisibleAnnotations), members, Set.of()));
				}
			}
		}

		/** The side-only mark on what this reference reaches: "" for common, {@code null} if nothing's there. */
		String sideOf(String owner, String member) {
			Set<String> seen = new HashSet<>();
			List<String> queue = new ArrayList<>(List.of(owner));
			boolean anyGameClass = false;
			while (!queue.isEmpty()) {
				String name = queue.remove(0);
				if (!seen.add(name)) continue;
				Info info = classes.get(name);
				if (info == null) continue;
				anyGameClass = true;
				String side = info.members.get(member);
				if (side != null) return !side.isEmpty() ? side : info.side != null ? info.side : "";
				if (info.superName != null) queue.add(info.superName);
				queue.addAll(info.interfaces);
			}
			// Inherited from the JDK, or a class that isn't the game's.
			return anyGameClass && !seen.stream().anyMatch(n -> !classes.containsKey(n) && n.startsWith("java/")) ? null : "";
		}

		String sideOfClass(String name) {
			Info info = classes.get(name);
			return info == null ? null : info.side == null ? "" : info.side;
		}
	}

	private static ClassNode node(ZipFile zip, ZipEntry e) throws IOException {
		try (InputStream in = zip.getInputStream(e)) {
			ClassNode c = new ClassNode();
			new ClassReader(in).accept(c, ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
			return c;
		}
	}

	private static String text(ZipFile zip, String name) throws IOException {
		ZipEntry e = zip.getEntry(name);
		if (e == null) return null;
		try (InputStream in = zip.getInputStream(e)) {
			return new String(in.readAllBytes(), StandardCharsets.UTF_8);
		}
	}

	private static final Pattern MOD_ID = Pattern.compile("\"id\"\\s*:\\s*\"([^\"]+)\"");
	private static final Pattern STRING = Pattern.compile("\"([^\"]+)\"");

	private static List<String> list(String json, String key) {
		List<String> out = new ArrayList<>();
		Matcher m = Pattern.compile("\"" + key + "\"\\s*:\\s*\\[(.*?)\\]", Pattern.DOTALL).matcher(json);
		if (m.find()) {
			Matcher s = STRING.matcher(m.group(1));
			while (s.find()) {
				if (!s.group(1).equals("config") && !s.group(1).equals("environment") && !s.group(1).equals("client") && !s.group(1).equals("server")) out.add(s.group(1));
			}
		}
		return out;
	}

	private static String packageOf(String config) {
		Matcher m = Pattern.compile("\"package\"\\s*:\\s*\"([^\"]+)\"").matcher(config);
		return m.find() ? m.group(1).replace('.', '/') + "/" : "";
	}

	/** Where a library mod's classes live: its id without its dashes, under the library's package. */
	static String ownPackage(String id) {
		return id.equals(CORE) ? "org/studioquire/oldfaithful/core/" : "org/studioquire/faithful/" + id.replace("-", "") + "/";
	}

	private final Game game = new Game();
	private final Set<String> failures = new LinkedHashSet<>();
	/** Target method to the mods on it, and each injector's line. */
	private final Map<String, Set<String>> byMethod = new TreeMap<>();
	private final Map<String, Set<String>> redirects = new TreeMap<>();
	private final Map<String, List<String>> targets = new TreeMap<>();
	private final Set<String> libraryMods = new TreeSet<>();
	private int classesRead;

	LibraryStatic(Path namedGame) throws IOException {
		game.read(namedGame);
	}

	private void fail(String mod, String rule, String what) {
		failures.add(mod + ": " + rule + ": " + what);
	}

	void read(Subject subject) throws IOException {
		try (ZipFile zip = new ZipFile(subject.jar().toFile())) {
			String json = text(zip, "fabric.mod.json");
			if (json == null) {
				if (subject.library()) failures.add(subject.jar().getFileName() + ": no fabric.mod.json");
				return;
			}
			Matcher idMatch = MOD_ID.matcher(json);
			if (!idMatch.find()) throw new IOException("no id in " + subject.jar());
			String id = idMatch.group(1);
			if (subject.selftest()) {
				readSelftest(id, zip, json);
				return;
			}
			if (subject.library()) libraryMods.add(id);

			Set<String> clientMixins = new HashSet<>(), serverMixins = new HashSet<>(), commonMixins = new HashSet<>();
			Set<String> configs = new LinkedHashSet<>(list(json, "mixins"));
			for (String config : configs) {
				String text = text(zip, config);
				if (text == null) {
					fail(id, "a mixin config it names isn't in the jar", config);
					continue;
				}
				String base = packageOf(text);
				for (String m : list(text, "mixins")) commonMixins.add(base + m.replace('.', '/'));
				for (String m : list(text, "client")) clientMixins.add(base + m.replace('.', '/'));
				for (String m : list(text, "server")) serverMixins.add(base + m.replace('.', '/'));
			}

			for (ZipEntry e : java.util.Collections.list(zip.entries())) {
				String name = e.getName();
				if (e.isDirectory()) continue;
				if (name.endsWith(".class")) {
					classesRead++;
					ClassNode c = node(zip, e);
					if (subject.library() && !name.startsWith(ownPackage(id))) fail(id, "classes stay in the mod's own package, " + ownPackage(id), name);
					readClass(id, subject.library(), c, commonMixins, clientMixins, serverMixins);
				} else if (subject.library() && !allowedFile(id, name, configs)) {
					fail(id, "nothing outside assets/" + id + "/ and its own package", name);
				}
			}
		}
	}

	/** Only where each class's code can run, and that it keeps to that: a test isn't shipped, but a client-only call in one still stops it on a server. */
	private void readSelftest(String id, ZipFile zip, String json) throws IOException {
		Map<String, ClassNode> classes = new HashMap<>();
		for (ZipEntry e : java.util.Collections.list(zip.entries())) {
			if (e.getName().endsWith(".class")) {
				ClassNode c = node(zip, e);
				classes.put(c.name, c);
				classesRead++;
			}
		}
		Set<String> common = reached(classes, json, TEST_COMMON), client = reached(classes, json, TEST_CLIENT);
		for (ClassNode c : classes.values()) {
			String side = codeSide(c);
			if (side == null && !common.contains(c.name) && client.contains(c.name)) side = CLIENT;
			references(id, c, side);
		}
	}

	/** Each class these entrypoints reach, stopping at code that's marked for one side. */
	private static Set<String> reached(Map<String, ClassNode> classes, String json, List<String> entrypoints) {
		Set<String> seen = new HashSet<>();
		List<String> todo = new ArrayList<>();
		for (String entrypoint : entrypoints) {
			for (String name : list(json, entrypoint)) todo.add(name.replace('.', '/'));
		}
		while (!todo.isEmpty()) {
			String name = todo.remove(todo.size() - 1);
			ClassNode c = classes.get(name);
			if (c == null || !seen.add(name) || codeSide(c) != null) continue;
			todo.addAll(mentioned(c));
		}
		return seen;
	}

	/** {@code null} for code not marked for one side. */
	private static String codeSide(ClassNode c) {
		String marked = Game.side(c.visibleAnnotations, c.invisibleAnnotations);
		if (marked != null) return marked;
		return c.name.contains("/client/") ? CLIENT : c.name.contains("/server/") ? SERVER : null;
	}

	private static Set<String> mentioned(ClassNode c) {
		Set<String> names = new HashSet<>(c.interfaces);
		if (c.superName != null) names.add(c.superName);
		for (MethodNode m : c.methods) {
			if (m.instructions == null) continue;
			for (AbstractInsnNode i = m.instructions.getFirst(); i != null; i = i.getNext()) {
				if (i instanceof MethodInsnNode) names.add(((MethodInsnNode) i).owner);
				else if (i instanceof FieldInsnNode) names.add(((FieldInsnNode) i).owner);
				else if (i instanceof TypeInsnNode) names.add(Type.getObjectType(((TypeInsnNode) i).desc).getInternalName());
				else if (i instanceof InvokeDynamicInsnNode) {
					for (Object arg : ((InvokeDynamicInsnNode) i).bsmArgs) {
						if (arg instanceof Handle) names.add(((Handle) arg).getOwner());
					}
				}
			}
		}
		return names;
	}

	private static boolean allowedFile(String id, String name, Set<String> configs) {
		return name.equals("fabric.mod.json") || configs.contains(name) || name.startsWith("LICENSE") || name.startsWith("META-INF/") || name.startsWith("assets/" + id + "/");
	}

	private static List<AnnotationNode> annotations(List<AnnotationNode> a, List<AnnotationNode> b) {
		List<AnnotationNode> all = new ArrayList<>();
		if (a != null) all.addAll(a);
		if (b != null) all.addAll(b);
		return all;
	}

	private static Object value(AnnotationNode a, String key) {
		if (a.values == null) return null;
		for (int i = 0; i + 1 < a.values.size(); i += 2) {
			if (a.values.get(i).equals(key)) return a.values.get(i + 1);
		}
		return null;
	}

	@SuppressWarnings("unchecked")
	private static List<String> mixinTargets(AnnotationNode mixin) {
		List<String> out = new ArrayList<>();
		Object types = value(mixin, "value");
		if (types instanceof List) {
			for (Object t : (List<Object>) types) out.add(((Type) t).getInternalName());
		}
		Object names = value(mixin, "targets");
		if (names instanceof List) {
			for (Object t : (List<Object>) names) out.add(((String) t).replace('.', '/'));
		}
		return out;
	}

	private void readClass(String id, boolean library, ClassNode c, Set<String> commonMixins, Set<String> clientMixins, Set<String> serverMixins) {
		boolean clientCode = c.name.contains("/client/") || clientMixins.contains(c.name) || CLIENT.equals(Game.side(c.visibleAnnotations, c.invisibleAnnotations));
		boolean serverCode = c.name.contains("/server/") || serverMixins.contains(c.name) || SERVER.equals(Game.side(c.visibleAnnotations, c.invisibleAnnotations));

		AnnotationNode mixin = null;
		for (AnnotationNode a : annotations(c.visibleAnnotations, c.invisibleAnnotations)) {
			if (a.desc.equals(MIXIN)) mixin = a;
		}
		if (mixin != null) readMixin(id, library, c, mixin, clientCode, serverCode);
		if (!library || id.equals(CORE)) return;

		references(id, c, clientCode ? CLIENT : serverCode ? SERVER : null);
		for (MethodNode m : c.methods) {
			accessRules(id, c, m);
		}
	}

	@SuppressWarnings("unchecked")
	private void readMixin(String id, boolean library, ClassNode c, AnnotationNode mixin, boolean clientCode, boolean serverCode) {
		List<String> classes = mixinTargets(mixin);
		for (String target : classes) {
			if (library && !id.equals(CORE) && !clientCode && CLIENT.equals(game.sideOfClass(target))) fail(id, "a common mixin targets a client-only class", c.name + " -> " + target);
			if (library && !id.equals(CORE) && !serverCode && SERVER.equals(game.sideOfClass(target))) fail(id, "a common mixin targets a server-only class", c.name + " -> " + target);
		}
		for (MethodNode m : c.methods) {
			for (AnnotationNode a : annotations(m.visibleAnnotations, m.invisibleAnnotations)) {
				if (!INJECTORS.contains(a.desc)) continue;
				if (a.desc.equals(OVERWRITE) && library) fail(id, "no @Overwrite", c.name + "." + m.name);
				List<String> methods = new ArrayList<>();
				Object named = value(a, "method");
				if (named instanceof List) {
					for (Object n : (List<Object>) named) methods.add((String) n);
				} else if (a.desc.equals(OVERWRITE)) {
					methods.add(m.name);
				}
				for (String target : classes) {
					for (String method : methods) {
						String key = target + "." + shortName(method);
						byMethod.computeIfAbsent(key, k -> new TreeSet<>()).add(id);
						targets.computeIfAbsent(key, k -> new ArrayList<>()).add(id + " " + a.desc.substring(a.desc.lastIndexOf('/') + 1, a.desc.length() - 1) + " " + c.name.substring(c.name.lastIndexOf('/') + 1) + "." + m.name);
						if (a.desc.equals(REDIRECT)) redirects.computeIfAbsent(target + "." + method + " at " + site(a), k -> new TreeSet<>()).add(id + " " + c.name + "." + m.name);
					}
				}
			}
		}
	}

	/** What a method's name is, without its descriptor, so overloads count as one when in doubt. */
	private static String shortName(String method) {
		int paren = method.indexOf('(');
		return paren < 0 ? method : method.substring(0, paren);
	}

	private static String site(AnnotationNode redirect) {
		Object at = value(redirect, "at");
		if (at instanceof List && !((List<?>) at).isEmpty()) at = ((List<?>) at).get(0);
		if (!(at instanceof AnnotationNode)) return String.valueOf(at);
		AnnotationNode node = (AnnotationNode) at;
		return value(node, "value") + " " + value(node, "target") + " " + value(node, "ordinal");
	}

	/**
	 * What code reaches against where it runs: {@code classSide} CLIENT, SERVER or null for both. A method marked {@code @Environment} is on that side whatever its class is, as Fabric
	 * strips it from the other.
	 */
	private void references(String id, ClassNode c, String classSide) {
		for (MethodNode m : c.methods) {
			if (m.instructions == null) continue;
			String marked = Game.side(m.visibleAnnotations, m.invisibleAnnotations);
			String side = marked != null ? marked : classSide;
			for (AbstractInsnNode i = m.instructions.getFirst(); i != null; i = i.getNext()) {
				if (i instanceof MethodInsnNode) {
					MethodInsnNode n = (MethodInsnNode) i;
					reference(id, c, side, n.owner, n.name + n.desc);
				} else if (i instanceof FieldInsnNode) {
					FieldInsnNode n = (FieldInsnNode) i;
					reference(id, c, side, n.owner, n.name + ":" + n.desc);
				} else if (i instanceof TypeInsnNode) {
					classReference(id, c, side, Type.getObjectType(((TypeInsnNode) i).desc).getInternalName());
				} else if (i instanceof LdcInsnNode && ((LdcInsnNode) i).cst instanceof Type) {
					Type t = (Type) ((LdcInsnNode) i).cst;
					if (t.getSort() == Type.OBJECT) classReference(id, c, side, t.getInternalName());
				} else if (i instanceof InvokeDynamicInsnNode) {
					for (Object arg : ((InvokeDynamicInsnNode) i).bsmArgs) {
						if (arg instanceof Handle) {
							Handle h = (Handle) arg;
							reference(id, c, side, h.getOwner(), h.getName() + (h.getTag() <= Opcodes.H_PUTSTATIC ? ":" : "") + h.getDesc());
						}
					}
				}
			}
		}
	}

	private static String codeName(String side) {
		return side == null ? "common code" : side.equals(CLIENT) ? "client code" : "server code";
	}

	private void classReference(String id, ClassNode c, String code, String name) {
		if (name.startsWith("[")) return;
		if (name.startsWith("org/lwjgl/")) {
			if (!CLIENT.equals(code)) fail(id, codeName(code) + " reaches LWJGL, which a server hasn't got", c.name + " -> " + name);
			return;
		}
		if (TEST_CLIENT_CLASSES.stream().anyMatch(name::startsWith)) {
			if (!CLIENT.equals(code)) fail(id, codeName(code) + " reaches the client only", c.name + " -> " + name);
			return;
		}
		if (!name.startsWith("net/minecraft/")) return;
		sideRule(id, c, code, game.sideOfClass(name), name);
	}

	private void sideRule(String id, ClassNode c, String code, String side, String what) {
		if (side == null) fail(id, codeName(code) + " names something the game hasn't got", c.name + " -> " + what);
		else if (side.equals(CLIENT) && !CLIENT.equals(code)) fail(id, codeName(code) + " reaches the client only", c.name + " -> " + what);
		else if (side.equals(SERVER) && !SERVER.equals(code)) fail(id, codeName(code) + " reaches the server only", c.name + " -> " + what);
	}

	private void reference(String id, ClassNode c, String code, String owner, String member) {
		if (owner.startsWith("[")) return;
		if (!owner.startsWith("net/minecraft/") && !owner.startsWith("org/lwjgl/") && TEST_CLIENT_CLASSES.stream().noneMatch(owner::startsWith)) return;
		classReference(id, c, code, owner);
		if (!owner.startsWith("net/minecraft/")) return;
		sideRule(id, c, code, game.sideOf(owner, member), owner + "." + member);
	}

	/** Rules on how a mod reads the game, for client code too. */
	private void accessRules(String id, ClassNode c, MethodNode m) {
		if (m.instructions == null) return;
		for (AbstractInsnNode i = m.instructions.getFirst(); i != null; i = i.getNext()) {
			if (i.getOpcode() == Opcodes.GETFIELD) {
				FieldInsnNode f = (FieldInsnNode) i;
				if (f.owner.equals("net/minecraft/world/chunk/Chunk") && (f.name.equals("blocks") || f.name.equals("meta"))) {
					fail(id, "reads Chunk." + f.name + " directly, which holds a stand-in: use getBlockId or getBlockMeta", c.name + "." + m.name);
				}
			}
			if ((i.getOpcode() == Opcodes.IF_ICMPEQ || i.getOpcode() == Opcodes.IF_ICMPNE) && i instanceof JumpInsnNode && comparesItemIds(i)) {
				fail(id, "compares stacks by hand: use Stacks.canMerge", c.name + "." + m.name);
			}
		}
	}

	/** {@code a.itemId == b.itemId}: both of the jump's operands end in that field. */
	private static boolean comparesItemIds(AbstractInsnNode jump) {
		int found = 0, steps = 0;
		for (AbstractInsnNode i = jump.getPrevious(); i != null && steps < 8; i = i.getPrevious()) {
			if (i.getOpcode() < 0) continue;
			steps++;
			if (i instanceof JumpInsnNode) break;
			if (i.getOpcode() == Opcodes.GETFIELD && ((FieldInsnNode) i).owner.equals("net/minecraft/item/ItemStack") && ((FieldInsnNode) i).name.equals("itemId")) found++;
		}
		return found >= 2;
	}

	/** Every read done: the rules across mods, then the report. */
	List<String> finish(Map<String, String> accepted, StringBuilder report) {
		report.append("Every mixin target, by method: mod, kind, handler\n");
		for (Map.Entry<String, List<String>> e : targets.entrySet()) {
			report.append(e.getKey()).append('\n');
			for (String line : e.getValue()) report.append("    ").append(line).append('\n');
		}
		for (Map.Entry<String, Set<String>> e : redirects.entrySet()) {
			if (e.getValue().size() > 1) failures.add("two @Redirects on one site, which can never both apply: " + e.getKey() + ": " + e.getValue());
		}
		Set<String> used = new HashSet<>();
		for (Map.Entry<String, Set<String>> e : byMethod.entrySet()) {
			Set<String> mods = new TreeSet<>(e.getValue());
			mods.remove(CORE);
			if (mods.size() < 2 || mods.stream().noneMatch(libraryMods::contains)) continue;
			String key = e.getKey() + " " + String.join(" ", mods);
			if (accepted.containsKey(key)) {
				used.add(key);
				report.append("accepted: ").append(key).append(": ").append(accepted.get(key)).append('\n');
			} else {
				failures.add(String.join(" and ", mods) + ": two mods on one method, until someone decides (a hook, or a line in harness/library-accepted.txt): " + key);
			}
		}
		for (String key : accepted.keySet()) {
			if (!used.contains(key)) failures.add("harness/library-accepted.txt: nothing conflicts here any more: " + key);
		}
		report.append("Classes read: ").append(classesRead).append(", mixin targets: ").append(targets.size()).append(", failures: ").append(failures.size()).append('\n');
		for (String f : failures) report.append("FAIL ").append(f).append('\n');
		return new ArrayList<>(failures);
	}
}
