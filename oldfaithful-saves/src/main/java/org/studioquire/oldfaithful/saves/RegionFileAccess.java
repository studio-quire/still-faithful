package org.studioquire.oldfaithful.saves;

/** Implemented by RegionFile through our mixin: its crash-safe writer, or null if the file didn't open. */
public interface RegionFileAccess {
	RegionWrites oldfaithful$writes();
}
