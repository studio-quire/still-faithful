package oldfaithful.gradle.lwjgl;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/**
 * Checks that every org.lwjgl reference made by a set of jars (the game, mods) links against a LWJGL
 * implementation: each class exists, each method and field resolves with the right static-ness. A reference that
 * doesn't is a NoSuchMethodError or NoClassDefFoundError waiting for whoever first reaches that code.
 */
public final class LinkCheck {
	public static final String PREFIX = "org/lwjgl/";

	public record Problem(References.Ref ref, String why, Set<String> from) {
	}

	public record Result(String subject, int references, List<Problem> problems, List<References.Ref> mixinTargets) {
	}

	public static Map<String, Result> check(List<Path> library, Map<String, Path> subjects) throws IOException {
		ClassPool pool = ClassPool.of(library);
		Map<String, Result> results = new LinkedHashMap<>();
		for (Map.Entry<String, Path> s : subjects.entrySet()) {
			References refs = References.scan(s.getValue(), PREFIX);
			List<Problem> problems = new ArrayList<>();
			List<References.Ref> mixins = new ArrayList<>();
			for (Map.Entry<References.Ref, Set<String>> e : refs.all().entrySet()) {
				References.Ref r = e.getKey();
				String why = resolve(pool, r);
				if (r.kind() == References.Kind.ANNOTATION) mixins.add(r);
				if (why != null) problems.add(new Problem(r, why, e.getValue()));
			}
			results.put(s.getKey(), new Result(s.getKey(), refs.all().size(), problems, mixins));
		}
		return results;
	}

	/** Null if the reference links, else why not. */
	public static String resolve(ClassPool pool, References.Ref r) {
		if (pool.get(r.owner()) == null) return "no class " + r.owner();
		return switch (r.kind()) {
			case CLASS, ANNOTATION -> null;
			case STATIC_METHOD, METHOD -> {
				ClassPool.Member m = pool.resolveMethod(r.owner(), r.name(), r.desc());
				if (m == null) yield "no method";
				yield m.isStatic() == (r.kind() == References.Kind.STATIC_METHOD) ? null : "static-ness differs";
			}
			case STATIC_FIELD, FIELD -> {
				ClassPool.Member f = pool.resolveField(r.owner(), r.name(), r.desc());
				if (f == null) yield "no field";
				yield f.isStatic() == (r.kind() == References.Kind.STATIC_FIELD) ? null : "static-ness differs";
			}
		};
	}

	/** Prototype entry: library jars separated by the path separator, then name=jar subjects. Prints the report. */
	public static void main(String[] args) throws IOException {
		List<Path> lib = new ArrayList<>();
		for (String p : args[0].split(java.io.File.pathSeparator)) lib.add(Path.of(p));
		Map<String, Path> subjects = new TreeMap<>();
		for (int i = 1; i < args.length; i++) {
			String[] kv = args[i].split("=", 2);
			subjects.put(kv[0], Path.of(kv[1]));
		}
		boolean verbose = Boolean.getBoolean("verbose");
		if (Boolean.getBoolean("all")) {
			for (Map.Entry<String, Path> s : subjects.entrySet()) {
				for (References.Ref r : References.scan(s.getValue(), PREFIX).all().keySet()) System.out.println(s.getKey() + "\t" + r);
			}
			return;
		}
		for (Result r : check(lib, subjects).values()) {
			System.out.println(r.subject() + ": " + r.references() + " references, " + r.problems().size() + " unresolved"
					+ (r.mixinTargets().isEmpty() ? "" : ", annotations naming LWJGL: " + r.mixinTargets()));
			for (Problem p : r.problems()) {
				System.out.println("  " + p.ref() + "  [" + p.why() + "]" + (verbose ? "  from " + p.from() : ""));
			}
		}
	}
}
