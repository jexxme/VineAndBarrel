package jexxme.vineandbarrel.tipsy;

import java.util.Random;

/**
 * Slurred chat (Tipsy level 4+). A pure function: the same message and seed always give the same result, so it can be
 * tested without a world. Gentle on purpose: the message stays readable, it just sounds a bit tipsy
 * ("I love thish shooong *hic*").
 */
public final class TipsySlur
{
	/** Vanilla's chat length limit; a slur that would go past it falls back to a plain trailing *hic*. */
	public static final int MAX_LENGTH = 256;
	public static final String HIC = "*hic*";

	private static final double S_TO_SH = 0.5;
	private static final double STRETCH_WORD = 0.3;
	private static final double HIC_AFTER_WORD = 0.12;

	private TipsySlur() {
	}

	public static String slur(String message, long seed) {
		if (message.isBlank()) {
			return message;
		}
		Random random = new Random(seed);
		StringBuilder out = new StringBuilder(message.length() + 16);
		int i = 0;
		while (i < message.length()) {
			int end = i;
			if (Character.isWhitespace(message.charAt(i))) {
				while (end < message.length() && Character.isWhitespace(message.charAt(end))) end++;
				out.append(message, i, end);
			} else {
				while (end < message.length() && !Character.isWhitespace(message.charAt(end))) end++;
				String word = message.substring(i, end);
				if (isProtected(word)) {
					out.append(word);
				} else {
					out.append(slurWord(word, random));
					if (end < message.length() && random.nextDouble() < HIC_AFTER_WORD) {
						out.append(' ').append(HIC);
					}
				}
			}
			i = end;
		}
		String result = out.toString();
		if (result.equals(message)) {
			result = message + " " + HIC;
		}
		if (result.length() > MAX_LENGTH) {
			String fallback = message + " " + HIC;
			return fallback.length() > MAX_LENGTH ? message : fallback;
		}
		return result;
	}

	/** Links, commands and mentions stay exactly as typed. */
	private static boolean isProtected(String word) {
		return word.contains("://") || word.startsWith("/") || word.startsWith("@") || word.startsWith("www.");
	}

	private static String slurWord(String word, Random random) {
		StringBuilder out = new StringBuilder(word.length() + 4);
		for (int i = 0; i < word.length(); i++) {
			char c = word.charAt(i);
			boolean nextIsH = i + 1 < word.length() && Character.toLowerCase(word.charAt(i + 1)) == 'h';
			if ((c == 's' || c == 'S') && !nextIsH && random.nextDouble() < S_TO_SH) {
				out.append(c).append(Character.isUpperCase(c) && isAllCaps(word) ? 'H' : 'h');
			} else {
				out.append(c);
			}
		}
		if (random.nextDouble() < STRETCH_WORD) {
			int vowel = pickVowel(out, random);
			if (vowel >= 0) {
				char v = out.charAt(vowel);
				char extra = isAllCaps(word) ? v : Character.toLowerCase(v);
				out.insert(vowel + 1, new char[]{extra, extra});
			}
		}
		return out.toString();
	}

	private static int pickVowel(CharSequence word, Random random) {
		int count = 0;
		for (int i = 0; i < word.length(); i++) {
			if (isVowel(word.charAt(i))) count++;
		}
		if (count == 0) {
			return -1;
		}
		int pick = random.nextInt(count);
		for (int i = 0; i < word.length(); i++) {
			if (isVowel(word.charAt(i)) && pick-- == 0) return i;
		}
		return -1;
	}

	private static boolean isVowel(char c) {
		return "aeiouAEIOU".indexOf(c) >= 0;
	}

	private static boolean isAllCaps(String word) {
		boolean anyLetter = false;
		for (int i = 0; i < word.length(); i++) {
			char c = word.charAt(i);
			if (Character.isLetter(c)) {
				anyLetter = true;
				if (!Character.isUpperCase(c)) return false;
			}
		}
		return anyLetter && word.length() > 1;
	}
}
