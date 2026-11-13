package jexxme.vineandbarrel.tipsy;

import java.util.Random;

/**
 * Drunk chat, worse with each level ({@link TipsyLevels.Chat#stage}; Jexxme: "chat should gradually become unusable, at 5
 * literally just jibberish"). A pure function: the same message, seed and stage always give the same result, so it can
 * be tested without a world. Links, commands and mentions are never touched.
 * <ol start="0">
 *   <li>as typed;</li>
 *   <li>now and then a *hic* at the end;</li>
 *   <li>slurred but easy to read: s to sh, a stretched vowel, the odd *hic* ("I love thish shooong *hic*");</li>
 *   <li>hard to read: letters doubled and swapped, spaces lost, more *hic*s;</li>
 *   <li>gibberish that keeps the shape of the message (word lengths, the odd real word), so friends see something was
 *   said.</li>
 * </ol>
 */
public final class TipsySlur
{
	/** Vanilla's chat length limit; a slur that would go past it is cut off there (it still goes out slurred). */
	public static final int MAX_LENGTH = 256;
	public static final String HIC = "*hic*";

	private static final double S_TO_SH = 0.5;
	private static final double STRETCH_WORD = 0.3;
	private static final double HIC_AFTER_WORD = 0.12;

	private TipsySlur() {
	}

	/** {@return the message as it comes out at this chat stage (0 as typed .. 4 gibberish)} */
	public static String slur(String message, long seed, int stage) {
		if (message.isBlank() || stage <= 0) {
			return message;
		}
		String result = switch (stage) {
			case 1 -> new Random(seed).nextDouble() < HIC_AT_ONE ? message + " " + HIC : message;
			case 2 -> slur(message, seed);
			case 3 -> hard(message, seed);
			default -> gibberish(message, seed);
		};
		return cut(result);
	}

	/** {@return {@code text} cut to {@link #MAX_LENGTH}, never between the two halves of a character} */
	static String cut(String text) {
		if (text.length() <= MAX_LENGTH) {
			return text;
		}
		int end = Character.isHighSurrogate(text.charAt(MAX_LENGTH - 1)) ? MAX_LENGTH - 1 : MAX_LENGTH;
		return text.substring(0, end);
	}

	/** Stage 2: slurred but easy to read. */
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
		return cut(result);
	}

	/** At stage 1, this share of messages gets a *hic* at the end. */
	private static final double HIC_AT_ONE = 0.5;

	/** Stage 3: s to sh, letters doubled and swapped, a stretched vowel now and then, a few spaces lost, more *hic*s. */
	private static String hard(String message, long seed) {
		Random random = new Random(seed);
		StringBuilder out = new StringBuilder(message.length() + 24);
		String[] words = message.split(" ", -1);
		for (int i = 0; i < words.length; i++) {
			String word = words[i];
			if (!word.isEmpty() && !isProtected(word)) {
				word = mangle(shWord(word, random, 0.9), random, 0.22);
				if (random.nextDouble() < 0.35) {
					word = stretch(word, random, 3);
				}
			}
			out.append(word);
			if (i < words.length - 1) {
				if (random.nextDouble() < 0.25) {
					out.append(' ').append(HIC).append(' ');
				} else if (random.nextDouble() >= 0.3 || isProtected(word) || isProtected(words[i + 1])) {
					out.append(' ');
				}
				// else: the space is lost
			}
		}
		return out.toString();
	}

	private static final String[] SOUNDS = {"bl", "gl", "mb", "rr", "wh", "hm", "sh", "ug", "ff"};
	private static final String VOWELS = "aeiouu";

	/** Stage 4: every word but the odd one becomes drunk noise of about its own length; capitals and protected words stay. */
	private static String gibberish(String message, long seed) {
		Random random = new Random(seed);
		StringBuilder out = new StringBuilder(message.length() + 16);
		String[] words = message.split(" ", -1);
		for (int i = 0; i < words.length; i++) {
			String word = words[i];
			if (word.isEmpty() || isProtected(word)) {
				out.append(word);
			} else if (word.length() <= 2) {
				out.append(random.nextBoolean() ? word : stretch(word, random, 3));
			} else if (random.nextDouble() < 0.15) {
				out.append(stretch(word, random, 3));
			} else {
				StringBuilder noise = new StringBuilder();
				int length = Math.max(2, word.length() + random.nextInt(4) - 1);
				while (noise.length() < length) {
					noise.append(SOUNDS[random.nextInt(SOUNDS.length)]).append(VOWELS.charAt(random.nextInt(VOWELS.length())));
				}
				String sound = noise.substring(0, length);
				out.append(Character.isUpperCase(word.charAt(0)) ? Character.toUpperCase(sound.charAt(0)) + sound.substring(1) : sound);
			}
			if (i < words.length - 1) {
				out.append(random.nextDouble() < 0.3 ? " " + HIC + " " : " ");
			}
		}
		if (random.nextBoolean()) {
			out.append(' ').append(HIC);
		}
		return out.toString();
	}

	/** s to sh (not before an h) with this chance. */
	private static String shWord(String word, Random random, double chance) {
		StringBuilder out = new StringBuilder(word.length() + 4);
		for (int i = 0; i < word.length(); i++) {
			char c = word.charAt(i);
			boolean nextIsH = i + 1 < word.length() && Character.toLowerCase(word.charAt(i + 1)) == 'h';
			out.append(c);
			if ((c == 's' || c == 'S') && !nextIsH && random.nextDouble() < chance) {
				out.append(Character.isUpperCase(c) && isAllCaps(word) ? 'H' : 'h');
			}
		}
		return out.toString();
	}

	/** Letters doubled or two neighbours swapped, each with about this chance. */
	private static String mangle(String word, Random random, double chance) {
		StringBuilder w = new StringBuilder(word);
		int i = 0;
		while (i < w.length() - 1) {
			double roll = random.nextDouble();
			if (roll < chance * 0.5 && Character.isLetter(w.charAt(i)) && Character.isLetter(w.charAt(i + 1))) {
				char a = w.charAt(i);
				w.setCharAt(i, w.charAt(i + 1));
				w.setCharAt(i + 1, a);
				i += 2;
			} else if (roll < chance && Character.isLetter(w.charAt(i))) {
				w.insert(i, w.charAt(i));
				i += 2;
			} else {
				i++;
			}
		}
		return w.toString();
	}

	/** One vowel said {@code times} more times. */
	private static String stretch(String word, Random random, int times) {
		StringBuilder out = new StringBuilder(word);
		int vowel = pickVowel(out, random);
		if (vowel >= 0) {
			char v = Character.toLowerCase(out.charAt(vowel));
			out.insert(vowel + 1, String.valueOf(isAllCaps(word) ? Character.toUpperCase(v) : v).repeat(times));
		}
		return out.toString();
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
