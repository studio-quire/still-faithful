package org.studioquire.oldfaithful.selftest;

/** A render chunk (ChunkBuilder, through ChunkBuilderBenchMixin) that knows whether it has been built yet. */
public interface BenchChunk {
	boolean oldfaithful$built();
}
