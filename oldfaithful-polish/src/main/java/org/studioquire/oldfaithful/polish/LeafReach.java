package org.studioquire.oldfaithful.polish;

/**
 * How far leaves stay from wood (Leaf Reach). Beta's {@code LeavesBlock.onTick} keeps a leaf marked to look for a log
 * (data bit 8) only if a log lies within 4 steps of it through leaves (a flood over the cube 4 each way round it, 4 steps
 * long), and a broken log marks the leaves in the cube 4 each way round it. A big oak's leaf clusters are up to five
 * layers tall with wood only under their middle, so their upper corners lie 5 and 6 steps from it. Here both are 6, as later
 * versions keep leaves (1.14 gave leaves a distance to the nearest log, up to 7, where 7 decays). Beta's flood array is
 * 32 wide, room for 6 each way and the cells beside them it reads.
 */
public final class LeafReach {
	public static final int BETA = 4, REACH = 6;

	/** For the self-test only: beta's 4, so the same trees are counted both ways in one run. */
	public static volatile boolean betaForTest;

	private LeafReach() {
	}

	public static int reach() {
		return betaForTest ? BETA : REACH;
	}
}
