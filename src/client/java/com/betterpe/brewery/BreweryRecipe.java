package com.betterpe.brewery;

import java.util.Locale;

/**
 * One Brewery-plugin recipe, sourced from the server's brewing guide.
 * Time fields are the plugin's own units: fermentMinutes is real minutes on the
 * cauldron, agingYears is barrel-years where 1 year = 20 real minutes.
 */
public class BreweryRecipe {
	public final String name;
	public final String ingredients;
	public final String barrelType;  // "a" = any barrel wood
	public final int fermentMinutes;
	public final int agingYears;
	public final int distillCount;   // 0 = not a distilled spirit
	public final double price;
	public final int abv;

	public BreweryRecipe(String name, String ingredients, String barrelType, int fermentMinutes,
			int agingYears, int distillCount, double price, int abv) {
		this.name = name;
		this.ingredients = ingredients;
		this.barrelType = barrelType;
		this.fermentMinutes = fermentMinutes;
		this.agingYears = agingYears;
		this.distillCount = distillCount;
		this.price = price;
		this.abv = abv;
	}

	public long fermentMs() {
		return fermentMinutes * 60_000L;
	}

	/** Barrel aging time in real ms (1 barrel-year = 20 real minutes). */
	public long agingMs() {
		return agingYears * 20L * 60_000L;
	}

	public static String normalize(String s) {
		return s == null ? "" : s.replace(" ", "").toLowerCase(Locale.ROOT);
	}
}
