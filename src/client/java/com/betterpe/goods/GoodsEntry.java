package com.betterpe.goods;

/** One special-good's respawn state, parsed from /goods chat output. */
public class GoodsEntry {
	public final String name;      // includes region, e.g. "닭고기(중국)"
	public long readyAtMs;         // epoch ms when it (re)spawns; <= now means available
	/** True once the server has confirmed the spawn via chat (respawn broadcast or /goods),
	 *  as opposed to our local countdown merely reaching zero. */
	public boolean confirmed;

	public GoodsEntry(String name, long readyAtMs) {
		this(name, readyAtMs, false);
	}

	public GoodsEntry(String name, long readyAtMs, boolean confirmed) {
		this.name = name;
		this.readyAtMs = readyAtMs;
		this.confirmed = confirmed;
	}

	public long remainingMs() {
		return Math.max(0, readyAtMs - System.currentTimeMillis());
	}

	public boolean isAvailable() {
		return remainingMs() == 0;
	}

	/** Normalized key for matching against dynmap labels and command args (spaces stripped). */
	public String key() {
		return normalize(name);
	}

	public static String normalize(String s) {
		return s == null ? "" : s.replace(" ", "").toLowerCase(java.util.Locale.ROOT);
	}

	/** Strips a trailing "(지역)" tag, e.g. "목재(바이칼호)" -> "목재". */
	public static String baseName(String s) {
		if (s == null) {
			return "";
		}
		int i = s.indexOf('(');
		return i >= 0 ? s.substring(0, i).trim() : s.trim();
	}

	/** e.g. "2분", "1시간 5분", "스폰대기중" (locally elapsed but not yet server-confirmed),
	 *  "스폰되었습니다" (server-confirmed). */
	public String remainingText() {
		long sec = remainingMs() / 1000;
		if (sec <= 0) {
			return confirmed ? "스폰되었습니다" : "스폰대기중";
		}
		long h = sec / 3600;
		long m = (sec % 3600) / 60;
		if (h > 0) {
			return h + "시간 " + m + "분";
		}
		if (m > 0) {
			return m + "분";
		}
		return sec + "초";
	}
}
