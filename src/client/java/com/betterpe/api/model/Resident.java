package com.betterpe.api.model;

/** One entry of GET /resident. Field names match the API JSON exactly. */
public class Resident {
	public String name;
	public String town;
	public String nation;
	public String townRanks;
	public String nationRanks;
	public long lastOnline;
	public long registered;
	public String title;
	public String surname;
	public long joinedTownAt;
	public String friends;
	public String uuid;
	public double balance;
	public boolean online;

	public boolean hasNation() {
		return nation != null && !nation.isEmpty();
	}

	public boolean hasTown() {
		return town != null && !town.isEmpty();
	}
}
