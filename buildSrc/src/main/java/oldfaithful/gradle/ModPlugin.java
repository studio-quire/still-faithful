package oldfaithful.gradle;

import java.util.Map;

import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.artifacts.dsl.DependencyHandler;
import org.gradle.api.plugins.JavaPlugin;
import org.gradle.api.plugins.JavaPluginExtension;
import org.gradle.api.tasks.SourceSet;
import org.gradle.api.tasks.TaskProvider;
import org.gradle.api.tasks.compile.JavaCompile;
import org.gradle.api.tasks.testing.Test;
import org.gradle.jvm.tasks.Jar;
import org.gradle.language.base.plugins.LifecycleBasePlugin;
import org.gradle.language.jvm.tasks.ProcessResources;

import static oldfaithful.gradle.MinecraftPlugin.prop;

/**
 * Applied to each mod: compiles against the named game jar, Fabric Loader, Mixin and MixinExtras (all provided by
 * the loader at runtime, so compile-only), targets Java 8 bytecode, and remaps the jar to intermediary names.
 * `build/libs/<mod>-<version>.jar` is the installable mod; the `-dev` jar is the named one.
 */
public class ModPlugin implements Plugin<Project> {
	/**
	 * An edition's property: the root's gradle.properties, or (Still Faithful's, kept private with its folder) the mod
	 * project's own.
	 */
	private static String editionProp(Project project, String name) {
		Object own = project.findProperty(name);
		return own != null ? own.toString() : prop(project, name);
	}

	@Override
	public void apply(Project project) {
		project.getPluginManager().apply(JavaPlugin.class);
		Project root = project.getRootProject();
		MinecraftFiles mc = root.getExtensions().getByType(MinecraftFiles.class);

		// The Pure edition's mods are Pure Faithful's publicly: the jar, mod id, licence file and
		// icon path carry that name; the project folders, packages and tasks keep the project's internal "oldfaithful".
		// A mod of the Fixed edition only (`oldfaithful_edition=fixed` in its gradle.properties) is Old Faithful's, so
		// its public name stays oldfaithful-*, with Old Faithful's icon, version and links (docs/release.md > Names).
		// One of Still Faithful's (`oldfaithful_edition=still`) is stillfaithful-*, likewise with that edition's own.
		Object edition = project.findProperty("oldfaithful_edition");
		boolean fixedEdition = "fixed".equals(edition);
		boolean stillEdition = "still".equals(edition);
		// The Faithful Library's (`oldfaithful_edition=library`, docs/library-authoring.md): faithful-*, or the mod's own folder name.
		boolean library = "library".equals(edition);
		String publicName = fixedEdition ? "oldfaithful" : stillEdition ? "stillfaithful" : library ? "faithful" : "purefaithful";
		String modId = project.getName().replaceFirst("^oldfaithful-", publicName + "-");
		String props = fixedEdition ? "fixed_" : stillEdition ? "still_" : library ? "library_" : "";

		project.setVersion(editionProp(project, fixedEdition ? "fixed_edition_version" : stillEdition ? "still_edition_version"
				: library ? "library_version" : "oldfaithful_version"));

		DependencyHandler deps = project.getDependencies();
		deps.add("compileOnly", project.files(mc.getNamedJar()));
		deps.add("compileOnly", "net.fabricmc:fabric-loader:" + prop(project, "fabric_loader_version"));
		deps.add("compileOnly", "net.fabricmc:sponge-mixin:" + prop(project, "mixin_version"));
		deps.add("compileOnly", "io.github.llamalad7:mixinextras-fabric:" + prop(project, "mixinextras_version"));
		deps.add("testImplementation", deps.platform("org.junit:junit-bom:" + prop(project, "junit_version")));
		deps.add("testImplementation", "org.junit.jupiter:junit-jupiter");
		deps.add("testRuntimeOnly", "org.junit.platform:junit-platform-launcher");

		JavaPluginExtension java = project.getExtensions().getByType(JavaPluginExtension.class);
		SourceSet main = java.getSourceSets().getByName(SourceSet.MAIN_SOURCE_SET_NAME);

		// Java 8 bytecode, so the mods also run in existing instances on old JREs.
		project.getTasks().named(main.getCompileJavaTaskName(), JavaCompile.class, t -> {
			t.getOptions().getRelease().set(8);
			t.getOptions().setEncoding("UTF-8");
			t.getOptions().getCompilerArgs().add("-Xlint:-options");
		});
		project.getTasks().named("compileTestJava", JavaCompile.class, t -> {
			t.getOptions().getRelease().set(21);
			t.getOptions().setEncoding("UTF-8");
		});
		project.getTasks().withType(Test.class).configureEach(t -> {
			t.useJUnitPlatform();
			t.setMaxParallelForks(1);
		});

		// fabric.mod.json gets the version and the public links; every jar carries our licence and the icon.
		project.getTasks().named(main.getProcessResourcesTaskName(), ProcessResources.class, t -> {
			Map<String, String> tokens = Map.of("version", project.getVersion().toString(),
					"repo", editionProp(project, props + "release_repo"), "homepage", editionProp(project, props + "release_homepage"));
			t.getInputs().property("tokens", tokens);
			t.filesMatching("fabric.mod.json", f -> f.expand(tokens));
			t.from(root.file("LICENSE"), c -> c.rename(n -> "LICENSE_" + modId));
			t.from(root.file("prism/release/icons/final/" + publicName + "-128.png"), c -> c.into("assets/" + modId).rename(n -> "icon.png"));
		});

		TaskProvider<Jar> jar = project.getTasks().named(JavaPlugin.JAR_TASK_NAME, Jar.class,
				t -> t.getArchiveClassifier().set("dev"));

		TaskProvider<RemapJarTask> remapJar = project.getTasks().register("remapJar", RemapJarTask.class, t -> {
			t.setGroup(LifecycleBasePlugin.BUILD_GROUP);
			t.setDescription("Remaps the mod to intermediary names: the jar players install.");
			t.getInput().set(jar.flatMap(Jar::getArchiveFile));
			t.getNamedMappings().set(mc.getNamedMappings());
			t.getClasspath().from(main.getCompileClasspath());
			t.getOutput().set(project.getLayout().getBuildDirectory()
					.file("libs/" + modId + "-" + project.getVersion() + ".jar"));
		});
		project.getTasks().named(LifecycleBasePlugin.ASSEMBLE_TASK_NAME, t -> t.dependsOn(remapJar));

		// A library mod's own test (docs/library-authoring.md > Testing), a mod of its own that's never shipped: `harness`'s librarySelftest runs it.
		if (library && project.file("src/selftest").isDirectory()) selftest(project, java, main, mc, modId);
	}

	private static void selftest(Project project, JavaPluginExtension java, SourceSet main, MinecraftFiles mc, String modId) {
		SourceSet selftest = java.getSourceSets().create("selftest");
		project.getConfigurations().getByName(selftest.getCompileOnlyConfigurationName()).extendsFrom(project.getConfigurations().getByName("compileOnly"));
		DependencyHandler deps = project.getDependencies();
		deps.add(selftest.getCompileOnlyConfigurationName(), main.getOutput());
		deps.add(selftest.getCompileOnlyConfigurationName(), deps.project(Map.of("path", ":harness")));
		deps.add(selftest.getCompileOnlyConfigurationName(), deps.project(Map.of("path", ":library:harness-probe")));
		project.getTasks().named(selftest.getCompileJavaTaskName(), JavaCompile.class, t -> {
			t.getOptions().getRelease().set(8);
			t.getOptions().setEncoding("UTF-8");
			t.getOptions().getCompilerArgs().add("-Xlint:-options");
		});
		TaskProvider<Jar> jar = project.getTasks().register("selftestJar", Jar.class, t -> {
			t.from(selftest.getOutput());
			t.getArchiveClassifier().set("selftest-dev");
		});
		project.getTasks().register("selftestRemapJar", RemapJarTask.class, t -> {
			t.getInput().set(jar.flatMap(Jar::getArchiveFile));
			t.getNamedMappings().set(mc.getNamedMappings());
			t.getClasspath().from(selftest.getCompileClasspath());
			t.getOutput().set(project.getLayout().getBuildDirectory().file("libs/" + modId + "-selftest.jar"));
		});
	}
}
