package org.studioquire.oldfaithful.polish;

import java.nio.ByteBuffer;
import java.nio.FloatBuffer;

import net.minecraft.world.World;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GLContext;

/**
 * The chunks' vertex program for smooth dusk (DuskLight): each vertex's light from the pairs its mesh carries and this
 * frame's darkness, as beta's baking computes it, then everything else as the fixed pipeline does (ftransform, so depth
 * matches what's drawn without it: the block-breaking cracks; the texture matrix; the fog distance, radial where beta
 * asks for NV_fog_distance's radial mode). Fragments stay fixed-function: texturing, alpha test, fog, blending.
 */
public final class DuskShader {
	private DuskShader() {
	}

	static final int FINE = 6, LIGHT = 7;

	/** Set by GameRenderer.applyFog's call (mixin/dusk/GameRendererMixin): NV_fog_distance's mode is radial. */
	public static boolean radialFog;

	private static int program;
	private static int darknessAt, tableAt, radialAt;
	private static final FloatBuffer table = BufferUtils.createFloatBuffer(16);
	private static boolean attached;

	private static final String SOURCE = "#version 120\n"
			+ "attribute vec4 oldfaithful_fine;\n"
			+ "attribute vec4 oldfaithful_light;\n"
			+ "uniform float oldfaithful_darkness;\n"
			+ "uniform float oldfaithful_table[16];\n"
			+ "uniform bool oldfaithful_radial;\n"
			// One pair's luminance at a whole darkness; a pair with sky below block light stands for 1.
			+ "float lum(float pair, float d) {\n"
			+ "  float sky = floor(pair / 16.0);\n"
			+ "  float blk = pair - sky * 16.0;\n"
			+ "  if (sky < blk) return 1.0;\n"
			+ "  return oldfaithful_table[int(max(max(sky - d, blk), 0.0))];\n"
			+ "}\n"
			+ "float light(float d) {\n"
			+ "  vec4 p = oldfaithful_light;\n"
			+ "  return ((lum(p.x, d) + lum(p.y, d)) + (lum(p.z, d) + lum(p.w, d))) / 4.0;\n"
			+ "}\n"
			+ "void main() {\n"
			+ "  gl_Position = ftransform();\n"
			+ "  vec4 eye = gl_ModelViewMatrix * gl_Vertex;\n"
			+ "  gl_FogFragCoord = oldfaithful_radial ? length(eye.xyz) : abs(eye.z);\n"
			+ "  gl_TexCoord[0] = gl_TextureMatrix[0] * gl_MultiTexCoord0;\n"
			+ "  float whole = floor(oldfaithful_darkness);\n"
			+ "  float f = light(whole);\n"
			+ "  float part = oldfaithful_darkness - whole;\n"
			+ "  if (part > 0.0) f = mix(f, light(whole + 1.0), part);\n"
			// The tint and shade to 1/65280, times the light, cut to a byte as the tessellator cuts beta's colours.
			+ "  vec3 tint = (floor(gl_Color.rgb * 255.0 + 0.5) + (oldfaithful_fine.rgb + 0.5) / 256.0) / 255.0;\n"
			+ "  vec3 c = min(floor(tint * f * 255.0), 255.0) / 255.0;\n"
			+ "  gl_FrontColor = vec4(c, gl_Color.a);\n"
			+ "  gl_BackColor = gl_FrontColor;\n"
			+ "}\n";

	/** Compiles the program; false (logged) where the GL can't, and beta's lighting stays. */
	static boolean init() {
		try {
			if (!GLContext.getCapabilities().OpenGL20) {
				System.out.println("[stillfaithful-polish] Smooth dusk off: OpenGL 2.0 isn't available; dusk steps as in beta");
				return false;
			}
			while (GL11.glGetError() != 0) {
				// Not ours: the game logs its own at its next check.
			}
			int shader = GL20.glCreateShader(GL20.GL_VERTEX_SHADER);
			GL20.glShaderSource(shader, SOURCE);
			GL20.glCompileShader(shader);
			if (GL20.glGetShaderi(shader, GL20.GL_COMPILE_STATUS) == 0) {
				fail("compiling", GL20.glGetShaderInfoLog(shader, 4096));
				GL20.glDeleteShader(shader);
				return false;
			}
			int p = GL20.glCreateProgram();
			GL20.glAttachShader(p, shader);
			GL20.glBindAttribLocation(p, FINE, "oldfaithful_fine");
			GL20.glBindAttribLocation(p, LIGHT, "oldfaithful_light");
			GL20.glLinkProgram(p);
			GL20.glDeleteShader(shader);
			if (GL20.glGetProgrami(p, GL20.GL_LINK_STATUS) == 0) {
				fail("linking", GL20.glGetProgramInfoLog(p, 4096));
				GL20.glDeleteProgram(p);
				return false;
			}
			darknessAt = GL20.glGetUniformLocation(p, "oldfaithful_darkness");
			tableAt = GL20.glGetUniformLocation(p, "oldfaithful_table");
			radialAt = GL20.glGetUniformLocation(p, "oldfaithful_radial");
			int error = GL11.glGetError();
			if (error != 0 || darknessAt < 0 || tableAt < 0 || radialAt < 0) {
				fail("setting up", "GL error " + error + ", uniforms " + darknessAt + " " + tableAt + " " + radialAt);
				GL20.glDeleteProgram(p);
				return false;
			}
			program = p;
			return true;
		} catch (RuntimeException | LinkageError e) {
			fail("starting", String.valueOf(e));
			return false;
		}
	}

	private static void fail(String what, String log) {
		System.out.println("[stillfaithful-polish] Smooth dusk off: the light shader failed " + what + "; dusk steps as in beta: "
				+ (log == null ? "" : log.trim()));
	}

	/** Before the chunks' display lists are called. */
	public static void begin(World world) {
		GL20.glUseProgram(program);
		GL20.glUniform1f(darknessAt, DuskLight.darkness);
		GL20.glUniform1i(radialAt, radialFog ? 1 : 0);
		table.clear();
		table.put(world.dimension.lightLevelToLuminance, 0, 16).flip();
		GL20.glUniform1(tableAt, table);
	}

	public static void end() {
		GL20.glUseProgram(0);
	}

	/**
	 * In a chunk mesh's draw (Tessellator.draw, just before glDrawArrays): the pairs and the colours' fractions as two
	 * more arrays in the tessellator's 32-byte vertices, at the normal's offset (24, unused in chunks) and the spare one
	 * (28). The display list being compiled keeps them with the rest.
	 */
	public static void attach(ByteBuffer data, boolean vbo) {
		if (vbo) {
			GL20.glVertexAttribPointer(FINE, 4, GL11.GL_UNSIGNED_BYTE, false, 32, 24L);
			GL20.glVertexAttribPointer(LIGHT, 4, GL11.GL_UNSIGNED_BYTE, false, 32, 28L);
		} else {
			ByteBuffer fine = data.duplicate();
			fine.position(24);
			ByteBuffer light = data.duplicate();
			light.position(28);
			GL20.glVertexAttribPointer(FINE, 4, GL11.GL_UNSIGNED_BYTE, false, 32, fine);
			GL20.glVertexAttribPointer(LIGHT, 4, GL11.GL_UNSIGNED_BYTE, false, 32, light);
		}
		GL20.glEnableVertexAttribArray(FINE);
		GL20.glEnableVertexAttribArray(LIGHT);
		attached = true;
	}

	public static void detach() {
		if (!attached) return;
		attached = false;
		GL20.glDisableVertexAttribArray(FINE);
		GL20.glDisableVertexAttribArray(LIGHT);
	}
}
