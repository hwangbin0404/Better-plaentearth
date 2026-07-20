package com.betterpe.api.model;

import java.util.ArrayList;
import java.util.List;

/** One entry of GET /nation. Comma-separated string fields get split by helpers. */
public class Nation {
	public String name;
	public int memberCount;
	public String capital;
	public String leader;
	public String towns;
	public String allies;
	public String enemies;
	public long registered;
	public String nationBoard;
	public String uuid;
	public double balance;

	public List<String> townList() {
		return splitCsv(towns);
	}

	public List<String> allyList() {
		return splitCsv(allies);
	}

	public List<String> enemyList() {
		return splitCsv(enemies);
	}

	private static List<String> splitCsv(String s) {
		List<String> out = new ArrayList<>();
		if (s == null || s.isBlank()) {
			return out;
		}
		for (String part : s.split(",")) {
			String t = part.trim();
			if (!t.isEmpty()) {
				out.add(t);
			}
		}
		return out;
	}
}
