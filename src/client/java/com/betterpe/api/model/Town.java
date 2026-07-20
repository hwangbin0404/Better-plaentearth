package com.betterpe.api.model;

import java.util.ArrayList;
import java.util.List;

/** One entry of GET /town. */
public class Town {
	public String name;
	public int memberCount;
	public String mayor;
	public String nation;
	public String residents;
	public String townBoard;
	public String trustedResidents;
	public String outlaws;
	public long registered;
	public String spawn;
	public String founder;
	public long joinedNationAt;
	public int claimSize;
	public String uuid;
	public double balance;
	public int onlineMemberCount;
	public String onlineMembers;

	public List<String> residentList() {
		List<String> out = new ArrayList<>();
		if (residents == null || residents.isBlank()) {
			return out;
		}
		for (String part : residents.split(",")) {
			String t = part.trim();
			if (!t.isEmpty()) {
				out.add(t);
			}
		}
		return out;
	}
}
