package oldfaithful.gradle;

import org.gradle.api.file.RegularFileProperty;

/** The game files the root project prepares, for mod and harness projects to use. */
public abstract class MinecraftFiles {
	public abstract RegularFileProperty getClientJar();

	public abstract RegularFileProperty getServerJar();

	/** Client and server merged, named: what mods compile against. */
	public abstract RegularFileProperty getNamedJar();

	/** Client and server merged, intermediary: the classpath for remapping mods. */
	public abstract RegularFileProperty getMergedIntermediaryJar();

	public abstract RegularFileProperty getIntermediaryMappings();

	public abstract RegularFileProperty getNamedMappings();
}
