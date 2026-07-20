package com.betterpe.dynmap;

/** A simple point marker (port or goods spawn) from dynmap. */
public class MapMarker {
	public final String label;
	public final double x;
	public final double z;

	public MapMarker(String label, double x, double z) {
		this.label = label;
		this.x = x;
		this.z = z;
	}

	public double distanceSqTo(double px, double pz) {
		double dx = x - px;
		double dz = z - pz;
		return dx * dx + dz * dz;
	}
}
