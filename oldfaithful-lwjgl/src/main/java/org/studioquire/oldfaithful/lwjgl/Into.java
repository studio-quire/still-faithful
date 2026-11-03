package org.studioquire.oldfaithful.lwjgl;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a patch class: at build time each of its public static methods gets a forwarding method of the same name
 * and signature in the named LWJGL 3 class (the build's LwjglBaker), so LWJGL 2 callers find it there.
 */
@Retention(RetentionPolicy.CLASS)
@Target(ElementType.TYPE)
public @interface Into {
	/** The class to add the methods to, e.g. "org.lwjgl.openal.AL". */
	String value();
}
