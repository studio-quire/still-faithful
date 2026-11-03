package org.studioquire.oldfaithful.speed;

/** A WorldRegion that caches light levels for the rebuild it was made for (WorldRegionMixin). */
public interface LightCached {
	void oldfaithful$cacheLight(LightCache cache);
}
