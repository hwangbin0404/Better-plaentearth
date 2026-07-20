package com.betterpe.dynmap;

/**
 * One town claim polygon from the dynmap towny.markerset.
 * x[] / z[] are world coordinates of the polygon vertices.
 */
public class TownArea {
	public final String town;
	public final String nation;
	public final double[] x;
	public final double[] z;
	// Precomputed centroid for nearest-town distance while in the wild.
	public final double centerX;
	public final double centerZ;

	public TownArea(String town, String nation, double[] x, double[] z) {
		this.town = town;
		this.nation = nation;
		this.x = x;
		this.z = z;
		double sx = 0, sz = 0;
		for (int i = 0; i < x.length; i++) {
			sx += x[i];
			sz += z[i];
		}
		this.centerX = x.length > 0 ? sx / x.length : 0;
		this.centerZ = z.length > 0 ? sz / z.length : 0;
	}

	/** Ray-casting point-in-polygon test on the (x, z) plane. */
	public boolean contains(double px, double pz) {
		boolean inside = false;
		int n = x.length;
		for (int i = 0, j = n - 1; i < n; j = i++) {
			boolean crosses = (z[i] > pz) != (z[j] > pz);
			if (crosses) {
				double atX = (x[j] - x[i]) * (pz - z[i]) / (z[j] - z[i]) + x[i];
				if (px < atX) {
					inside = !inside;
				}
			}
		}
		return inside;
	}

	/**
	 * Squared distance from (px, pz) to this claim: 0 if the point is inside the claim,
	 * otherwise the squared distance to the nearest point on the claim's edge. Used so
	 * "how close am I to this town" is measured against the actual claim boundary
	 * instead of the polygon centroid.
	 */
	public double distanceSqTo(double px, double pz) {
		if (contains(px, pz)) {
			return 0;
		}
		double best = Double.MAX_VALUE;
		int n = x.length;
		for (int i = 0, j = n - 1; i < n; j = i++) {
			double d = pointToSegmentDistSq(px, pz, x[j], z[j], x[i], z[i]);
			if (d < best) {
				best = d;
			}
		}
		return best;
	}

	private static double pointToSegmentDistSq(double px, double pz, double ax, double az, double bx, double bz) {
		double abx = bx - ax;
		double abz = bz - az;
		double apx = px - ax;
		double apz = pz - az;
		double abLen2 = abx * abx + abz * abz;
		double t = abLen2 > 0 ? Math.max(0, Math.min(1, (apx * abx + apz * abz) / abLen2)) : 0;
		double cx = ax + t * abx;
		double cz = az + t * abz;
		double dx = px - cx;
		double dz = pz - cz;
		return dx * dx + dz * dz;
	}

	/** "마을명(국가명)" or just "마을명" when nation is empty. */
	public String display() {
		if (nation == null || nation.isBlank()) {
			return town;
		}
		return town + "(" + nation + ")";
	}
}
