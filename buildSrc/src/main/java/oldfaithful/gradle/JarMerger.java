package oldfaithful.gradle;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.function.Function;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.tree.AnnotationNode;
import org.objectweb.asm.tree.ClassNode;

/**
 * Merges the client and server jars (both already in intermediary names) into one compile-time jar. Classes and
 * members found on only one side are marked with Fabric's {@code @Environment}, so code reading the decompiled
 * source can see what exists where. Method bodies come from the client where both sides have one: the result is
 * for compiling against and reading, never for running.
 */
final class JarMerger {
	private static final String ENVIRONMENT = "Lnet/fabricmc/api/Environment;";
	private static final String ENV_TYPE = "Lnet/fabricmc/api/EnvType;";
	private static final FileTime EPOCH = FileTime.fromMillis(0);

	private JarMerger() {
	}

	static void merge(Path client, Path server, Path output) throws IOException {
		Map<String, byte[]> clientClasses = readClasses(client);
		Map<String, byte[]> serverClasses = readClasses(server);
		Set<String> names = new TreeSet<>(clientClasses.keySet());
		names.addAll(serverClasses.keySet());

		Files.createDirectories(output.getParent());
		try (OutputStream os = Files.newOutputStream(output); ZipOutputStream zip = new ZipOutputStream(os)) {
			for (String name : names) {
				byte[] c = clientClasses.get(name);
				byte[] s = serverClasses.get(name);
				ClassNode node;
				if (s == null) {
					node = read(c);
					mark(node, "CLIENT");
				} else if (c == null) {
					node = read(s);
					mark(node, "SERVER");
				} else {
					node = mergeClass(read(c), read(s));
				}

				ClassWriter writer = new ClassWriter(0);
				node.accept(writer);
				ZipEntry entry = new ZipEntry(name);
				entry.setLastModifiedTime(EPOCH);
				zip.putNextEntry(entry);
				zip.write(writer.toByteArray());
				zip.closeEntry();
			}
		}
	}

	private static ClassNode mergeClass(ClassNode client, ClassNode server) {
		client.fields = mergeMembers(client.fields, server.fields, f -> f.name + f.desc,
				(f, side) -> f.invisibleAnnotations = withSide(f.invisibleAnnotations, side));
		client.methods = mergeMembers(client.methods, server.methods, m -> m.name + m.desc,
				(m, side) -> m.invisibleAnnotations = withSide(m.invisibleAnnotations, side));

		Set<String> interfaces = new LinkedHashSet<>(client.interfaces);
		interfaces.addAll(server.interfaces);
		client.interfaces = new ArrayList<>(interfaces);
		return client;
	}

	private interface Marker<T> {
		void mark(T member, String side);
	}

	private static <T> List<T> mergeMembers(List<T> client, List<T> server, Function<T, String> key, Marker<T> marker) {
		Map<String, T> serverByKey = new TreeMap<>();
		for (T member : server) serverByKey.put(key.apply(member), member);

		List<T> merged = new ArrayList<>();
		for (T member : client) {
			if (serverByKey.remove(key.apply(member)) == null) marker.mark(member, "CLIENT");
			merged.add(member);
		}
		for (T member : server) {
			if (serverByKey.containsKey(key.apply(member))) {
				marker.mark(member, "SERVER");
				merged.add(member);
			}
		}
		return merged;
	}

	private static void mark(ClassNode node, String side) {
		node.invisibleAnnotations = withSide(node.invisibleAnnotations, side);
	}

	private static List<AnnotationNode> withSide(List<AnnotationNode> annotations, String side) {
		List<AnnotationNode> list = annotations == null ? new ArrayList<>() : annotations;
		AnnotationNode env = new AnnotationNode(ENVIRONMENT);
		env.visitEnum("value", ENV_TYPE, side);
		list.add(env);
		return list;
	}

	private static ClassNode read(byte[] bytes) {
		ClassNode node = new ClassNode();
		new ClassReader(bytes).accept(node, 0);
		return node;
	}

	private static Map<String, byte[]> readClasses(Path jar) throws IOException {
		Map<String, byte[]> classes = new TreeMap<>();
		try (InputStream is = Files.newInputStream(jar); ZipInputStream zip = new ZipInputStream(is)) {
			ZipEntry entry;
			while ((entry = zip.getNextEntry()) != null) {
				if (!entry.isDirectory() && entry.getName().endsWith(".class")) {
					classes.put(entry.getName(), zip.readAllBytes());
				}
			}
		}
		return classes;
	}
}
