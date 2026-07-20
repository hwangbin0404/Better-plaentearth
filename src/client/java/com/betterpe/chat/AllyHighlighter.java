package com.betterpe.chat;

import com.betterpe.config.Config;
import com.betterpe.config.ConfigManager;
import net.minecraft.text.MutableText;
import net.minecraft.text.Style;
import net.minecraft.text.Text;
import net.minecraft.text.TextColor;
import net.minecraft.util.Formatting;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Feature 5 rendering: given an incoming chat message, recolor ONLY the speaker's
 * nickname when they are an own-nation (&a) or ally (&b) member. The rest of the
 * line — timestamps, tags like [전체]/[PRO], and the message body — keeps its
 * original styling because we rebuild the message run-by-run.
 */
public final class AllyHighlighter {

	// Nickname sits right before ": " — capture a Minecraft-name-shaped token.
	private static final Pattern SPEAKER = Pattern.compile("(?:^|\\s)([A-Za-z0-9_]{2,16}):(?:\\s|$)");

	private AllyHighlighter() {
	}

	/**
	 * Returns a possibly-recolored copy of {@code message}, or the original if no change.
	 * Never throws: this runs on every incoming server message, so any unexpected input
	 * (an exotic Text shape we didn't anticipate) must fall back to the original message
	 * rather than risk that message silently vanishing from chat.
	 */
	public static Text highlight(Text message) {
		Config cfg = ConfigManager.get();
		if (!cfg.modEnabled || !cfg.chatHighlightEnabled || !AllyManager.isLoaded()) {
			return message;
		}
		try {
			Text result = tryHighlight(message, cfg);
			return result != null ? result : message;
		} catch (Exception e) {
			return message;
		}
	}

	private static Text tryHighlight(Text message, Config cfg) {
		// Flatten into styled runs so we can preserve everyone else's formatting.
		List<Run> runs = new ArrayList<>();
		message.visit((style, str) -> {
			runs.add(new Run(str, style));
			return Optional.empty();
		}, Style.EMPTY);

		StringBuilder sb = new StringBuilder();
		for (Run r : runs) {
			sb.append(r.text);
		}
		String full = sb.toString();

		int[] span = findSpeakerSpan(full);
		if (span == null) {
			return message;
		}

		String name = full.substring(span[0], span[1]);
		Formatting color;
		if (AllyManager.isNationMember(name)) {
			color = parseColor(cfg.nationColor, Formatting.GREEN);
		} else if (AllyManager.isAllyMember(name)) {
			color = parseColor(cfg.allyColor, Formatting.AQUA);
		} else {
			return message;
		}

		return rebuild(runs, span[0], span[1], color);
	}

	/** Locate the first speaker nickname that is a tracked member. Returns [start,end) or null. */
	private static int[] findSpeakerSpan(String full) {
		Matcher m = SPEAKER.matcher(full);
		while (m.find()) {
			String candidate = m.group(1);
			if (AllyManager.isNationMember(candidate) || AllyManager.isAllyMember(candidate)) {
				return new int[]{m.start(1), m.end(1)};
			}
		}
		return null;
	}

	/** Rebuild the message, overriding color only for chars in [start,end). */
	private static Text rebuild(List<Run> runs, int start, int end, Formatting color) {
		MutableText result = Text.empty();
		int pos = 0;
		for (Run r : runs) {
			int runStart = pos;
			int runEnd = pos + r.text.length();
			pos = runEnd;

			// Split this run against the highlight span.
			int i = runStart;
			while (i < runEnd) {
				int segEnd = runEnd;
				boolean inSpan = i >= start && i < end;
				if (inSpan) {
					segEnd = Math.min(runEnd, end);
				} else if (i < start) {
					segEnd = Math.min(runEnd, start);
				}
				String seg = r.text.substring(i - runStart, segEnd - runStart);
				if (!seg.isEmpty()) {
					Style style = inSpan
							? r.style.withColor(TextColor.fromFormatting(color))
							: r.style;
					result.append(Text.literal(seg).setStyle(style));
				}
				i = segEnd;
			}
		}
		return result;
	}

	private static Formatting parseColor(String code, Formatting fallback) {
		if (code != null && code.length() == 1) {
			Formatting f = Formatting.byCode(code.charAt(0));
			if (f != null && f.isColor()) {
				return f;
			}
		}
		return fallback;
	}

	private record Run(String text, Style style) {
	}
}
